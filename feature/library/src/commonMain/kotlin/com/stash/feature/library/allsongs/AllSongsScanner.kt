package com.stash.feature.library.allsongs

import com.stash.core.data.db.entity.ScanFolderEntity

/**
 * One folder's scan: songs [added], tags re-read for files that [updated] on disk, songs [removed]
 * because their file is gone, and whether some subfolder couldn't be listed ([incomplete]; nothing
 * was removed then).
 */
data class ScanResult(val added: Int, val updated: Int, val removed: Int, val incomplete: Boolean)

/** What deleting a song's file came to: [GONE] was already missing, [FAILED] (typically no write access) is still there. */
enum class FileDeletion { DELETED, GONE, FAILED }

/**
 * Scanner interface for indexing local audio files from user-selected folders.
 */
interface AllSongsScanner {
    suspend fun displayName(treeUri: String): String
    fun deleteFile(uri: String): FileDeletion
    suspend fun downloadTreeUri(): String?
    suspend fun conflictFor(treeUri: String, folders: List<ScanFolderEntity>): FolderConflict?
    suspend fun scan(folder: ScanFolderEntity): ScanResult
    suspend fun sweepArt()
    fun takeGrant(treeUri: String): Boolean
    suspend fun releaseGrant(treeUri: String, remaining: List<ScanFolderEntity>)
}