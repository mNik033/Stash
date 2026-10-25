package com.stash.feature.library.allsongs

import com.stash.core.data.db.dao.ScannedSongDao
import com.stash.core.data.db.entity.ScanFolderEntity
import com.stash.core.data.db.entity.ScannedSongEntity
import com.stash.core.model.SCANNED_UNKNOWN_ARTIST
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DesktopAllSongsScanner @Inject constructor(
    private val dao: ScannedSongDao,
    private val covers: ScannedCoverStore,
) : AllSongsScanner {

    override suspend fun displayName(treeUri: String): String = withContext(Dispatchers.IO) {
        val f = File(treeUri.removePrefix("file://"))
        f.name.ifBlank { f.path }
    }

    override fun deleteFile(uri: String): FileDeletion {
        val file = File(uri.removePrefix("file://"))
        return if (!file.exists()) {
            FileDeletion.GONE
        } else if (file.delete()) {
            FileDeletion.DELETED
        } else {
            FileDeletion.FAILED
        }
    }

    override suspend fun downloadTreeUri(): String? = null

    override suspend fun conflictFor(treeUri: String, folders: List<ScanFolderEntity>): FolderConflict? {
        val targetPath = File(treeUri.removePrefix("file://")).canonicalPath
        for (f in folders) {
            val existingPath = File(f.treeUri.removePrefix("file://")).canonicalPath
            if (targetPath == existingPath || targetPath.startsWith(existingPath + File.separator)) {
                return FolderConflict.InsideAdded(f.displayName)
            }
            if (existingPath.startsWith(targetPath + File.separator)) {
                return FolderConflict.ContainsAdded(f.displayName)
            }
        }
        return null
    }

    override suspend fun scan(folder: ScanFolderEntity): ScanResult = withContext(Dispatchers.IO) {
        val root = File(folder.treeUri.removePrefix("file://"))
        if (!root.exists() || !root.isDirectory) {
            return@withContext ScanResult(0, 0, 0, incomplete = true)
        }
        val audioExts = setOf("mp3", "flac", "m4a", "aac", "ogg", "wav", "opus")
        val files = root.walkTopDown().filter { it.isFile && it.extension.lowercase() in audioExts }.toList()

        val listed = files.map { f ->
            ListedFile(
                uri = "file://${f.canonicalPath}",
                name = f.name,
                ext = f.extension.lowercase(),
                size = f.length(),
                lastModified = f.lastModified(),
            )
        }

        val plan = planRescan(dao.getStamps(folder.id), listed, listingComplete = true)

        plan.gone.chunked(500).forEach { dao.deleteByUris(folder.id, it) }

        val now = System.currentTimeMillis()
        val newEntities = plan.fresh.map { f ->
            ScannedSongEntity(
                folderId = folder.id,
                uri = f.uri,
                title = f.name.substringBeforeLast('.'),
                artist = SCANNED_UNKNOWN_ARTIST,
                album = "",
                durationMs = 0L,
                fileFormat = f.ext,
                fileSizeBytes = f.size,
                lastModified = f.lastModified,
                dateAdded = now,
                albumArtPath = null,
            )
        }
        newEntities.chunked(100).forEach { dao.insertSongs(it) }

        sweepArt()
        ScanResult(added = plan.fresh.size, updated = plan.changed.size, removed = plan.gone.size, incomplete = false)
    }

    override fun takeGrant(treeUri: String): Boolean = true

    override suspend fun releaseGrant(treeUri: String, remaining: List<ScanFolderEntity>) {
        // No-op on desktop
    }

    override suspend fun sweepArt() = withContext(Dispatchers.IO) {
        covers.sweep(dao.getArtPaths().toHashSet())
    }
}
