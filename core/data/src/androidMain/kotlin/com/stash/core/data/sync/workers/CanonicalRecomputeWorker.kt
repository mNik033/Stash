package com.stash.core.data.sync.workers

import android.content.Context
import com.stash.core.common.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stash.core.data.library.CanonicalRecompute
import com.stash.core.data.library.CanonicalRecomputeRunner
import com.stash.core.data.library.CanonicalRecomputeState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Runs the one-time [CanonicalRecompute] after the schema 55 migration: rows a stricter rule wrote
 * get the one loose rule's canonical words, so lookups by words find them. No row is merged or
 * deleted.
 *
 * Once per install: enqueued at startup ([enqueueIfPending]) only until [CanonicalRecomputeState]
 * says it finished, as unique work with `KEEP` so a relaunch never starts a second copy. A run that
 * is stopped (cancelled, the process killed, an error) resumes after the last batch it committed;
 * an error retries with WorkManager's backoff, and after [MAX_ATTEMPTS] the next app start enqueues
 * a fresh run. No constraints: it is local, a few seconds of short transactions, and lookups find
 * the old rows only once it has run. The sync runs it too, inline, if it gets there first; both go
 * through [CanonicalRecomputeRunner], one run at a time.
 */
@HiltWorker
class CanonicalRecomputeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val runner: CanonicalRecomputeRunner,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        runner.runIfPending()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "stopped by an error; the next attempt resumes after the last batch", e)
        if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "CanonicalRecompute"

        /** Unique work name; the `_v1` matches [CanonicalRecomputeState.RULE_VERSION]. */
        const val WORK_NAME = "canonical_recompute_v1"

        /** Attempts per enqueue before giving up until the next app start. */
        private const val MAX_ATTEMPTS = 5

        /**
         * Startup: enqueues the recompute unless it already finished on this install. `KEEP`, so a
         * relaunch while it runs never starts a second copy.
         *
         * @return true when the work was enqueued (or was already pending).
         */
        suspend fun enqueueIfPending(context: Context, state: CanonicalRecomputeState): Boolean {
            if (state.isDone()) return false
            enqueue(context, ExistingWorkPolicy.KEEP)
            return true
        }

        /**
         * Right after a re-arm (a merged backup, which restarts nothing): runs the recompute now, or
         * after the run already under way. `KEEP` would drop this request while a run is finishing,
         * past its last look at the state but not yet succeeded, and leave the merged rows in their
         * old words until the next app start.
         */
        fun enqueueAfterRearm(context: Context) {
            enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                policy,
                OneTimeWorkRequestBuilder<CanonicalRecomputeWorker>().build(),
            )
        }
    }
}
