package com.stash.core.model

import kotlin.math.roundToInt

/**
 * The rules for Settings › Appearance › Display size, kept in one place so the preference, the
 * slider and the activity that applies the scale can never disagree about the range.
 *
 * The scale multiplies the activity's density, so it stacks on top of Android's own Display size.
 * A phone already set to its largest system size has little width left, so the top of the range
 * is capped per window ([maxFor]); a tablet keeps the full [MAX].
 */
object UiScale {
    /** Android's own layout: no scaling. */
    const val DEFAULT = 1f

    /** Below this, 48dp touch targets get too small to hit reliably. */
    const val MIN = 0.85f

    /** The most any window gets; small windows get less, see [maxFor]. */
    const val MAX = 1.6f

    /** The slider detents and the stored value both sit on this grid. */
    const val STEP = 0.05f

    /** 1 / [STEP], as an int, so snapping and capping can be done in exact integer steps. */
    private const val STEPS_PER_UNIT = 20

    /**
     * The narrowest a window may become, in scaled dp. Phone layouts are designed down to 320dp;
     * below that rows start to truncate and the Now Playing top row runs out of slots.
     */
    const val MIN_SCALED_SMALLEST_WIDTH_DP = 320

    /**
     * [scale] clamped to [MIN]..[MAX] and rounded to the nearest [STEP]. Anything unusable (NaN,
     * from a corrupt or hand-edited store) reads as [DEFAULT], so a bad value can never leave the
     * app unreadable.
     */
    fun snap(scale: Float): Float {
        if (scale.isNaN()) return DEFAULT
        return (scale.coerceIn(MIN, MAX) * STEPS_PER_UNIT).roundToInt() / STEPS_PER_UNIT.toFloat()
    }

    /**
     * The largest scale a window whose unscaled smallest width is [smallestWidthDp] can take while
     * keeping at least [MIN_SCALED_SMALLEST_WIDTH_DP] of scaled width, rounded down to a [STEP].
     * Never below [DEFAULT]: the cap only limits enlarging, a window narrower than 320dp to begin
     * with still lays out as Android sizes it. An unknown width (0, Android's "undefined") gets no
     * enlarging either, the safe side.
     */
    fun maxFor(smallestWidthDp: Int): Float {
        if (smallestWidthDp <= 0) return DEFAULT
        // Integer division floors, so 411dp → 25 steps → 1.25, never a value just over the line.
        val steps = smallestWidthDp * STEPS_PER_UNIT / MIN_SCALED_SMALLEST_WIDTH_DP
        return (steps / STEPS_PER_UNIT.toFloat()).coerceIn(DEFAULT, MAX)
    }

    /** What a window of unscaled smallest width [smallestWidthDp] is actually laid out at for the chosen [scale]. */
    fun effective(scale: Float, smallestWidthDp: Int): Float = minOf(snap(scale), maxFor(smallestWidthDp))

    /**
     * The `steps` for a Slider over [MIN]..[max] that stops on every [STEP] (1.00 included, since
     * [MIN] sits on the grid). Material's `steps` counts only the stops between the two ends.
     */
    fun sliderSteps(max: Float): Int =
        ((max - MIN) * STEPS_PER_UNIT).roundToInt().minus(1).coerceAtLeast(0)

    /** The density to lay out at: [baseDensityDpi] times [scale], to the nearest whole dpi. */
    fun scaledDensityDpi(baseDensityDpi: Int, scale: Float): Int = (baseDensityDpi * scale).roundToInt()

    /**
     * A width or height of [dp] at [baseDensityDpi], restated at [scaledDensityDpi]: the same pixels,
     * fewer (or more) dp. Rounded down, as Android rounds its own screen sizes, so a layout sized from
     * it never asks for more room than the window has.
     */
    fun rescaleDp(dp: Int, baseDensityDpi: Int, scaledDensityDpi: Int): Int =
        if (dp <= 0 || baseDensityDpi <= 0 || scaledDensityDpi <= 0) {
            dp // 0 is Android's "undefined": leave it undefined
        } else {
            (dp.toLong() * baseDensityDpi / scaledDensityDpi).toInt()
        }
}
