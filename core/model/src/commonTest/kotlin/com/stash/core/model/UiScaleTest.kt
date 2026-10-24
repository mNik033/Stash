package com.stash.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.roundToInt

/** Settings › Appearance › Display size: the range, the grid and the per-window cap. */
class UiScaleTest {

    @Test fun `snap clamps to the range`() {
        assertThat(UiScale.snap(0.5f)).isEqualTo(UiScale.MIN)
        assertThat(UiScale.snap(0.8f)).isEqualTo(0.85f) // the old floor, from a pre-release build
        assertThat(UiScale.snap(3f)).isEqualTo(UiScale.MAX)
        assertThat(UiScale.snap(Float.POSITIVE_INFINITY)).isEqualTo(UiScale.MAX)
        assertThat(UiScale.snap(Float.NEGATIVE_INFINITY)).isEqualTo(UiScale.MIN)
    }

    @Test fun `snap rounds to the nearest 0_05`() {
        assertThat(UiScale.snap(1.01f)).isEqualTo(1f)
        assertThat(UiScale.snap(1.024f)).isEqualTo(1f)
        assertThat(UiScale.snap(1.026f)).isEqualTo(1.05f)
        assertThat(UiScale.snap(1.2999f)).isEqualTo(1.3f)
    }

    @Test fun `snapped values compare equal to their literals`() {
        // The activity compares the stored and applied scales with ==, so float drift would mean a
        // recreate on every launch. Every stop must land on exactly the literal it prints as.
        var stop = 85
        while (stop <= 160) {
            val literal = "%.2f".format(java.util.Locale.US, stop / 100f).toFloat()
            assertThat(UiScale.snap(literal)).isEqualTo(literal)
            assertThat(UiScale.snap(UiScale.snap(literal))).isEqualTo(UiScale.snap(literal))
            stop += 5
        }
    }

    @Test fun `NaN reads as the default`() {
        assertThat(UiScale.snap(Float.NaN)).isEqualTo(UiScale.DEFAULT)
    }

    @Test fun `a typical phone tops out where 320dp of width is left`() {
        assertThat(UiScale.maxFor(411)).isEqualTo(1.25f) // Pixel, default system size: 411/320 = 1.284
        assertThat(UiScale.maxFor(384)).isEqualTo(1.2f) // exactly on a step
        assertThat(UiScale.maxFor(360)).isEqualTo(1.1f) // 1.125 rounds down, never over the line
    }

    @Test fun `a tablet keeps the full range`() {
        assertThat(UiScale.maxFor(600)).isEqualTo(UiScale.MAX)
        assertThat(UiScale.maxFor(800)).isEqualTo(UiScale.MAX)
    }

    @Test fun `the cap never drops below the default`() {
        // A phone at its largest system Display size, or a split-screen half: no enlarging, but
        // nothing is shrunk that the user didn't ask for.
        assertThat(UiScale.maxFor(320)).isEqualTo(1f)
        assertThat(UiScale.maxFor(280)).isEqualTo(1f)
        assertThat(UiScale.maxFor(0)).isEqualTo(1f) // undefined
    }

    @Test fun `the scaled smallest width never drops below 320dp`() {
        for (sw in 320..1200) {
            // In whole steps, so float rounding can't blur the line: sw / (steps / 20) >= 320.
            val steps = (UiScale.maxFor(sw) * 20).roundToInt()
            assertThat(sw * 20).isAtLeast(UiScale.MIN_SCALED_SMALLEST_WIDTH_DP * steps)
        }
    }

    @Test fun `effective applies the cap but keeps shrinking`() {
        assertThat(UiScale.effective(1.6f, 411)).isEqualTo(1.25f)
        assertThat(UiScale.effective(1.1f, 411)).isEqualTo(1.1f)
        assertThat(UiScale.effective(0.85f, 300)).isEqualTo(0.85f)
        assertThat(UiScale.effective(1.6f, 900)).isEqualTo(1.6f)
    }

    @Test fun `slider steps stop on every 0_05 including 1_00`() {
        assertThat(UiScale.sliderSteps(UiScale.MAX)).isEqualTo(14) // 0.85..1.60 = 16 stops
        assertThat(UiScale.sliderSteps(1.25f)).isEqualTo(7) // 0.85..1.25 = 9 stops
        assertThat(UiScale.sliderSteps(1f)).isEqualTo(2) // 0.85, 0.90, 0.95, 1.00
        // 1.00 is a stop: (1.00 - MIN) is a whole number of steps.
        val stopsBelowOne = (1f - UiScale.MIN) / UiScale.STEP
        assertThat(stopsBelowOne).isWithin(1e-4f).of(3f)
    }

    @Test fun `the override keeps the window's pixels`() {
        val dpi = UiScale.scaledDensityDpi(420, 1.25f)
        assertThat(dpi).isEqualTo(525)
        // 411dp at 420dpi = 1079px; at 525dpi that is 328dp.
        assertThat(UiScale.rescaleDp(411, 420, dpi)).isEqualTo(328)
        assertThat(UiScale.rescaleDp(411, 420, dpi)).isAtLeast(UiScale.MIN_SCALED_SMALLEST_WIDTH_DP)
        // Shrinking gives more dp.
        assertThat(UiScale.rescaleDp(411, 420, UiScale.scaledDensityDpi(420, 0.85f))).isEqualTo(483)
    }

    @Test fun `undefined sizes stay undefined`() {
        assertThat(UiScale.rescaleDp(0, 420, 525)).isEqualTo(0)
        assertThat(UiScale.rescaleDp(411, 0, 525)).isEqualTo(411)
    }
}
