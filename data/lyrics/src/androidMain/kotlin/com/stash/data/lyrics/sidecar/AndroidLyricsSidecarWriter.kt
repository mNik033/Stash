package com.stash.data.lyrics.sidecar

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.stash.core.common.Log
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.LyricsEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.prefs.LibraryLayout
import com.stash.core.data.prefs.StoragePreference
import com.stash.data.download.files.LibraryLayoutResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android sidecar writer, supporting internal storage and SAF document tree writes.
 */
@Singleton
class AndroidLyricsSidecarWriter @Inject constructor(
    private val trackDao: TrackDao,
    @ApplicationContext private val context: Context,
    private val storagePreference: StoragePreference,
) : LyricsSidecarWriter {

    override suspend fun write(trackId: Long, lyrics: LyricsEntity): Unit = withContext(Dispatchers.IO) {
        val ttml = lyrics.ttml?.takeUnless(String::isBlank)
        val hasLrcBody = !(lyrics.syncedLrc.isNullOrBlank() && lyrics.plainText.isNullOrBlank())
        if (ttml == null && !hasLrcBody) fail("No lyrics body for track $trackId")

        val track = trackDao.getById(trackId) ?: fail("Track $trackId no longer exists")
        val path = track.filePath ?: throw LyricsSidecarWriter.NoDownloadedFileException(trackId)

        if (ttml != null) {
            writeSidecarFile(track, path, ttml, "ttml", TTML_MIME)
            if (hasLrcBody) {
                runCatching { writeSidecarFile(track, path, LyricsSidecarFormatting.buildLrcBody(track, lyrics), "lrc", LRC_MIME) }
                    .onFailure { e -> Log.w(TAG, ".lrc alongside .ttml failed for track $trackId", e) }
            }
            return@withContext
        }

        if (!hasLrcBody) fail("No lyrics body for track $trackId")
        writeSidecarFile(track, path, LyricsSidecarFormatting.buildLrcBody(track, lyrics), "lrc", LRC_MIME)
    }

    override suspend fun writeLrcSidecar(trackId: Long, lyrics: LyricsEntity): Unit = withContext(Dispatchers.IO) {
        if (lyrics.syncedLrc.isNullOrBlank() && lyrics.plainText.isNullOrBlank()) {
            fail("No lyrics body for track $trackId")
        }
        val track = trackDao.getById(trackId) ?: fail("Track $trackId no longer exists")
        val path = track.filePath ?: fail("Track $trackId has no downloaded file")
        writeSidecarFile(track, path, LyricsSidecarFormatting.buildLrcBody(track, lyrics), "lrc", LRC_MIME)
    }

    override suspend fun deleteTtmlSidecar(trackId: Long): Unit = withContext(Dispatchers.IO) {
        val track = trackDao.getById(trackId) ?: return@withContext
        val path = track.filePath ?: return@withContext
        runCatching { deleteSidecarFile(track, path, "ttml") }
            .onFailure { e -> Log.w(TAG, "Couldn't delete .ttml sidecar for track $trackId", e) }
    }

    private suspend fun writeSidecarFile(track: TrackEntity, path: String, body: String, ext: String, mime: String) {
        if (path.startsWith("content://")) writeSafSidecar(track, body, ext, mime)
        else writeFilesystemSidecar(path, body, ext)
    }

    private suspend fun deleteSidecarFile(track: TrackEntity, path: String, ext: String) {
        if (path.startsWith("content://")) deleteSafSidecar(track, ext)
        else deleteFilesystemSidecar(path, ext)
    }

    private fun writeFilesystemSidecar(audioPath: String, body: String, ext: String) {
        val audio = File(audioPath)
        val parent = audio.parentFile ?: run {
            Log.w(TAG, "Cannot resolve parent directory for $audioPath; sidecar skipped")
            throw IOException("Cannot resolve parent directory for $audioPath")
        }
        File(parent, "${audio.nameWithoutExtension}.$ext").writeText(body, Charsets.UTF_8)
    }

    private fun deleteFilesystemSidecar(audioPath: String, ext: String) {
        val audio = File(audioPath)
        val parent = audio.parentFile ?: return
        File(parent, "${audio.nameWithoutExtension}.$ext").takeIf { it.exists() }?.delete()
    }

    private suspend fun writeSafSidecar(track: TrackEntity, body: String, ext: String, mime: String) {
        val (tree, segments, baseName) = resolveSafLocation(track)
            ?: fail("Track ${track.id} has no SAF tree configured")
        var cursor = tree
        for (segment in segments) {
            cursor = findOrCreateDir(cursor, segment) ?: fail("Could not create directory '$segment'")
        }
        val filename = "$baseName.$ext"
        val existing = cursor.findFile(filename)
        val target = existing ?: cursor.createFile(mime, filename) ?: run {
            Log.w(TAG, "Could not create SAF sidecar '$filename' under ${cursor.uri}")
            throw IOException("Could not create SAF sidecar $filename")
        }
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
            out.write(body.toByteArray(Charsets.UTF_8))
        } ?: fail("Could not open SAF output stream for sidecar ${target.uri}")
    }

    private suspend fun deleteSafSidecar(track: TrackEntity, ext: String) {
        val (tree, segments, baseName) = resolveSafLocation(track) ?: return
        var cursor = tree
        for (segment in segments) {
            cursor = cursor.findFile(segment)?.takeIf { it.isDirectory } ?: return
        }
        cursor.findFile("$baseName.$ext")?.delete()
    }

    private suspend fun resolveSafLocation(track: TrackEntity): Triple<DocumentFile, List<String>, String>? {
        val treeUri: Uri = storagePreference.externalTreeUri.first() ?: run {
            Log.w(TAG, "Track ${track.id} has SAF filePath but no externalTreeUri persisted; sidecar skipped")
            return null
        }
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: run {
            Log.w(TAG, "DocumentFile.fromTreeUri returned null for $treeUri; sidecar skipped")
            return null
        }
        val beside = safLocationBesideAudio(treeUri, track.filePath)
        val (segments, baseName) = beside ?: run {
            val layout = runCatching { storagePreference.libraryLayout.first() }.getOrDefault(LibraryLayout.DEFAULT)
            val playlistName = if (layout == LibraryLayout.PLAYLIST) {
                runCatching { trackDao.getFirstPlaylistNameForTrack(track.id) }.getOrNull()
            } else null
            val location = LibraryLayoutResolver.resolve(
                layout, artist = track.artist, album = track.album.takeIf { it.isNotBlank() },
                title = track.title, playlistName = playlistName,
            )
            location.segments to location.baseName
        }
        return Triple(tree, segments, baseName)
    }

    private fun safLocationBesideAudio(treeUri: Uri, docUriString: String?): Pair<List<String>, String>? {
        if (docUriString.isNullOrBlank() || !docUriString.startsWith("content://")) return null
        return try {
            val baseRel = DocumentsContract.getTreeDocumentId(treeUri).substringAfter(':', "")
            val docRel = DocumentsContract.getDocumentId(Uri.parse(docUriString)).substringAfter(':', "")
            if (!docRel.startsWith(baseRel, ignoreCase = true)) return null
            val parts = docRel.substring(baseRel.length).split('/').filter { it.isNotBlank() }
            if (parts.isEmpty()) null else parts.dropLast(1) to parts.last().substringBeforeLast('.')
        } catch (t: Throwable) {
            Log.d(TAG, "Sidecar location undecodable for $docUriString: ${t.message}")
            null
        }
    }

    private fun findOrCreateDir(parent: DocumentFile, name: String): DocumentFile? {
        parent.findFile(name)?.takeIf { it.isDirectory }?.let { return it }
        val created = parent.createDirectory(name)
        if (created == null) Log.w(TAG, "Could not create SAF dir '$name' under ${parent.uri}")
        return created
    }

    private fun fail(message: String): Nothing = throw IOException(message).also { Log.w(TAG, message) }

    private companion object {
        private const val TAG = "AndroidLyricsSidecarWriter"
        private const val LRC_MIME = "application/x-lrc"
        private const val TTML_MIME = "application/x-ttml"
    }
}
