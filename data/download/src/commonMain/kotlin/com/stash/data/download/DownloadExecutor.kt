package com.stash.data.download

import java.io.File

/**
 * Result of a yt-dlp download attempt.
 */
sealed class DownloadResult {
    /** Download succeeded; [file] is the audio on disk. */
    data class Success(val file: File) : DownloadResult()

    /** yt-dlp exited with an error. [message] contains the stderr output. */
    data class YtDlpError(val message: String) : DownloadResult()

    /** An unexpected exception occurred. */
    data class Error(val message: String, val cause: Throwable? = null) : DownloadResult()

    /** yt-dlp ran without error but no output file was found. */
    data class NoOutput(val stdout: String?, val stderr: String?) : DownloadResult()
}

/**
 * Platform abstraction for executing yt-dlp downloads.
 */
interface DownloadExecutor {
    suspend fun download(
        url: String,
        outputDir: File,
        filename: String,
        qualityArgs: List<String>,
        onProgress: (Float) -> Unit = {},
    ): DownloadResult
}

