package com.stash.core.data.prefs

import kotlinx.coroutines.flow.Flow

/**
 * How hard Stash pushes Android to leave the audio alone on a USB DAC.
 *
 * Background: every app's audio normally goes through Android's mixer
 * (AudioFlinger), which runs at ONE sample rate per output — usually 48 kHz —
 * and resamples everything else to it: a 44.1 kHz CD rip and a 96 kHz Qobuz
 * stream alike. Android 14 (API 34) lets an app ask for the USB output's mixer
 * to be reconfigured ("preferred mixer attributes"); that is the only
 * sanctioned way past the resampler short of a userspace USB driver.
 *
 * Has no effect below Android 14 or on non-USB outputs.
 */
enum class HiResOutputMode {
    /** Android's shared mixer, as every app gets. */
    STANDARD,

    /**
     * Reopen the USB output at each song's own sample rate (support is
     * mandatory for USB on Android 14+). The music is never resampled;
     * notifications, volume and Stash's EQ all still work.
     */
    MATCH_RATE,

    /**
     * Ask for the bit-perfect mixer (optional for phone makers; Pixel 8 and
     * later ship it on recent updates). Samples reach the DAC unaltered: no
     * mixing, no software volume, no EQ — volume is the DAC's own.
     */
    BIT_PERFECT,
}

/**
 * Persisted [HiResOutputMode]. Lives in `:core:data` so Settings and the
 * playback service share one source of truth; the DataStore implementation is
 * [HiResOutputPreferencesManager], bound via Hilt.
 */
interface HiResOutputPreference {

    /** Emits the chosen mode, defaulting to [HiResOutputMode.STANDARD]. */
    val mode: Flow<HiResOutputMode>

    suspend fun setMode(mode: HiResOutputMode)
}
