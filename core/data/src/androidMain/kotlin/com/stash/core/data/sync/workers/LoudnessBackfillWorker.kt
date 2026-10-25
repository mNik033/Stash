package com.stash.core.data.sync.workers

import android.content.Context
import com.stash.core.common.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.stash.core.data.audio.LoudnessMeasurer
import com.stash.core.data.audio.LoudnessProgressStore
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.sync.enqueueUniquePeriodicWorkReviving
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that drains `tracks.loudness_measured_at IS NULL`
 * rows in batches of [BATCH_SIZE], invoking [LoudnessMeasurer] to compute
 * integrated LUFS + true-peak via ffmpeg's `ebur128` filter and writing the
 * results back through [TrackDao.updateLoudness] / [TrackDao.markLoudnessFailed].
 *
 * ## Scheduling
 *
 * Runs every 6 hours under WorkManager's standard constraint suite â€” charging
 * + idle + battery-not-low. Measuring 20 lossless tracks burns ~30s of CPU
 * and decodes hundreds of MB of audio; we are emphatically not running this
 * while the user is on battery or actively using the device. The 6-hour
 * cadence is a compromise: long enough that even a million-track library
 * drains in a few weeks of overnight charging sessions, short enough that
 * a freshly-imported batch on a Sunday afternoon doesn't sit unmeasured
 * until Wednesday.
 *
 * ## Batching strategy
 *
 * Each invocation processes up to [BATCH_SIZE] rows and exits â€” we
 * deliberately don't loop until the queue is empty because:
 *  1. WorkManager has a 10-minute soft cap on a single worker run, and
 *     measuring lossless audio can run 1-2s per track on cold cache.
 *  2. Bounded batches give the OS a chance to reclaim ffmpeg memory
 *     between runs (the bridge spawns a fresh native process each call,
 *     but the JNI surface area is large enough that long-running batches
 *     leak Dalvik refs in the wild).
 *  3. The periodic schedule means we'll be back in 6 hours anyway. There
 *     is no scenario where draining a 10,000-track library in one shot
 *     beats draining it across 500 idle-overnight runs.
 *
 * ## Cancellation
 *
 * `isStopped` is checked at the top of each iteration â€” a track caught
 * mid-measurement when WorkManager kills the worker is **not** marked
 * failed. The row stays NULL and the next run picks it up. Idempotent.
 *
 * ## Progress reporting
 *
 * After each batch, [LoudnessProgressStore.recordBatchComplete] is called
 * with the actual count processed (which may be less than the batch size
 * if cancelled mid-flight). The EqualizerScreen progress card reads from
 * the same store via a Flow, so the UI stays in sync without any extra
 * plumbing.
 */
@HiltWorker
open class LoudnessBackfillWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val trackDao: TrackDao,
    private val measurer: LoudnessMeasurer,
    private val progressStore: LoudnessProgressStore,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val deadline = System.currentTimeMillis() + MAX_RUN_MS
        var page = trackDao.tracksNeedingLoudness(afterId = 0L, limit = BATCH_SIZE)
        if (page.isEmpty()) {
            progressStore.setRemaining(0)
            Log.i(TAG, "LoudnessBackfill: nothing to do")
            return Result.success(workDataOf(KEY_DONE to true))
        }
        Log.i(TAG, "LoudnessBackfill: up to $BATCH_SIZE tracks to measure")
        var batchCompleted = 0
        var skipped = 0
        // Rows it cannot measure (missing file, SAF content:// path) are left
        // unstamped so a re-download still gets measured, so the worker pages
        // past them by id instead of re-reading one fixed first page. Before,
        // 250 such rows at the front meant every run measured nothing.
        paging@ while (page.isNotEmpty()) {
            for (track in page) {
                if (batchCompleted >= BATCH_SIZE || shouldStop() ||
                    System.currentTimeMillis() > deadline
                ) break@paging
                val file = track.filePath?.let { File(it) }?.takeIf { it.exists() }
                if (file == null) {
                    skipped++
                    continue
                }
                measureOne(track.id, file)
                batchCompleted++
                onTrackProcessed()
            }
            if (page.size < BATCH_SIZE) break
            page = trackDao.tracksNeedingLoudness(afterId = page.last().id, limit = BATCH_SIZE)
        }
        progressStore.recordBatchComplete(
            completed = batchCompleted,
            at = System.currentTimeMillis(),
        )
        Log.i(TAG, "LoudnessBackfill complete: processed $batchCompleted, skipped $skipped unmeasurable")
        return Result.success()
    }

    /** Measures one file and writes the result, or the failed stamp, back. */
    private suspend fun measureOne(trackId: Long, file: File) {
        when (val r = measurer.measure(file)) {
            is LoudnessMeasurer.Result.Success ->
                trackDao.updateLoudness(
                    id = trackId,
                    lufs = r.lufs,
                    peak = r.truePeakDbfs,
                    now = System.currentTimeMillis(),
                )
            is LoudnessMeasurer.Result.Failed -> {
                Log.w(TAG, "LoudnessBackfill: measurement failed for trackId=$trackId: ${r.reason}")
                trackDao.markLoudnessFailed(
                    id = trackId,
                    now = System.currentTimeMillis(),
                )
            }
        }
    }

    /**
     * Test seam â€” invoked once per track after the row is written back.
     * Production builds do nothing; the test subclass uses it to drive
     * its own iteration counter so [shouldStop] can fire after N tracks.
     */
    protected open fun onTrackProcessed() {
        // no-op in production
    }

    /**
     * Cancellation predicate. Wraps WorkManager's `isStopped` so the test
     * subclass can simulate mid-batch cancellation â€” `isStopped` itself
     * is a `final` accessor on [androidx.work.ListenableWorker] and can't
     * be overridden directly.
     */
    protected open fun shouldStop(): Boolean = isStopped

    companion object {
        private const val TAG = "LoudnessBackfill"

        /** Unique work name â€” KEEP-policy enqueue de-dupes across cold starts. */
        const val WORK_NAME = "loudness-backfill"

        /** Output-data key signalling an empty queue (nothing left to measure). */
        const val KEY_DONE = "done"

        /**
         * Max tracks measured per worker run â€” see class kdoc.
         *
         * Raised from 20 once downloads stopped measuring on battery: this
         * worker is now the primary path, not a mop-up for the odd missed
         * row, so a 500-track sync has to be able to drain. The real limiter
         * is [MAX_RUN_MS], and every run is already charging + device-idle,
         * so a bigger batch costs the user nothing â€” where 20/run meant a
         * heavy sync took days of charge cycles to level-match.
         */
        private const val BATCH_SIZE = 250

        /**
         * Soft per-run budget. WorkManager itself enforces a 10-minute hard
         * cap on foreground workers; we exit cleanly a hair earlier so the
         * batch-complete bookkeeping always lands instead of dying mid-write.
         */
        private const val MAX_RUN_MS = 10L * 60 * 1000

        /**
         * Schedule the 6-hour periodic backfill. Idempotent â€” uses
         * [ExistingPeriodicWorkPolicy.KEEP] so re-calls (e.g. on every cold
         * start from `StashApplication.onCreate`) don't reset the next-run
         * timer or clobber an in-flight execution. A finished (e.g. FAILED)
         * job is enqueued again.
         */
        suspend fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<LoudnessBackfillWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWorkReviving(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
