package com.stash.data.download

import java.io.File

/**
 * The short-lived Netscape cookie files handed to yt-dlp via `--cookies`.
 *
 * Each holds the user's whole YouTube session (LOGIN_INFO, SAPISID,
 * `__Secure-3PAPISID`) and is deleted in the `finally` of the run that wrote it.
 * Process death mid-run (OOM, swipe-away, a crash) skips that `finally`, and
 * nothing else ever removed them, so they piled up in `noBackupFilesDir` and
 * outlived "Disconnect YouTube". [sweep] removes every one of them.
 */
object YtCookieFiles {

    /** [DownloadExecutor]'s files: `yt_cookies_<nanoTime>.txt`. */
    internal const val DOWNLOAD_PREFIX = "yt_cookies_"

    /** PreviewUrlExtractor's files: `yt_preview_cookies_<nanoTime>.txt`. */
    internal const val PREVIEW_PREFIX = "yt_preview_cookies_"

    /** A fresh cookie file name in [dir] for a run with [prefix]. */
    internal fun newFile(dir: File, prefix: String): File =
        File(dir, "$prefix${System.nanoTime()}.txt")

    /**
     * Deletes every yt-dlp cookie file in [dir]. Called at yt-dlp's one-time
     * initialisation, before any run of this process has written one, and on
     * YouTube sign-out, when the session they hold must not survive.
     *
     * @return how many files were deleted.
     */
    fun sweep(dir: File): Int =
        dir.listFiles { f -> f.isFile && isCookieFileName(f.name) }
            .orEmpty()
            .count { it.delete() }

    internal fun isCookieFileName(name: String): Boolean =
        (name.startsWith(DOWNLOAD_PREFIX) || name.startsWith(PREVIEW_PREFIX)) && name.endsWith(".txt")
}
