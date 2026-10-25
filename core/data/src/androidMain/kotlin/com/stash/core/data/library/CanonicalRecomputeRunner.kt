package com.stash.core.data.library

import com.stash.core.common.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs the one-time [CanonicalRecompute] to the end, one run at a time, for both of its callers:
 * the startup worker ([com.stash.core.data.sync.workers.CanonicalRecomputeWorker]) and the sync,
 * which runs it inline before its diff files songs by their words
 * ([com.stash.core.data.sync.workers.DiffWorker]): a sync that ran first would miss every row
 * still in the old strict words and file a permanent duplicate of each.
 *
 * Once [CanonicalRecomputeState] says it is done, a call is a single DataStore read.
 */
@Singleton
class CanonicalRecomputeRunner @Inject constructor(
    private val recompute: CanonicalRecompute,
    private val state: CanonicalRecomputeState,
) {

    /** One run at a time: the worker and a sync can both arrive; the second then finds it done. */
    private val mutex = Mutex()

    /**
     * Re-keys whatever is left, from where a stopped run left off, until [CanonicalRecomputeState]
     * says it is done. A re-arm that lands during a run (a merged backup) makes it run again from
     * the first row before returning, so the rows that backup brought back are re-keyed too.
     *
     * Cancellable between batches; a cancelled or failed run resumes after its last committed batch.
     *
     * @return true when it ran, false when it was already done.
     */
    suspend fun runIfPending(): Boolean = mutex.withLock {
        var ran = false
        while (true) {
            val pass = state.pendingPass() ?: break
            ran = true
            val outcome = recompute.run(afterId = pass.cursor) { lastId -> state.saveCursor(pass, lastId) }
            val finished = state.markDone(pass)
            Log.i(
                TAG,
                "re-keyed ${outcome.rekeyed} of ${outcome.scanned} rows after id ${pass.cursor}; " +
                    "${outcome.keptBlocked} kept their words (blocked song)" +
                    if (finished) "" else "; re-armed meanwhile, running again from the first row",
            )
        }
        ran
    }

    private companion object {
        const val TAG = "CanonicalRecompute"
    }
}
