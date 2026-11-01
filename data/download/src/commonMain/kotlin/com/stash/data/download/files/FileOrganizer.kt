package com.stash.data.download.files

import java.io.File

/**
 * Common interface for managing file paths, directories, and committing downloaded music.
 */
interface FileOrganizer {
    fun getTempDir(): File
    fun getAlbumArtDir(): File

    suspend fun commitDownload(
        tempFile: File,
        artist: String,
        album: String?,
        title: String,
        format: String,
        trackId: Long? = null,
        nameSuffix: String? = null,
        asNewFile: Boolean = false,
    ): CommittedTrack
}
