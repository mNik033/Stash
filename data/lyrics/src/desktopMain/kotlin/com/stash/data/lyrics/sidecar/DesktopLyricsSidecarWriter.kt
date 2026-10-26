package com.stash.data.lyrics.sidecar

import com.stash.core.common.Log
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.LyricsEntity
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DesktopLyricsSidecarWriter @Inject constructor(
    private val trackDao: TrackDao,
) : LyricsSidecarWriter {

    override suspend fun write(trackId: Long, lyrics: LyricsEntity): Unit = withContext(Dispatchers.IO) {
        val ttml = lyrics.ttml?.takeUnless(String::isBlank)
        val hasLrcBody = !(lyrics.syncedLrc.isNullOrBlank() && lyrics.plainText.isNullOrBlank())
        if (ttml == null && !hasLrcBody) fail("No lyrics body for track $trackId")

        val track = trackDao.getById(trackId) ?: fail("Track $trackId no longer exists")
        val path = track.filePath ?: throw LyricsSidecarWriter.NoDownloadedFileException(trackId)

        if (ttml != null) {
            writeFilesystemSidecar(path, ttml, "ttml")
            if (hasLrcBody) {
                runCatching { writeFilesystemSidecar(path, LyricsSidecarFormatting.buildLrcBody(track, lyrics), "lrc") }
                    .onFailure { e -> Log.w(TAG, ".lrc alongside .ttml failed for track $trackId", e) }
            }
            return@withContext
        }

        if (!hasLrcBody) fail("No lyrics body for track $trackId")
        writeFilesystemSidecar(path, LyricsSidecarFormatting.buildLrcBody(track, lyrics), "lrc")
    }

    override suspend fun writeLrcSidecar(trackId: Long, lyrics: LyricsEntity): Unit = withContext(Dispatchers.IO) {
        if (lyrics.syncedLrc.isNullOrBlank() && lyrics.plainText.isNullOrBlank()) {
            fail("No lyrics body for track $trackId")
        }
        val track = trackDao.getById(trackId) ?: fail("Track $trackId no longer exists")
        val path = track.filePath ?: fail("Track $trackId has no downloaded file")
        writeFilesystemSidecar(path, LyricsSidecarFormatting.buildLrcBody(track, lyrics), "lrc")
    }

    override suspend fun deleteTtmlSidecar(trackId: Long): Unit = withContext(Dispatchers.IO) {
        val track = trackDao.getById(trackId) ?: return@withContext
        val path = track.filePath ?: return@withContext
        val audio = getValidAudioFile(path) ?: return@withContext
        runCatching { deleteFilesystemSidecar(audio, "ttml") }
            .onFailure { e -> Log.w(TAG, "Couldn't delete .ttml sidecar for track $trackId", e) }
    }

    private fun getValidAudioFile(filePath: String): File? {
        val audio = runCatching { File(filePath).canonicalFile }.getOrNull() ?: return null
        if (!audio.isFile || audio.extension.lowercase() !in allowedAudioExts) return null
        return audio
    }

    private fun writeFilesystemSidecar(audioPath: String, body: String, ext: String) {
        val audio = getValidAudioFile(audioPath) ?: run {
            Log.w(TAG, "Invalid audio file path or extension: $audioPath")
            throw IOException("Invalid audio file path or extension: $audioPath")
        }
        val parent = audio.parentFile ?: run {
            Log.w(TAG, "Cannot resolve parent directory for $audioPath; sidecar skipped")
            throw IOException("Cannot resolve parent directory for $audioPath")
        }
        File(parent, "${audio.nameWithoutExtension}.$ext").writeText(body, Charsets.UTF_8)
    }

    private fun deleteFilesystemSidecar(audio: File, ext: String) {
        val parent = audio.parentFile ?: return
        File(parent, "${audio.nameWithoutExtension}.$ext").takeIf { it.exists() }?.delete()
    }

    private fun fail(message: String): Nothing = throw IOException(message).also { Log.w(TAG, message) }

    private companion object {
        private const val TAG = "DesktopLyricsSidecarWriter"
        private val allowedAudioExts = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wv")
    }
}
