package com.stash.data.download.files

import com.stash.core.common.platform.AppPaths
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Desktop implementation of [FileOrganizer] that stores music under `appPaths.filesDir/music/Artist/Album/Title.ext`.
 */
@Singleton
class DesktopFileOrganizer @Inject constructor(
    private val appPaths: AppPaths,
) : FileOrganizer {

    private val musicDir: File get() = File(appPaths.filesDir, "music").also { it.mkdirs() }

    override fun getTempDir(): File = File(appPaths.cacheDir, "temp").also { it.mkdirs() }
    override fun getAlbumArtDir(): File = File(appPaths.cacheDir, "album_art").also { it.mkdirs() }

    override suspend fun commitDownload(
        tempFile: File,
        artist: String,
        album: String?,
        title: String,
        format: String,
        trackId: Long?,
        nameSuffix: String?,
        asNewFile: Boolean,
    ): CommittedTrack {
        val safeArtist = sanitizeName(artist.ifBlank { "Unknown Artist" })
        val safeAlbum = sanitizeName(album?.takeIf { it.isNotBlank() } ?: "Singles")
        val baseTitle = sanitizeName(title.ifBlank { "Untitled" })
        val suffix = nameSuffix?.let { " - ${sanitizeName(it)}" }.orEmpty()
        val ext = format.trimStart('.').lowercase()

        val targetDir = File(musicDir, "$safeArtist/$safeAlbum").also { it.mkdirs() }
        var targetFile = File(targetDir, "$baseTitle$suffix.$ext")

        if (asNewFile && targetFile.exists()) {
            var counter = 2
            while (targetFile.exists()) {
                targetFile = File(targetDir, "$baseTitle$suffix ($counter).$ext")
                counter++
            }
        }

        Files.move(
            tempFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )

        return CommittedTrack(targetFile.absolutePath, targetFile.length())
    }

    private fun sanitizeName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
}
