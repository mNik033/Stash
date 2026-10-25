package com.stash.data.download.files

/**
 * One-walk breakdown of the music library on disk.
 */
data class LibrarySizeBreakdown(
    val totalBytes: Long = 0L,
    val losslessBytes: Long = 0L,
    val losslessFileCount: Int = 0,
)
