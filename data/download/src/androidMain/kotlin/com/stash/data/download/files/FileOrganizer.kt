package com.stash.data.download.files

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.prefs.LibraryLayout
import com.stash.core.data.prefs.StoragePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages file paths for downloaded music.
 *
 * Supports two storage destinations:
 *  - **Internal** (default): tracks live under `context.filesDir/music/…`.
 *    Pure `java.io.File` API. Tracks are deleted when the app is uninstalled
 *    and are not visible to other apps.
 *  - **External** (user-chosen via SAF): when the user picks an SD card or
 *    USB-OTG folder via `ACTION_OPEN_DOCUMENT_TREE`, new downloads are
 *    written to that location via `ContentResolver` / `DocumentFile`. These
 *    files survive app uninstall and are accessible to other apps + over
 *    USB/PC — users can take their library anywhere.
 *
 * Within either destination, the folder structure follows the user's
 * [LibraryLayout] preference (#198/#104): Artist/Album (classic default),
 * Single folder, or Per playlist. ALL path computation goes through
 * [LibraryLayoutResolver]; this class never slugs artist/album by hand so
 * writers and readers cannot drift.
 *
 * yt-dlp always writes to the internal cache (`getTempDir()`); the
 * destination switch happens in [commitDownload] after the download
 * completes, which copies the temp file to the final location and cleans up.
 */
@Singleton
class FileOrganizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storagePreference: StoragePreference,
    private val trackDao: TrackDao,
) {
    /** Root directory for all internally-stored downloaded music files. */
    private val musicDir: File get() = File(context.filesDir, "music").also { it.mkdirs() }

    /**
     * Internal library paths that commits in this process have picked, by
     * absolute path (see [claimInternalFile]). Guarded by [claimLock].
     */
    private val claims = HashMap<String, PathClaim>()
    private val claimLock = Mutex()

    /**
     * The absolute internal music root, exposed so bulk coordinators
     * (reorganize / move-library gating) can classify stored paths as
     * internal vs SAF without duplicating the `filesDir/music` convention.
     */
    fun internalMusicRoot(): File = musicDir

    /**
     * The persisted library layout, defaulting safely when DataStore read
     * fails. One-call convenience for bulk passes that want a consistent
     * layout for their whole duration (adoption, reorganize).
     */
    suspend fun currentLayout(): LibraryLayout =
        runCatching { storagePreference.libraryLayout.first() }.getOrDefault(LibraryLayout.DEFAULT)

    /**
     * Resolves a track's destination under the CURRENT library layout,
     * including the owning-playlist lookup when the layout is
     * [LibraryLayout.PLAYLIST] and a [trackId] is known. Every writer
     * (downloads, moves, reorganize, sidecars) resolves through here.
     */
    suspend fun resolveLocation(
        artist: String,
        album: String?,
        title: String,
        trackId: Long? = null,
        layout: LibraryLayout? = null,
    ): LibraryLayoutResolver.ResolvedLocation {
        val effectiveLayout = layout ?: currentLayout()
        val playlistName = if (effectiveLayout == LibraryLayout.PLAYLIST && trackId != null) {
            runCatching { trackDao.getFirstPlaylistNameForTrack(trackId) }.getOrNull()
        } else {
            null
        }
        return LibraryLayoutResolver.resolve(effectiveLayout, artist, album, title, playlistName)
    }

    /**
     * Returns the internal directory for a specific artist/album combination
     * under [layout]. Only meaningful for internal-storage destinations;
     * SAF downloads compute their own path inside [commitDownload].
     * Defaults to the classic Artist/Album shape — live downloads should go
     * through [commitDownload]/[resolveLocation], which honor the pref.
     */
    fun getTrackDir(artist: String, album: String?, layout: LibraryLayout = LibraryLayout.DEFAULT): File {
        val location = LibraryLayoutResolver.resolve(layout, artist, album, title = "", playlistName = null)
        val dir = if (location.segments.isEmpty()) musicDir else File(musicDir, location.segments.joinToString("/"))
        return dir.also { it.mkdirs() }
    }

    /**
     * Returns the internal file path for a downloaded track under [layout].
     * Prefer [commitDownload] which automatically handles both internal and
     * SAF destinations plus the live layout preference; this getter is
     * retained for callers that only need the classic target path.
     */
    fun getTrackFile(
        artist: String,
        album: String?,
        title: String,
        format: String = "opus",
        layout: LibraryLayout = LibraryLayout.DEFAULT,
    ): File {
        requireSafeFormat(format)
        val location =
            LibraryLayoutResolver.resolve(layout, artist, album, title, playlistName = null)
        return File(getTrackDir(artist, album, layout), "${location.baseName}.$format")
    }

    /**
     * Where [commitDownload] would put this track right now, once per name
     * suffix in [nameSuffixes] (null = the canonical name). For internal
     * storage, the absolute path, whether or not a file is there yet. For a
     * SAF tree, the URI of the document already at that spot, or null when
     * the spot is empty: a SAF URI only exists once its document does. The
     * folder is listed once for all the names; findFile per name re-lists it
     * each time. The wrong-match swap asks this before saving, to keep the
     * user's old file safe and to stay off a file another track uses (#531).
     */
    suspend fun plannedPaths(
        artist: String,
        album: String?,
        title: String,
        format: String,
        trackId: Long?,
        nameSuffixes: List<String?>,
    ): List<String?> {
        requireSafeFormat(format)
        val location = resolveLocation(artist, album, title, trackId)
        val names = nameSuffixes.map { fileNameFor(location, format, it) }
        val externalTree = storagePreference.externalTreeUri.first()
        if (externalTree == null) {
            val dir = if (location.segments.isEmpty()) {
                musicDir
            } else {
                File(musicDir, location.segments.joinToString("/"))
            }
            return names.map { File(dir, it).absolutePath }
        }
        val listing: Map<String, String> = runCatching {
            var cursor = DocumentFile.fromTreeUri(context, externalTree) ?: return@runCatching emptyMap()
            for (segment in location.segments) {
                cursor = cursor.findFile(segment)?.takeIf { it.isDirectory } ?: return@runCatching emptyMap()
            }
            cursor.listFiles().mapNotNull { doc -> doc.name?.let { name -> name to doc.uri.toString() } }.toMap()
        }.getOrDefault(emptyMap())
        return names.map { listing[it] }
    }

    /**
     * `<baseName>[-<suffix>].<format>`. A name suffix keeps a track's file
     * apart from another track's at the same spot; it is slugged like every
     * other path part.
     */
    private fun fileNameFor(
        location: LibraryLayoutResolver.ResolvedLocation,
        format: String,
        nameSuffix: String?,
    ): String {
        val suffix = nameSuffix?.let(FileOrganizerSlugs::slugify)?.takeIf { it.isNotEmpty() }
        return if (suffix == null) "${location.baseName}.$format" else "${location.baseName}-$suffix.$format"
    }

    /**
     * Temporary download directory inside the cache. Cleaned by the OS as
     * needed, and of files a day old once per process: each download attempt
     * names its temp files apart, so what a cancelled or crashed attempt
     * leaves is never reused, and would otherwise sit there.
     */
    fun getTempDir(): File {
        val dir = File(context.cacheDir, "downloads").also { it.mkdirs() }
        if (tempDirSwept.compareAndSet(false, true)) {
            val cutoff = System.currentTimeMillis() - STALE_TEMP_AGE_MS
            dir.listFiles { file -> file.isFile && file.lastModified() < cutoff }?.forEach { it.delete() }
        }
        return dir
    }

    private val tempDirSwept = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * One-pass existence checker: whether a track's file still exists, and
     * (for SAF) a fresh URI to persist if the stored one was stale.
     * [FileExistenceChecker.exists]'s `filePath` is only consulted for the
     * internal-storage fast path; for SAF paths the location is re-derived
     * via [LibraryLayoutResolver.lookupCandidates] against a [buildSafIndex]
     * built lazily ONCE per session — per-track findFile walks were the
     * "stuck on Checking library" hang (#429). A missing tree grant fails
     * SAFE (files reported present) so it can never mass-reset the library.
     *
     * Used by library reconciliation (missing-file detection) via
     * [com.stash.core.data.library.FileExistenceSessionFactory]. Open a
     * fresh session per pass.
     */
    fun existenceSession(): com.stash.core.data.library.FileExistenceChecker =
        object : com.stash.core.data.library.FileExistenceChecker {
            private val lock = kotlinx.coroutines.sync.Mutex()
            private var built = false
            private var snapshot: IndexSnapshot? = null

            override suspend fun exists(
                trackId: Long,
                artist: String,
                album: String?,
                title: String,
                filePath: String,
            ): com.stash.core.data.library.FileExistenceResult {
                if (!filePath.startsWith("content://")) {
                    return com.stash.core.data.library.FileExistenceResult(exists = File(filePath).exists())
                }
                val snap = lock.withLock {
                    if (!built) {
                        val layout = runCatching { storagePreference.libraryLayout.first() }
                            .getOrNull() ?: LibraryLayout.DEFAULT
                        val index = buildSafIndex()
                        if (index == null) {
                            android.util.Log.w(
                                "FileOrganizer",
                                "existenceSession: content:// paths present but no SAF tree grant — " +
                                    "treating files as present (cannot verify; a false 'missing' here " +
                                    "would reset the whole library to undownloaded)",
                            )
                        }
                        // Fail SAFE on a missing/revoked tree grant: report the file as
                        // existing rather than triggering a library-wide reset + redownload
                        // storm the first sync after a re-grant lapse.
                        snapshot = index?.let { IndexSnapshot(it, layout) }
                        built = true
                    }
                    snapshot
                } ?: return com.stash.core.data.library.FileExistenceResult(exists = true)

                // The row's own document is still in the tree, so it exists as
                // stored. A swap can give a track a distinct name when another
                // track uses the canonical one (#531); the canonical lookup
                // below would heal this row onto that other track's file.
                if (filePath in snap.index.documentUris) {
                    return com.stash.core.data.library.FileExistenceResult(exists = true)
                }

                val playlistName = if (snap.layout == LibraryLayout.PLAYLIST) {
                    runCatching { trackDao.getFirstPlaylistNameForTrack(trackId) }.getOrNull()
                } else {
                    null
                }
                val match = resolveInIndex(
                    index = snap.index,
                    layout = snap.layout,
                    artist = artist,
                    album = album,
                    title = title,
                    playlistName = playlistName,
                ) ?: return com.stash.core.data.library.FileExistenceResult(exists = false)
                val freshUri = match.uri.toString()
                return com.stash.core.data.library.FileExistenceResult(
                    exists = true,
                    // Only report a "resolved" path when it actually differs — avoids
                    // a needless DB write on the common case where the URI was fine.
                    resolvedFilePath = freshUri.takeIf { it != filePath },
                )
            }
        }

    /** Index + the layout it was built under, frozen for one bulk pass. */
    private data class IndexSnapshot(val index: SafIndex, val layout: LibraryLayout)

    /**
    * Pre-built index of the current SAF tree, so a bulk pass (like
    * [com.stash.core.data.library.AdoptExistingFilesUseCase]) can look up
    * any track in O(1) instead of re-walking directories via
    * [DocumentFile.findFile] for every single candidate.
    *
    * [DocumentFile.findFile] is not a direct lookup: internally it calls
    * `listFiles()` on the parent and linearly scans for a name match. Doing
    * that per-candidate against thousands of tracks means re-listing the same
    * directories over and over via ContentResolver IPC — the actual cost that
    * was making adoption slower than downloading the same tracks over the
    * network. Building this index means the tree is walked exactly once
    * regardless of candidate count.
    *
    * Since #198/#104 the tree is NOT guaranteed to be a strict two-level
    * `<artist>/<album>` hierarchy anymore (flat single-folder libraries,
    * per-playlist folders, mixed layouts mid-reorganize), so the walk is
    * fully recursive and keyed by slash-joined relative directory paths
    * ([SafIndex.byDirKey]). A whole-tree filename index ([SafIndex.byFileName])
    * backs the last-resort unique-name match for locations whose owning
    * playlist can't be re-derived.
    */
    suspend fun buildSafIndex(): SafIndex? {
        val treeUri = storagePreference.externalTreeUri.first() ?: return null
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null

        val byDirKey = LinkedHashMap<String, AlbumIndex>(256)
        val byFileName = HashMap<String, MutableList<DocumentFile>>(512)
        val documentUris = HashSet<String>(512)
        indexTree(root, "", byDirKey, byFileName, documentUris)
        return SafIndex(byDirKey, byFileName, documentUris)
    }

    /** Recursive worker behind [buildSafIndex]. Depth is directory nesting (≤4 in practice). */
    private fun indexTree(
        dir: DocumentFile,
        dirKey: String,
        byDirKey: MutableMap<String, AlbumIndex>,
        byFileName: MutableMap<String, MutableList<DocumentFile>>,
        documentUris: MutableSet<String>,
    ) {
        val subDirs = ArrayList<DocumentFile>()
        val filesByName = LinkedHashMap<String, DocumentFile>()
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                subDirs += child
            } else if (child.isFile) {
                val name = child.name ?: continue
                filesByName[name] = child
                byFileName.getOrPut(name) { ArrayList(1) }.add(child)
                documentUris += child.uri.toString()
            }
        }
        byDirKey[dirKey] = AlbumIndex(dir, filesByName)
        for (sub in subDirs) {
            val subName = sub.name ?: continue
            indexTree(
                sub,
                if (dirKey.isEmpty()) subName else "$dirKey/$subName",
                byDirKey,
                byFileName,
                documentUris,
            )
        }
    }

    /**
    * O(1) lookup against a pre-built [SafIndex]. Probes every location
    * [LibraryLayoutResolver.lookupCandidates] derives for the track — the
    * current-layout spot first, then the legacy/nested and flat spots for
    * files downloaded before a layout switch — with zero further SAF IPC
    * once the index is built. A final whole-tree unique-filename match
    * covers playlist-owned directories when the owning playlist couldn't be
    * re-derived (deterministic: ambiguous duplicates match nothing rather
    * than guessing wrong).
    */
    fun resolveInIndex(
        index: SafIndex,
        layout: LibraryLayout,
        artist: String,
        album: String?,
        title: String,
        playlistName: String? = null,
        knownFormat: String? = null,
    ): DocumentFile? {
        val formats = knownFormat?.let { listOf(it) } ?: KNOWN_AUDIO_FORMATS
        val candidates = LibraryLayoutResolver.lookupCandidates(
            layout, artist, album, title, playlistName, formats,
        )
        for (candidate in candidates) {
            val filesByName = index.byDirKey[candidate.dirKey]?.filesByName ?: continue
            for (name in candidate.candidateFileNames) {
                filesByName[name]?.let { return it }
            }
        }
        // Last resort: exactly one file somewhere in the tree carries a
        // candidate name. Restricted to ARTIST-QUALIFIED names
        // (`<artist>-<title>.<ext>`): the legacy Artist/Album candidate's
        // filename is a bare `<title>.<ext>`, which is not an identity once
        // it leaves its `<artist>/<album>` directory. A different artist's
        // song that slugs to the same title would be matched here and have
        // this track's file_path healed onto it. Uniqueness of a FILENAME is
        // not uniqueness of a TRACK, so only names that still carry the
        // artist may be matched tree-wide; multiple hits stay unmatched
        // (reporting "missing" for a genuinely ambiguous pair is safer than
        // adopting the wrong song's file).
        val artistQualifiedPrefix = LibraryLayoutResolver.artistSlug(artist) + "-"
        for (candidate in candidates) {
            for (name in candidate.candidateFileNames) {
                if (!name.startsWith(artistQualifiedPrefix)) continue
                index.byFileName[name]?.singleOrNull()?.let { return it }
            }
        }
        return null
    }

    /** In-memory index built once per bulk pass by [buildSafIndex]. */
    class SafIndex(
        /** Relative slash-joined dir key ("" = tree root) → its listing. */
        val byDirKey: Map<String, AlbumIndex>,
        /** Whole-tree filename → every document carrying it (usually 1). */
        val byFileName: Map<String, List<DocumentFile>>,
        /** Every file document's URI, so a row's stored URI can be found as is. */
        val documentUris: Set<String> = emptySet(),
    )

    /** Pre-listed contents of a single directory. */
    class AlbumIndex(val dir: DocumentFile, val filesByName: Map<String, DocumentFile>)

    /** Directory for cached album artwork files. */
    fun getAlbumArtDir(): File = File(context.cacheDir, "albumart").also { it.mkdirs() }

    /** Returns the file path for a specific album's artwork. */
    fun getAlbumArtFile(albumId: String): File = File(getAlbumArtDir(), "$albumId.jpg")

    /**
     * Calculates the total storage consumed by internally-downloaded files.
     * Internal music dir only; does not include SAF-targeted files —
     * see [computeMusicLibrarySize] for storage-mode-aware totals.
     */
    fun getTotalStorageBytes(): Long =
        musicDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * Storage-mode-aware total music size on disk. Walks the right place
     * based on the user's [StoragePreference]:
     *  - Internal mode (default): walks `filesDir/music`
     *  - SAF mode (user picked an external folder): walks the persisted
     *    tree URI, one provider query per folder ([sumDocumentTree])
     *
     * Returns the sum of every file under that root regardless of DB state.
     * Used by Home to display Storage truthfully on legacy libraries where
     * `tracks.file_size_bytes` is unreliable (many older download paths
     * left it at 0 and the backfill couldn't recover it).
     *
     * Returns `LibrarySizeBreakdown(total, lossless, losslessCount)` so the
     * caller doesn't have to walk the tree three times for three numbers.
     */
    suspend fun computeMusicLibrarySize(): LibrarySizeBreakdown {
        val externalUri = storagePreference.externalTreeUri.first()
        return if (externalUri != null) {
            walkSafTree(externalUri)
        } else {
            walkInternalDir()
        }
    }

    private fun walkInternalDir(): LibrarySizeBreakdown {
        var total = 0L
        var lossless = 0L
        var losslessCount = 0
        musicDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val size = file.length()
            total += size
            if (file.extension.lowercase() in LOSSLESS_EXTENSIONS) {
                lossless += size
                losslessCount++
            }
        }
        return LibrarySizeBreakdown(total, lossless, losslessCount)
    }

    /**
     * Walks a SAF tree URI counting/sizing every file recursively, with ONE
     * provider query per folder: its children, projected to id, MIME type,
     * size and name ([sumDocumentTree]).
     *
     * This used to go through DocumentFile, which costs about four provider
     * round-trips per FILE (the folder listing, then `isDirectory`, `isFile`,
     * `length` and `name` each query again): 1-2 minutes per 3,000 files on
     * an SD card, every time Home or Settings showed the size. A library has
     * a folder per album at most, so this is a small fraction of the queries.
     * Suspended + IO-dispatched at the call-site ([LibrarySizeHolder]) so the
     * cost stays off the main thread.
     */
    private fun walkSafTree(treeUri: Uri): LibrarySizeBreakdown {
        val resolver = context.contentResolver
        // The folder the user picked, read the way DocumentFile.fromTreeUri reads it.
        val rootId = if (DocumentsContract.isDocumentUri(context, treeUri)) {
            DocumentsContract.getDocumentId(treeUri)
        } else {
            DocumentsContract.getTreeDocumentId(treeUri)
        }
        return sumDocumentTree(rootId) { folderId ->
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId),
                SAF_CHILD_PROJECTION,
                null,
                null,
                null,
            )
        }
    }

    /**
     * Refuses a [format] that can't end a library file name (see
     * [isSafeFormat]) before anything is created or written. The value isn't
     * echoed: it may have come from another app or a server.
     */
    private fun requireSafeFormat(format: String) {
        require(isSafeFormat(format)) { "Refused a file extension that isn't 1-5 lower-case letters or digits" }
    }

    internal companion object {
        /**
         * True when [format] can end a library file name: 1 to 5 lower-case
         * ASCII letters or digits. Callers pass yt-dlp's or a source's
         * extension, or an import's checked one; whatever they pass, a
         * separator, a dot or `..` never reaches a file name.
         */
        fun isSafeFormat(format: String): Boolean =
            format.length in 1..5 && format.all { it in 'a'..'z' || it in '0'..'9' }

        // Mirrors `LibraryViewModel.LOSSLESS_CODECS` and
        // `core/ui/.../FlacBadge.kt`. Kept duplicated rather than reaching
        // across the data → ui boundary; the set is short and stable.
        private val LOSSLESS_EXTENSIONS = setOf(
            "flac", "alac", "wav", "ape", "tta", "wv", "aiff",
        )

        /** The four columns the SAF size walk reads from each folder's children. */
        private val SAF_CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )

        /**
         * Sums every file under the folder [rootId], calling [queryChildren]
         * exactly once per folder for a cursor over its children (the four
         * [SAF_CHILD_PROJECTION] columns, in any order) and descending into
         * each child whose MIME type is a folder.
         *
         * Counts what DocumentFile counted: a child with no MIME type is
         * neither a file nor a folder, and a file whose provider reports no
         * size adds 0. A folder whose query comes back null (the card was
         * pulled mid-walk) adds nothing; what was summed so far stands. A
         * folder seen twice is walked once, so a provider that lists a parent
         * under its own child can't loop the walk forever.
         */
        internal fun sumDocumentTree(
            rootId: String,
            queryChildren: (folderId: String) -> Cursor?,
        ): LibrarySizeBreakdown {
            var total = 0L
            var lossless = 0L
            var losslessCount = 0
            val folders = ArrayDeque<String>().apply { addLast(rootId) }
            val seen = hashSetOf(rootId)
            while (folders.isNotEmpty()) {
                queryChildren(folders.removeLast())?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val mime = c.getString(mimeCol)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            c.getString(idCol)?.let { if (seen.add(it)) folders.addLast(it) }
                        } else if (!mime.isNullOrEmpty()) {
                            val size = if (c.isNull(sizeCol)) 0L else c.getLong(sizeCol)
                            total += size
                            val ext = c.getString(nameCol)?.lowercase().orEmpty().substringAfterLast('.', "")
                            if (ext in LOSSLESS_EXTENSIONS) {
                                lossless += size
                                losslessCount++
                            }
                        }
                    }
                }
            }
            return LibrarySizeBreakdown(total, lossless, losslessCount)
        }
        // Mirrors the format switch in mimeTypeFor(). Used by
        // resolveInIndex's extension-probe when the caller doesn't
        // know the track's format ahead of time.
        private val KNOWN_AUDIO_FORMATS = listOf("opus", "m4a", "flac", "mp3", "ogg", "wav")
        // Highest `-N` a new file's name tries before the commit gives up.
        private const val MAX_COPY_NUMBER = 999
        // A temp download older than this is no attempt's any more; no download takes a day.
        private const val STALE_TEMP_AGE_MS = 24L * 60 * 60 * 1000
        // How long a written file stays claimed for its track (see releaseInternalFile):
        // far longer than any worker takes from the commit to its row write.
        private const val CLAIM_HOLD_NANOS = 10L * 60 * 1_000_000_000
        // A library file being written, before one rename puts it in place.
        private const val PART_SUFFIX = ".part"
        // Names a SAF document written to replace one: `<name>.stash-part.<ext>`.
        // The real extension stays last, so providers keep the name as given.
        private const val STAGING_INFIX = ".stash-part"
    }

    /**
     * Moves [tempFile] (written by yt-dlp to the cache) into its final
     * destination and returns the path/URI to store in
     * [com.stash.core.model.Track.filePath]. Deletes [tempFile] on success.
     *
     * If the user has chosen an external SAF folder via Settings, the file
     * is copied there through `ContentResolver` and the returned path is a
     * `content://` URI string. ExoPlayer + MediaPlayer + MediaMetadataRetriever
     * all accept content URIs natively so playback works without further
     * changes. Otherwise the file is moved into internal storage and the
     * returned path is the absolute File path (existing behaviour).
     *
     * The sub-destination inside the root follows the current [LibraryLayout]
     * (#198/#104); when the layout is Per-playlist and [trackId] is known,
     * the owning playlist is looked up so the file lands in its folder.
     * [nameSuffix] gives the file a distinct name at that spot (see
     * [plannedPaths]).
     *
     * A file already at the spot is replaced: that is a track's own file on
     * a re-download or upgrade. Unless ANOTHER track records it: names aren't
     * unique per track (two movements of one work slug alike once cut to 60
     * characters; a studio and a live "Intro" share `<artist>-intro` in the
     * flat layouts), and replacing it wrote this track's audio over the other
     * track's, leaving both rows on one file. Then, as the wrong-match swap
     * does, the album name is added, then the track id, then a counter (see
     * [replaceSuffixes]). Existing files keep their names; only a commit that
     * would land on another track's file is named apart. A caller-chosen
     * [nameSuffix] is used as given. [asNewFile] is for a file that starts a NEW
     * track (a device import), which must never take another track's place:
     * the name is used only when no file is there and no track records it,
     * else `-2`, `-3`, … is added until one is free. Otherwise an import
     * named like a downloaded song wrote over the download, and both rows
     * pointed at the one file, so deleting either broke the other.
     */
    suspend fun commitDownload(
        tempFile: File,
        artist: String,
        album: String?,
        title: String,
        format: String,
        trackId: Long? = null,
        nameSuffix: String? = null,
        asNewFile: Boolean = false,
    ): CommittedTrack {
        requireSafeFormat(format)
        val size = tempFile.length()
        val externalTree = storagePreference.externalTreeUri.first()
        return if (externalTree == null) {
            val location = resolveLocation(artist, album, title, trackId)
            val dir = if (location.segments.isEmpty()) {
                musicDir
            } else {
                File(musicDir, location.segments.joinToString("/")).also { it.mkdirs() }
            }
            val finalFile = claimInternalFile(trackId) { claimed ->
                if (asNewFile) {
                    freeInternalFile(dir, location, format, nameSuffix, trackId, claimed)
                } else {
                    ownInternalFile(dir, location, format, replaceSuffixes(nameSuffix, album, trackId), trackId, claimed)
                }
            }
            var written = false
            try {
                writeInternalFile(tempFile, finalFile, replace = !asNewFile)
                written = true
            } finally {
                releaseInternalFile(finalFile, written)
            }
            tempFile.delete()
            CommittedTrack(finalFile.absolutePath, size)
        } else {
            val location = resolveLocation(artist, album, title, trackId)
            val safUriString = if (asNewFile) {
                writeNewToSafTree(tempFile, externalTree, location, format, nameSuffix, trackId)
            } else {
                writeToSafTree(
                    tempFile,
                    externalTree,
                    location,
                    format,
                    replaceSuffixes(nameSuffix, album, trackId),
                    trackId,
                )
            }
            tempFile.delete()
            CommittedTrack(safUriString, size)
        }
    }

    /**
     * The names a new file may take, in order: [nameSuffix] (null = the
     * plain name), then `-2`, `-3`, … added after it.
     */
    private fun newFileSuffixes(nameSuffix: String?): Sequence<String?> =
        sequenceOf(nameSuffix) +
            (2..MAX_COPY_NUMBER).asSequence().map { n -> listOfNotNull(nameSuffix, "$n").joinToString("-") }

    /**
     * The names a re-download or upgrade may take, in order: [nameSuffix]
     * alone when the caller chose one; else the plain name, then the album,
     * then album and [trackId], then a counter after that. Mirrors the
     * wrong-match swap's choice (#531), so both name a clash the same way.
     */
    private fun replaceSuffixes(nameSuffix: String?, album: String?, trackId: Long?): Sequence<String?> {
        if (nameSuffix != null) return sequenceOf(nameSuffix)
        val albumName = album?.takeIf { it.isNotBlank() }
        val withId = listOfNotNull(albumName, trackId?.toString()).joinToString("-").ifEmpty { null }
        val choices = (listOf<String?>(null) + listOfNotNull(albumName, withId)).distinct()
        return choices.asSequence() + newFileSuffixes(choices.last()).drop(1)
    }

    /**
     * Whether a track other than [trackId] records [path] as its file, whether
     * or not the file is still there. Ids start at 1, so a null [trackId]
     * (no row yet) counts every track.
     */
    private suspend fun recordedByAnotherTrack(path: String, trackId: Long?): Boolean =
        trackDao.countOtherTracksWithFilePath(path, trackId ?: 0L) > 0

    /**
     * The first name in [dir] with no file on disk, no track recording it and
     * no other commit's claim on it ([claimed]).
     */
    private suspend fun freeInternalFile(
        dir: File,
        location: LibraryLayoutResolver.ResolvedLocation,
        format: String,
        nameSuffix: String?,
        trackId: Long?,
        claimed: (File) -> Boolean,
    ): File {
        for (suffix in newFileSuffixes(nameSuffix)) {
            val file = File(dir, fileNameFor(location, format, suffix))
            if (claimed(file)) continue
            if (!file.exists() && !recordedByAnotherTrack(file.absolutePath, trackId)) return file
        }
        error("No free file name for '${location.baseName}.$format' in $dir")
    }

    /**
     * The first of [suffixes]' names in [dir] that no other track records and
     * no other commit claims ([claimed]): a file there that is this track's
     * own, or nobody's, is replaced.
     */
    private suspend fun ownInternalFile(
        dir: File,
        location: LibraryLayoutResolver.ResolvedLocation,
        format: String,
        suffixes: Sequence<String?>,
        trackId: Long?,
        claimed: (File) -> Boolean,
    ): File {
        for (suffix in suffixes) {
            val file = File(dir, fileNameFor(location, format, suffix))
            if (claimed(file)) continue
            if (!recordedByAnotherTrack(file.absolutePath, trackId)) return file
        }
        error("No free file name for '${location.baseName}.$format' in $dir")
    }

    /**
     * Picks an internal file with [pick] and claims it for this commit, so a
     * concurrent commit for another track names its file apart. A track's row
     * is what tells commits apart ([recordedByAnotherTrack]), and the worker
     * writes it seconds after the commit: two downloads whose names slug
     * alike (a studio and a live "Intro") both found the name free and the
     * second replaced the first one's file, leaving both rows on one file.
     * [pick] is given the claim check for each candidate and runs under
     * [claimLock], so picks happen one at a time. Commits of the SAME track
     * share a claim and may replace each other's file, as they always could.
     * Release with [releaseInternalFile].
     */
    private suspend fun claimInternalFile(
        trackId: Long?,
        pick: suspend (claimed: (File) -> Boolean) -> File,
    ): File = claimLock.withLock {
        val now = System.nanoTime()
        claims.values.removeAll { !it.isLive(now) }
        val file = pick { candidate ->
            val claim = claims[candidate.absolutePath]
            claim != null && (claim.trackId == null || claim.trackId != trackId)
        }
        claims.getOrPut(file.absolutePath) { PathClaim(trackId) }.inFlight++
        file
    }

    /**
     * Ends one commit's write of [file]. The claim outlives a write that
     * [written] the file by [CLAIM_HOLD_NANOS]: until the worker's row
     * records the file, a later commit would otherwise still see it as
     * nobody's and replace it. NonCancellable: a release skipped by a cancel
     * would hold the name for the rest of the process.
     */
    private suspend fun releaseInternalFile(file: File, written: Boolean) {
        withContext(NonCancellable) {
            claimLock.withLock {
                val path = file.absolutePath
                val claim = claims[path] ?: return@withLock
                claim.inFlight--
                val now = System.nanoTime()
                if (written) claim.writtenAtNanos = now
                if (!claim.isLive(now)) claims.remove(path)
            }
        }
    }

    /**
     * A commit's claim on an internal path for track [trackId] (null: no row
     * yet, so it conflicts with every other commit). [inFlight] counts the
     * commits writing it now; [writtenAtNanos] is when one last wrote it.
     */
    private class PathClaim(val trackId: Long?) {
        var inFlight = 0
        var writtenAtNanos: Long? = null

        fun isLive(nowNanos: Long): Boolean =
            inFlight > 0 || writtenAtNanos?.let { nowNanos - it < CLAIM_HOLD_NANOS } == true
    }

    /**
     * Writes [tempFile] to [finalFile] through a `.part` file beside it, put in
     * place by one rename once complete. `copyTo(overwrite = true)` deleted
     * the old file first and, on a full disk, left a partial one at the name:
     * a failed re-download or upgrade destroyed the track's own audio, and the
     * partial file could later be adopted as the download. Now a failure
     * leaves the old file as it was. [replace] false (a new file): the move
     * refuses a name that is taken, even one that appeared since it was picked.
     */
    private fun writeInternalFile(tempFile: File, finalFile: File, replace: Boolean) {
        // Its own name per call: two commits of one track at once (a double
        // tap on Find in FLAC) must not write into one part file.
        val tag = java.util.UUID.randomUUID().toString().substring(0, 8)
        val part = File(finalFile.parentFile, "${finalFile.name}.$tag$PART_SUFFIX")
        try {
            tempFile.copyTo(part, overwrite = true)
            if (replace) {
                Files.move(
                    part.toPath(),
                    finalFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } else {
                Files.move(part.toPath(), finalFile.toPath())
            }
        } finally {
            part.delete() // gone already once moved
        }
    }

    /**
     * Writes [tempFile] into the user's SAF tree at [location]'s relative
     * path, creating intermediate directories on demand, under the first of
     * [suffixes]' names whose document no other track records (see
     * [commitDownload]). Returns the created document's URI as a string.
     * Throws if the tree URI is stale (permission revoked) or I/O fails —
     * caller should log and leave the temp file in place for retry.
     */
    private suspend fun writeToSafTree(
        tempFile: File,
        treeUri: Uri,
        location: LibraryLayoutResolver.ResolvedLocation,
        format: String,
        suffixes: Sequence<String?>,
        trackId: Long?,
    ): String {
        val cursor = openSafDir(treeUri, location)
        // Listed once: findFile per candidate name re-lists the folder each time.
        val listing = cursor.listFiles().mapNotNull { doc -> doc.name?.let { it to doc } }.toMap()
        for (suffix in suffixes) {
            val filename = fileNameFor(location, format, suffix)
            val existing = listing[filename]
            if (existing != null) {
                if (recordedByAnotherTrack(existing.uri.toString(), trackId)) continue
                // This track's own (or nobody's) file is at the name.
                return replaceSafDocument(tempFile, cursor, existing, filename, format)
            }
            val target = cursor.createFile(mimeTypeFor(format), filename)
                ?: error("Could not create SAF file '$filename' under ${cursor.uri}")
            // Path-based providers hand a new document a deleted one's URI,
            // which a track whose file went missing may still record.
            if (recordedByAnotherTrack(target.uri.toString(), trackId)) {
                target.delete()
                continue
            }
            copyIntoNewDocument(tempFile, target, filename)
            return target.uri.toString()
        }
        error("No free file name for '${location.baseName}.$format' under ${cursor.uri}")
    }

    /**
     * [writeToSafTree] for a new file ([commitDownload]'s `asNewFile`): never
     * deletes or reuses a document. The folder is listed once; a name in it
     * is taken. A document made at a deleted one's name gets that one's URI
     * back on path-based providers (ExternalStorageProvider), and a track may
     * still record it, so a URI a track records is given up and the next
     * name tried.
     */
    private suspend fun writeNewToSafTree(
        tempFile: File,
        treeUri: Uri,
        location: LibraryLayoutResolver.ResolvedLocation,
        format: String,
        nameSuffix: String?,
        trackId: Long?,
    ): String {
        val cursor = openSafDir(treeUri, location)
        val taken = cursor.listFiles().mapNotNullTo(HashSet()) { it.name }
        for (suffix in newFileSuffixes(nameSuffix)) {
            val filename = fileNameFor(location, format, suffix)
            if (filename in taken) continue
            val target = cursor.createFile(mimeTypeFor(format), filename)
                ?: error("Could not create SAF file '$filename' under ${cursor.uri}")
            var kept = false
            try {
                if (recordedByAnotherTrack(target.uri.toString(), trackId)) {
                    taken += filename
                    continue
                }
                copyIntoDocument(tempFile, target, filename)
                kept = true
                return target.uri.toString()
            } finally {
                // The document is this call's own: unless it now holds the
                // file, it goes (a taken URI, a failed check or copy).
                if (!kept) target.delete()
            }
        }
        error("No free file name for '${location.baseName}.$format' under ${cursor.uri}")
    }

    /**
     * Replaces [existing] with [tempFile]'s bytes without risking it: the new
     * file is written in full to a staging document first, and only then is
     * the old one deleted and the staging one renamed to [filename]. The old
     * code deleted the document, then created and filled a new one: a full or
     * ejected SD card left the track with no file but a partial one at its
     * name, which the next library check adopted as the download. On a
     * path-based provider the renamed document gets the old one's URI back,
     * so a row still recording it stays valid.
     */
    private fun replaceSafDocument(
        tempFile: File,
        cursor: DocumentFile,
        existing: DocumentFile,
        filename: String,
        format: String,
    ): String {
        val stagingName = "${filename.substringBeforeLast('.')}$STAGING_INFIX.$format"
        val staging = cursor.createFile(mimeTypeFor(format), stagingName)
            ?: error("Could not create SAF file '$stagingName' under ${cursor.uri}")
        copyIntoNewDocument(tempFile, staging, stagingName)
        if (!existing.delete()) {
            staging.delete()
            error("Could not replace SAF file '$filename' under ${cursor.uri}")
        }
        // A failed rename still leaves a complete file, under the staging name.
        if (!staging.renameTo(filename)) {
            android.util.Log.w("FileOrganizer", "commit: kept '$stagingName'; renaming it to '$filename' failed")
        }
        return staging.uri.toString()
    }

    /** [copyIntoDocument] into a document this call made, which goes again if the copy fails. */
    private fun copyIntoNewDocument(tempFile: File, target: DocumentFile, filename: String) {
        var copied = false
        try {
            copyIntoDocument(tempFile, target, filename)
            copied = true
        } finally {
            if (!copied) target.delete()
        }
    }

    /** The folder for [location] in the SAF tree, made as needed. */
    private fun openSafDir(treeUri: Uri, location: LibraryLayoutResolver.ResolvedLocation): DocumentFile {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Could not open SAF tree; permission may have been revoked: $treeUri")
        var cursor = root
        for (segment in location.segments) {
            cursor = cursor.findOrCreateDir(segment)
        }
        return cursor
    }

    private fun copyIntoDocument(tempFile: File, target: DocumentFile, filename: String) {
        tempFile.inputStream().use { input ->
            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                input.copyTo(output)
            } ?: error("Could not open SAF output stream for '$filename'")
        }
    }

    private fun DocumentFile.findOrCreateDir(name: String): DocumentFile =
        findFile(name)?.takeIf { it.isDirectory }
            ?: createDirectory(name)
            ?: error("Could not create SAF directory '$name' under $uri")

    private fun mimeTypeFor(format: String): String = when (format.lowercase()) {
        "m4a", "mp4", "aac" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> "audio/*"
    }

    /**
     * Result of a successful [commitDownload]. `filePath` is either an
     * absolute `java.io.File` path (internal storage) or a `content://…`
     * URI string (SAF target); `sizeBytes` is the track's size at the
     * moment it was written.
     */
    data class CommittedTrack(
        val filePath: String,
        val sizeBytes: Long,
    )
}

/**
 * One-walk breakdown of the music library on disk. Returned from
 * [FileOrganizer.computeMusicLibrarySize] so consumers get total + lossless
 * sizes + lossless count without three separate tree walks.
 */
data class LibrarySizeBreakdown(
    val totalBytes: Long,
    val losslessBytes: Long,
    val losslessFileCount: Int,
)
