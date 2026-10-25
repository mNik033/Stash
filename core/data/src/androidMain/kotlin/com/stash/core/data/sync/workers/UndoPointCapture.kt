package com.stash.core.data.sync.workers

import com.stash.core.common.Log
import com.stash.core.data.db.dao.SyncUndoDao
import com.stash.core.data.sync.SyncLog
import kotlinx.coroutines.CancellationException

/**
 * Takes sync [syncId]'s "Undo last sync" restore point unless it already has
 * one ([SyncUndoDao.captureOnce]). Called by whichever step changes the library
 * first: the fetch step just before it hides Spotify playlists the account no
 * longer has, else the diff step before it touches anything. The point used to
 * be taken only by the diff, after the fetch had hidden them, so Undo never
 * brought those back.
 *
 * Never fatal: a sync that works must not be blocked because the safety net
 * couldn't be set up. Worst case the user has no undo for this run, which is
 * exactly where they were before the feature.
 */
internal suspend fun captureUndoPoint(syncUndoDao: SyncUndoDao, syncLog: SyncLog, syncId: Long, tag: String) {
    try {
        syncUndoDao.captureOnce(syncId, System.currentTimeMillis())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(tag, "Undo restore point capture failed for sync $syncId", e)
        syncLog.warn("Couldn't save a restore point â€” undo unavailable for this sync")
    }
}
