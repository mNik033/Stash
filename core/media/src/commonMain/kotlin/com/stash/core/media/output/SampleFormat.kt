package com.stash.core.media.output

/**
 * The linear-PCM sample formats Stash's output stage reads and writes.
 *
 * Kept free of Media3 / android.media so the conversion and DSP code built on
 * it runs (and is tested) on the plain JVM. [StashAudioOutputProvider] maps
 * these to `C.ENCODING_PCM_*` at the edge.
 *
 * All integer formats are little-endian, signed, interleaved — what
 * `AudioTrack` takes. [INT24] is *packed* (3 bytes per sample,
 * `ENCODING_PCM_24BIT_PACKED`), not 24-in-32.
 */
enum class SampleFormat(val bytesPerSample: Int, val bits: Int) {
    INT16(2, 16),
    INT24(3, 24),
    INT32(4, 32),
    FLOAT(4, 32),
    ;

    val isInteger: Boolean get() = this != FLOAT

    /** Human label for the signal-path sheet: "16-bit", "24-bit", "32-bit", "32-bit float". */
    val label: String get() = if (this == FLOAT) "32-bit float" else "$bits-bit"

    fun frameSize(channels: Int): Int = bytesPerSample * channels
}
