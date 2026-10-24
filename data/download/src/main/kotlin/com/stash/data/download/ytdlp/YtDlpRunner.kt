package com.stash.data.download.ytdlp

/**
 * Result of a yt-dlp execution run.
 */
data class YtDlpResponse(
    val exitCode: Int,
    val out: String,
    val err: String = "",
    val elapsedTime: Long = 0L,
)

/**
 * Platform abstraction for running yt-dlp commands, decoupling
 * download extraction from the Android youtubedl-android native wrapper.
 */
interface YtDlpRunner {
    /**
     * Executes yt-dlp with the specified command-line arguments.
     *
     * @param args Command-line arguments to pass to yt-dlp.
     * @param onProgress Optional callback receiving (progressPercent, etaSeconds, statusText).
     */
    suspend fun execute(
        args: List<String>,
        onProgress: ((Float, Long, String) -> Unit)? = null,
    ): YtDlpResponse
}
