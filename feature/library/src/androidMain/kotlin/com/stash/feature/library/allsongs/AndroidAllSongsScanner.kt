package com.stash.feature.library.allsongs

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.stash.core.common.constants.StashConstants
import com.stash.core.data.db.dao.ScannedSongDao
import com.stash.core.data.db.entity.ScanFolderEntity
import com.stash.core.data.db.entity.ScannedSongEntity
import com.stash.core.data.prefs.StoragePreference
import com.stash.core.model.SCANNED_UNKNOWN_ARTIST
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class AndroidAllSongsScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ScannedSongDao,
    private val storagePreference: StoragePreference,
    private val covers: ScannedCoverStore,
) : AllSongsScanner {

    override suspend fun displayName(treeUri: String): String = withContext(Dispatchers.IO) {
        val tree = Uri.parse(treeUri)
        val name = try {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver
                .query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
        name ?: tree.lastPathSegment ?: "Folder"
    }

    override fun deleteFile(uri: String): FileDeletion = try {
        if (DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri))) {
            FileDeletion.DELETED
        } else {
            FileDeletion.FAILED
        }
    } catch (e: FileNotFoundException) {
        FileDeletion.GONE
    } catch (e: Exception) {
        FileDeletion.FAILED
    }

    override suspend fun downloadTreeUri(): String? = storagePreference.externalTreeUri.first()?.toString()

    override suspend fun conflictFor(treeUri: String, folders: List<ScanFolderEntity>): FolderConflict? {
        val tree = Uri.parse(treeUri)
        val candidate = treeRef(tree) ?: return null
        val added = folders.mapNotNull { f -> treeRef(Uri.parse(f.treeUri))?.let { it to f.displayName } }
        val dlUri = storagePreference.externalTreeUri.first()
        return folderConflict(candidate, added, dlUri?.let(::treeRef))
    }

    override suspend fun scan(folder: ScanFolderEntity): ScanResult = withContext(Dispatchers.IO) {
        val tree = Uri.parse(folder.treeUri)
        val dlUri = storagePreference.externalTreeUri.first()
        val skip = treeRef(tree)?.let { downloadSubtreeToSkip(it, dlUri?.let(::treeRef)) }
        val listing = walk(tree, skip)
        val plan = planRescan(dao.getStamps(folder.id), listing.files, listing.complete)

        plan.gone.chunked(500).forEach { dao.deleteByUris(folder.id, it) }

        plan.fresh.chunked(TAG_BATCH).forEach { batch ->
            dao.insertSongs(readEach(batch) { f -> readTags(folder.id, f, dateAdded = System.currentTimeMillis()) })
        }
        plan.changed.chunked(TAG_BATCH).forEach { batch ->
            dao.updateSongs(
                readEach(batch) { (stamp, f) -> readTags(folder.id, f, dateAdded = stamp.dateAdded).copy(id = stamp.id) },
            )
        }
        fillMissingArt(folder.id)
        ScanResult(added = plan.fresh.size, updated = plan.changed.size, removed = plan.gone.size, incomplete = !listing.complete)
    }

    override fun takeGrant(treeUri: String): Boolean {
        val uri = Uri.parse(treeUri)
        val resolver = context.contentResolver
        return try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        } catch (e: SecurityException) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            } catch (e2: SecurityException) {
                false
            }
        }
    }

    override suspend fun releaseGrant(treeUri: String, remaining: List<ScanFolderEntity>) {
        if (remaining.any { it.treeUri == treeUri }) return
        val dlUri = storagePreference.externalTreeUri.first()
        if (dlUri?.toString() == treeUri) return
        val tree = Uri.parse(treeUri)
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                // Already gone (revoked, or never held): nothing to release.
            }
        }
    }

    override suspend fun sweepArt() {
        withContext(Dispatchers.IO) {
            covers.sweep(dao.getArtPaths().toHashSet())
        }
    }

    private suspend fun <T> readEach(items: List<T>, read: suspend (T) -> ScannedSongEntity): List<ScannedSongEntity> =
        coroutineScope {
            val sem = Semaphore(TAG_READERS)
            items.map { item -> async { sem.withPermit { read(item) } } }.awaitAll()
        }

    private class Listing(val files: List<ListedFile>, val complete: Boolean)

    private suspend fun walk(tree: Uri, skipDocId: String?): Listing {
        val resolver = context.contentResolver
        val out = ArrayList<ListedFile>()
        val stack = ArrayList<String>()
        stack.add(DocumentsContract.getTreeDocumentId(tree))
        var isRoot = true
        var complete = true
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val parentId = stack.removeAt(stack.lastIndex)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val cursor = try {
                resolver.query(children, COLUMNS, null, null, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (cursor == null) {
                if (isRoot) throw IOException("Can't read folder")
                complete = false
                continue
            }
            isRoot = false
            cursor.use { c ->
                val extras = c.extras
                if (extras != null &&
                    (extras.getBoolean(DocumentsContract.EXTRA_LOADING, false) || extras.containsKey(DocumentsContract.EXTRA_ERROR))
                ) {
                    complete = false
                }
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!name.startsWith(".") && docId != skipDocId) stack.add(docId)
                        continue
                    }
                    val ext = playableAudioExtension(name, mime) ?: continue
                    val sizeKnown = !c.isNull(3)
                    val size = if (sizeKnown) c.getLong(3) else 0L
                    if (sizeKnown && size < StashConstants.MIN_PLAYABLE_LOCAL_BYTES) continue
                    val lastModified = if (c.isNull(4)) 0L else c.getLong(4)
                    out.add(
                        ListedFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId).toString(),
                            name = name,
                            ext = ext,
                            size = size,
                            lastModified = lastModified,
                        ),
                    )
                }
            }
        }
        return Listing(out, complete)
    }

    private suspend fun readTags(folderId: Long, f: ListedFile, dateAdded: Long): ScannedSongEntity {
        coroutineContext.ensureActive()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration = 0L
        var art: ByteArray? = null
        var readOk = false
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, Uri.parse(f.uri))
            readOk = true
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            art = r.embeddedPicture
        } catch (e: Exception) {
            // Unreadable tags: fall back to the filename below.
        } finally {
            runCatching { r.release() }
        }
        return ScannedSongEntity(
            folderId = folderId,
            uri = f.uri,
            title = title?.takeIf { it.isNotBlank() } ?: f.name.substringBeforeLast('.'),
            artist = artist?.takeIf { it.isNotBlank() } ?: SCANNED_UNKNOWN_ARTIST,
            album = album?.takeIf { it.isNotBlank() } ?: "",
            durationMs = duration,
            fileFormat = f.ext,
            fileSizeBytes = f.size,
            lastModified = f.lastModified,
            dateAdded = dateAdded,
            albumArtPath = if (readOk) covers.save(art) else null,
        )
    }

    private suspend fun fillMissingArt(folderId: Long) {
        val present = covers.names()
        dao.getArtRefs(folderId).filter { it.art !in present }.map { it.id }
            .chunked(500).forEach { dao.clearAlbumArt(it) }

        val sem = Semaphore(TAG_READERS)
        coroutineScope {
            dao.getUnarted(folderId).map { row ->
                async {
                    sem.withPermit {
                        coroutineContext.ensureActive()
                        val art = try {
                            readArt(row.uri)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                        if (art != null) dao.setAlbumArtPath(row.id, art)
                    }
                }
            }.awaitAll()
        }
    }

    private fun readArt(uri: String): String? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, Uri.parse(uri))
            return covers.save(r.embeddedPicture)
        } finally {
            runCatching { r.release() }
        }
    }

    private companion object {
        const val TAG_READERS = 4
        const val TAG_BATCH = 100
        val COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        fun treeRef(tree: Uri): TreeRef? = try {
            val authority = tree.authority ?: return null
            TreeRef(authority, DocumentsContract.getTreeDocumentId(tree))
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
