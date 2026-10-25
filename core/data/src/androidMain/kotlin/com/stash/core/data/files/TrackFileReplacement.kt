package com.stash.core.data.files

import com.stash.core.common.Log
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.fileUsedByAnotherTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What happens to the file a track had before a re-download, once the
 * download is over. The download workers capture the track's file path
 * before the download and hand it here with the outcome:
 *
 *  - committed ([deleteReplacedFile]): the old file goes, but only after the
 *    row records the new one. Deleting it as soon as the new file passed
 *    validation (TrackDownloaderImpl, 889c24ae) lost both when the worker was
 *    cancelled before the row write (its cancel branch deletes the new file)
 *    or the process died in between: the row still pointed at the old file.
 *  - not coming ([restoreKeptFile]): a track the YouTube-library backfill
 *    handed back for a better copy ([TrackDao.markForReplacement]) keeps its
 *    file but is not downloaded; when the download defers or fails for good,
 *    the kept file is its download again, or the song drops out of the
 *    Library while its file sits unseen on disk.
 *  - cancelled after commit ([discardCancelledDownload]): the new file goes,
 *    unless it IS the kept file (a same-name re-download replaces it in
 *    place), and the kept file is restored.
 *  - stopped anywhere else ([restoreAfterStop]): the kept file is restored.
 */
@Singleton
class TrackFileReplacement @Inject constructor(
    private val trackDao: TrackDao,
    private val localFileOps: LocalFileOps,
) {

    /**
     * Deletes [oldPath], the file track [trackId] had before a download whose
     * file at [newPath] the row now records. Call only after that row write
     * succeeded. Kept: the new file itself (a same-name re-download), and a
     * file another track still plays from.
     */
    suspend fun deleteReplacedFile(trackId: Long, oldPath: String?, newPath: String) = bestEffort("delete $oldPath") {
        if (oldPath.isNullOrBlank()) return@bestEffort
        if (localFileOps.isSameFile(oldPath, newPath)) return@bestEffort
        if (trackDao.fileUsedByAnotherTrack(oldPath, trackId)) return@bestEffort
        val deleted = localFileOps.delete(oldPath)
        Log.d(TAG, "deleted the replaced file $oldPath for trackId=$trackId: $deleted")
    }

    /**
     * Marks track [trackId] downloaded again from [keptPath], the file it had
     * before a download that is not going to replace it (deferred, failed for
     * good, cancelled). A no-op for a track that had no file, whose file is
     * gone, or whose row moved on (see [TrackDao.restoreKeptFile]).
     */
    suspend fun restoreKeptFile(trackId: Long, keptPath: String?) = bestEffort("restore $keptPath") {
        if (keptPath.isNullOrBlank() || !localFileOps.exists(keptPath)) return@bestEffort
        if (trackDao.restoreKeptFile(trackId, keptPath) > 0) {
            Log.i(TAG, "trackId=$trackId keeps its file $keptPath: the replacement isn't coming")
        }
    }

    /**
     * [restoreKeptFile] for a download that stopped (the user cancelled it, or
     * WorkManager stopped the worker) before its row recorded a new file,
     * wherever it stopped: before the download returned anything, or after
     * the commit. The kept file is the one the row still records; the DAO
     * restores only a row [TrackDao.markForReplacement] left, so for every
     * other track, and once the row records the new file, this does nothing.
     * Without it a replacement stopped mid-download kept the song out of the
     * Library with its file unseen on disk. NonCancellable: it runs as the
     * coroutine is being cancelled.
     */
    suspend fun restoreAfterStop(trackId: Long) = withContext(NonCancellable) {
        bestEffort("restore the kept file of trackId=$trackId after a stop") {
            restoreKeptFile(trackId, trackDao.getById(trackId)?.filePath)
        }
    }

    /**
     * A download committed [newPath] but the user cancelled it before the row
     * recorded it: the new file goes, unless it is [keptPath] itself (replaced
     * in place, so it is the track's only copy), and the kept file is
     * restored.
     */
    suspend fun discardCancelledDownload(trackId: Long, keptPath: String?, newPath: String) {
        if (!localFileOps.isSameFile(keptPath, newPath)) localFileOps.delete(newPath)
        restoreKeptFile(trackId, keptPath)
    }

    /**
     * Runs [block], logging instead of throwing: it runs after the download's
     * outcome is settled, and a failed cleanup must not turn a finished
     * download into a failed one, or bury the failure being handled. A failed
     * cleanup leaves at worst a leftover file or a hidden song, never a lost one.
     */
    private inline fun bestEffort(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "couldn't $what", e)
        }
    }

    private companion object {
        const val TAG = "TrackFileReplacement"
    }
}
