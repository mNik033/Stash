package com.stash.feature.library

import com.stash.core.common.platform.AppPaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopPlaylistImageHelper @Inject constructor(
    private val appPaths: AppPaths,
) : PlaylistImageHelper {

    override fun savePlaylistCoverImage(playlistId: Long, imageUri: String): String? {
        return try {
            val cleanPath = imageUri.removePrefix("file://")
            val sourceFile = File(cleanPath)
            if (!sourceFile.exists()) return null

            val dir = File(appPaths.filesDir, "playlist_covers")
            dir.mkdirs()
            val destFile = File(dir, "$playlistId.jpg")
            sourceFile.copyTo(destFile, overwrite = true)

            "file://${destFile.absolutePath}?v=${System.currentTimeMillis()}"
        } catch (e: Exception) {
            null
        }
    }

    override fun deletePlaylistCoverFile(playlistId: Long) {
        val file = File(appPaths.filesDir, "playlist_covers/$playlistId.jpg")
        file.delete()
    }
}
