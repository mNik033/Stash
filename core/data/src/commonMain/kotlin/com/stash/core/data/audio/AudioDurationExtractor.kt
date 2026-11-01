package com.stash.core.data.audio

/**
 * Bundle of audio-file metadata read from the container itself.
 */
data class AudioMetadata(
    val durationMs: Long,
    val bitrateKbps: Int,
    val format: String,
    val bitsPerSample: Int? = null,
    val sampleRateHz: Int? = null,
)

/**
 * Common interface for reading duration and codec metadata off downloaded audio files.
 */
interface AudioDurationExtractor {
    /**
     * Returns the track's duration in milliseconds, or null if missing / corrupt / unparseable.
     */
    fun extractMs(filePath: String): Long?

    /**
     * Reads duration + codec + bitrate (+ sample rate/bit depth) from the audio file.
     */
    fun extract(filePath: String): AudioMetadata?

    /**
     * Reads the embedded album tag from the file, if present.
     */
    fun extractAlbum(filePath: String): String? = null
}
