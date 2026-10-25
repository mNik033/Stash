package com.stash.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.SyncHistoryEntity
import com.stash.core.data.sync.workers.DailySyncTriggerWorker
import com.stash.core.data.sync.workers.PlaylistFetchWorker
import com.stash.core.model.SyncDisplayStatus
import com.stash.core.model.SyncState
import com.stash.core.model.SyncTrigger
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Auto-sync's daily trigger, "Sync now" while Auto-sync is on, and what Stop
 * and turning Auto-sync off cancel.
 *
 * Runs WorkManager's real scheduling through its test driver. The sync chain's
 * steps are stand-ins that finish once [stepGate] completes, so a test can hold
 * a step RUNNING. A step whose constraints the driver hasn't met stays waiting,
 * as it would for its network. The sync history is a real in-memory database,
 * so a test can check what the Sync tab's status card would read after Stop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class DailySyncTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = MutableStateFlow(SyncPreferences(autoSyncEnabled = true))
    private val prefsManager = mockk<SyncPreferencesManager> { every { preferences } returns prefs }
    private lateinit var scheduler: SyncScheduler
    private lateinit var workManager: WorkManager
    private lateinit var db: StashDatabase

    /** Chain steps finish when this completes; already complete = at once. */
    private var stepGate = CompletableDeferred(Unit)

    /** The input each chain step last started with, by worker class name. */
    private val stepInputs = mutableMapOf<String, Data>()

    @Before fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker =
                if (workerClassName == DailySyncTriggerWorker::class.java.name) {
                    DailySyncTriggerWorker(appContext, workerParameters, prefsManager, scheduler)
                } else {
                    stepInputs[workerClassName] = workerParameters.inputData
                    object : CoroutineWorker(appContext, workerParameters) {
                        override suspend fun doWork(): Result {
                            stepGate.await()
                            return Result.success()
                        }
                    }
                }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .setWorkerFactory(factory)
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        db = Room.inMemoryDatabaseBuilder(context, StashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduler = SyncScheduler(context, SyncStateManager(), db.syncHistoryDao())
        // Online, as the phone is for every case here; the offline "Sync now" is SyncSchedulerManualSyncTest's.
        scheduler.networkAvailable = { true }
    }

    @After fun tearDown() {
        stepGate.complete(Unit) // lets a held step finish
        WorkManagerTestInitHelper.closeWorkDatabase()
        db.close()
    }

    // -- "Sync now" ---------------------------------------------------------------

    /** The reported bug: with Auto-sync on, every tap was ignored until the scheduled time. */
    @Test fun `Sync now starts a sync while Auto-sync is on`() {
        scheduler.scheduleDailySync(6, 0)

        syncNow()

        val fetch = step()
        assertEquals(0L, fetch.initialDelayMillis)
        driver.setAllConstraintsMet(fetch.id)
        assertEquals(WorkInfo.State.SUCCEEDED, step().state)
    }

    @Test fun `Sync now replaces a scheduled sync that is waiting to start`() {
        fireTrigger() // the scheduled chain, waiting for Wi-Fi
        val scheduled = chainIds()
        assertEquals(NetworkType.UNMETERED, step().constraints.requiredNetworkType)

        syncNow()

        assertTrue(chainIds().none { it in scheduled })
        assertEquals(NetworkType.CONNECTED, step().constraints.requiredNetworkType)
    }

    @Test fun `Sync now is ignored while a sync is running`() {
        holdSyncRunning()
        val running = chainIds()

        syncNow()

        assertEquals(running, chainIds())
        assertEquals(WorkInfo.State.RUNNING, step().state)
    }

    /** Fetched, then Wi-Fi dropped: the Sync tab shows no sync, and a tap must not look dead. */
    @Test fun `Sync now replaces a sync paused between steps`() {
        fireTrigger()
        driver.setAllConstraintsMet(step().id) // fetch runs and finishes
        assertEquals(WorkInfo.State.ENQUEUED, step("sync_diff").state) // waits for Wi-Fi
        val paused = chainIds()

        syncNow()

        assertTrue(chainIds().none { it in paused })
        assertEquals(NetworkType.CONNECTED, step().constraints.requiredNetworkType)
    }

    /** An upgrade from the one-time schedule: its chain waits, hours away. */
    @Test fun `the old schedule's delayed chain neither blocks Sync now nor gets doubled`() {
        val legacy = enqueueLegacyChain()

        fireTrigger()
        assertEquals(setOf(legacy), chainIds()) // the trigger left it alone

        syncNow()
        assertEquals(0L, step().initialDelayMillis) // Sync now replaced it
    }

    // -- The schedule ------------------------------------------------------------------

    @Test fun `scheduling enqueues only the daily trigger, first run at the chosen time`() {
        scheduler.scheduleDailySync(6, 0)

        val trigger = trigger()!!
        assertEquals(TimeUnit.HOURS.toMillis(24), trigger.periodicityInfo!!.repeatIntervalMillis)
        assertAbout(scheduler.computeDelayToNextSync(6, 0)!!, trigger.initialDelayMillis)
        assertTrue(chain().isEmpty())

        scheduler.scheduleDailySync(18, 30) // a changed time takes effect
        assertAbout(scheduler.computeDelayToNextSync(18, 30)!!, trigger()!!.initialDelayMillis)
    }

    @Test fun `no sync days cancels the daily trigger`() {
        scheduler.scheduleDailySync(6, 0)
        assertNotNull(trigger())

        scheduler.scheduleDailySync(6, 0, days = DayOfWeekSet.NONE)

        assertNull(trigger())
    }

    // -- The trigger ---------------------------------------------------------------------

    @Test fun `the trigger starts the scheduled sync, on Wi-Fi only when set`() {
        fireTrigger()
        val fetch = step()
        assertEquals(0L, fetch.initialDelayMillis)
        assertEquals(NetworkType.UNMETERED, fetch.constraints.requiredNetworkType)
        assertTrue(fetch.constraints.requiresBatteryNotLow())

        stop()
        prefs.value = prefs.value.copy(wifiOnly = false)
        fireTrigger()

        assertEquals(NetworkType.CONNECTED, step().constraints.requiredNetworkType)
    }

    @Test fun `the trigger does nothing when Auto-sync is off`() {
        prefs.value = SyncPreferences(autoSyncEnabled = false)

        fireTrigger()

        assertTrue(chain().isEmpty())
    }

    /** "Undo last sync": the next scheduled run would redo what the user just undid. */
    @Test fun `the trigger does nothing while Auto-sync is paused after an undo, and keeps its time`() {
        val at = LocalDateTime.now().plusHours(3)
        prefs.value = SyncPreferences(
            autoSyncEnabled = true, syncHour = at.hour, syncMinute = at.minute, autoSyncPausedAfterUndo = true,
        )

        fireTrigger(at.hour, at.minute)

        assertTrue(chain().isEmpty())
        // The schedule itself stays, so the next "Sync now" resumes it as it was.
        val expected = System.currentTimeMillis() + scheduler.computeDelayToNextSync(at.hour, at.minute)!!
        assertAbout(expected, trigger()!!.nextScheduleTimeMillis)
    }

    @Test fun `Undo drops a scheduled sync still waiting for Wi-Fi, but not a running one`() = runBlocking {
        fireTrigger() // waits for Wi-Fi
        assertTrue(scheduler.cancelPendingSync())
        assertEquals(WorkInfo.State.CANCELLED, step().state)

        scheduler.cancelSync()
        holdSyncRunning()
        assertEquals(false, scheduler.cancelPendingSync())
        assertEquals(WorkInfo.State.RUNNING, step().state)
    }

    @Test fun `the trigger does nothing when today isn't a sync day`() {
        // Only the day after tomorrow: neither today nor the day an early-morning run counts for.
        val notToday = DayOfWeekSet.NONE.with(LocalDate.now().dayOfWeek.plus(2), true)
        prefs.value = SyncPreferences(autoSyncEnabled = true, syncDays = notToday.bitmask)

        fireTrigger()

        assertTrue(chain().isEmpty())
    }

    @Test fun `a run Doze held past midnight counts for the day it was due`() {
        // 2026-09-27 is a Sunday.
        assertEquals(DayOfWeek.SUNDAY, DailySyncTriggerWorker.dueDay(23, 30, clockAt(2026, 9, 28, 0, 10)))
        assertEquals(DayOfWeek.SUNDAY, DailySyncTriggerWorker.dueDay(23, 30, clockAt(2026, 9, 27, 23, 30)))
        assertEquals(DayOfWeek.MONDAY, DailySyncTriggerWorker.dueDay(6, 0, clockAt(2026, 9, 28, 9, 0)))
    }

    @Test fun `the trigger replaces its own waiting sync with the current Wi-Fi rule`() {
        fireTrigger() // waits for Wi-Fi
        val waiting = chainIds()
        prefs.value = prefs.value.copy(wifiOnly = false)

        fireTrigger() // the next day's run

        assertTrue(chainIds().none { it in waiting })
        assertEquals(NetworkType.CONNECTED, step().constraints.requiredNetworkType)
    }

    @Test fun `turning Wi-Fi-only off re-queues a waiting scheduled sync now`() = runBlocking {
        fireTrigger() // waits for Wi-Fi

        scheduler.applyWifiOnlyToWaitingSync(wifiOnly = false)

        assertEquals(NetworkType.CONNECTED, step().constraints.requiredNetworkType)
    }

    @Test fun `a queued Sync now is never turned into a Wi-Fi-only sync`() = runBlocking {
        syncNow() // waits for a network
        val queued = chainIds()

        fireTrigger()
        scheduler.applyWifiOnlyToWaitingSync(wifiOnly = true)

        assertEquals(queued, chainIds())
        assertEquals(NetworkType.CONNECTED, step().constraints.requiredNetworkType)
    }

    @Test fun `the trigger leaves a running sync alone`() {
        holdScheduledSyncRunning()
        val running = chainIds()

        fireTrigger()

        assertEquals(running, chainIds())
        assertEquals(WorkInfo.State.RUNNING, step().state)
    }

    /** Without this a periodic job runs 24 h after the last run ended, drifting later. */
    @Test fun `each trigger run pins the next run to the chosen time`() {
        val at = LocalDateTime.now().plusHours(3) // far from the plain 24 h period
        prefs.value = SyncPreferences(autoSyncEnabled = true, syncHour = at.hour, syncMinute = at.minute)

        fireTrigger(at.hour, at.minute)

        val expected = System.currentTimeMillis() + scheduler.computeDelayToNextSync(at.hour, at.minute)!!
        assertAbout(expected, trigger()!!.nextScheduleTimeMillis)
    }

    /**
     * The trigger stays alive (a periodic worker that throws is FAILED for good), and
     * retries soon: a pin set before queuing would push the retry to the next slot.
     */
    @Test fun `a run that fails to queue the day's sync retries soon, not at the next slot`() {
        val at = LocalDateTime.now().plusHours(3)
        prefs.value = SyncPreferences(autoSyncEnabled = true, syncHour = at.hour, syncMinute = at.minute)
        scheduler = spyk(scheduler)
        coEvery { scheduler.startScheduledSync(any()) } throws IllegalStateException("database busy")

        fireTrigger(at.hour, at.minute)

        val inMs = trigger()!!.nextScheduleTimeMillis - System.currentTimeMillis()
        assertTrue("retry within the hour, got in $inMs ms", inMs < TimeUnit.HOURS.toMillis(1))
    }

    /** The fetch step records it in the sync history ([PlaylistFetchWorker.KEY_TRIGGER]). */
    @Test fun `the fetch step is told whether the schedule or the user started the sync`() {
        fireTrigger()
        driver.setAllConstraintsMet(step().id)
        assertEquals(SyncTrigger.SCHEDULED.name, fetchTrigger())

        stop()
        syncNow()
        driver.setAllConstraintsMet(step().id)
        assertEquals(SyncTrigger.MANUAL.name, fetchTrigger())
    }

    // -- Stop and Auto-sync off ------------------------------------------------------------

    @Test fun `Stop cancels the sync but keeps the daily schedule`() {
        scheduler.scheduleDailySync(6, 0)
        holdSyncRunning()

        stop()

        assertEquals(WorkInfo.State.CANCELLED, step().state)
        assertNotNull(trigger())
    }

    /**
     * The status card at the top of the Sync tab reads the latest history row. Only
     * the download step closed its own row when stopped, so Stop mid-fetch left the
     * card on "Syncing..." beside an idle Sync button.
     */
    @Test fun `Stop mid-fetch closes the run as cancelled, so the status card stops saying Syncing`() {
        val earlier = recordRun(SyncState.COMPLETED, startedAt = Instant.now().minusSeconds(86_400))
        holdSyncRunning()
        val run = recordRun(SyncState.FETCHING_PLAYLISTS) // what the fetch step writes
        assertEquals(SyncDisplayStatus.Running, latestRun().toDisplayStatus())

        stop()

        val latest = latestRun()
        assertEquals(run, latest.id)
        assertEquals(SyncState.CANCELLED, latest.status)
        assertEquals(SyncDisplayStatus.Cancelled, latest.toDisplayStatus())
        assertNotNull(latest.completedAt)
        // A finished run keeps its own result.
        assertEquals(SyncState.COMPLETED, runBlocking { db.syncHistoryDao().getById(earlier) }!!.status)
    }

    /** Fetched, then waiting for Wi-Fi: no step runs, so none would ever close the row. */
    @Test fun `Stop on a sync paused between steps closes its run too`() {
        fireTrigger()
        driver.setAllConstraintsMet(step().id) // fetch runs and finishes
        assertEquals(WorkInfo.State.ENQUEUED, step("sync_diff").state)
        recordRun(SyncState.DIFFING)

        stop()

        assertEquals(WorkInfo.State.CANCELLED, step("sync_diff").state)
        assertEquals(SyncDisplayStatus.Cancelled, latestRun().toDisplayStatus())
    }

    @Test fun `Undo closes the run of the sync it drops, and leaves a running one's open`() = runBlocking {
        fireTrigger()
        driver.setAllConstraintsMet(step().id) // paused before the diff
        recordRun(SyncState.DIFFING)
        assertTrue(scheduler.cancelPendingSync())
        assertEquals(SyncState.CANCELLED, latestRun().status)

        holdSyncRunning()
        recordRun(SyncState.FETCHING_PLAYLISTS)
        assertEquals(false, scheduler.cancelPendingSync())
        assertEquals(SyncState.FETCHING_PLAYLISTS, latestRun().status)
    }

    @Test fun `turning Auto-sync off cancels the trigger and a scheduled sync not yet started`() = runBlocking {
        fireTrigger() // the scheduled chain, waiting for Wi-Fi

        scheduler.cancelDailySync()

        assertNull(trigger())
        assertEquals(WorkInfo.State.CANCELLED, step().state)
    }

    @Test fun `turning Auto-sync off cancels the old schedule's delayed chain`() = runBlocking {
        enqueueLegacyChain()

        scheduler.cancelDailySync()

        assertEquals(WorkInfo.State.CANCELLED, step().state)
    }

    @Test fun `turning Auto-sync off leaves a queued Sync now alone`() = runBlocking {
        syncNow() // waits for a network

        scheduler.cancelDailySync()

        assertEquals(WorkInfo.State.ENQUEUED, step().state)
    }

    @Test fun `turning Auto-sync off lets a scheduled sync that has started finish`() = runBlocking {
        holdScheduledSyncRunning()
        scheduler.cancelDailySync()
        assertEquals(WorkInfo.State.RUNNING, step().state)

        stepGate.complete(Unit) // fetch finishes; diff waits for Wi-Fi
        assertEquals(WorkInfo.State.ENQUEUED, step("sync_diff").state)
        scheduler.cancelDailySync()
        assertEquals(WorkInfo.State.ENQUEUED, step("sync_diff").state)
    }

    // -- App start --------------------------------------------------------------------------

    @Test fun `the startup check clears a leftover trigger while Auto-sync is off`() = runBlocking {
        scheduler.scheduleDailySync(6, 0)

        scheduler.ensureDailySync(SyncPreferences(autoSyncEnabled = false))

        assertNull(trigger())
    }

    @Test fun `the startup check adds the trigger when Auto-sync is on, and keeps an existing one's time`() = runBlocking {
        scheduler.ensureDailySync(SyncPreferences(autoSyncEnabled = true, syncHour = 6, syncMinute = 0))
        val first = trigger()!!
        assertAbout(scheduler.computeDelayToNextSync(6, 0)!!, first.initialDelayMillis)

        scheduler.ensureDailySync(SyncPreferences(autoSyncEnabled = true, syncHour = 18, syncMinute = 30))

        assertEquals(first.id, trigger()!!.id)
        assertEquals(first.initialDelayMillis, trigger()!!.initialDelayMillis)
    }

    // -- Helpers ------------------------------------------------------------------------------

    private val driver get() = WorkManagerTestInitHelper.getTestDriver(context)!!

    /** The Sync tab's Stop. */
    private fun stop() = runBlocking { scheduler.cancelSync() }

    /** A sync history row as the chain's steps write it; returns its id. */
    private fun recordRun(status: SyncState, startedAt: Instant = Instant.now()): Long = runBlocking {
        db.syncHistoryDao().insert(SyncHistoryEntity(status = status, startedAt = startedAt))
    }

    /** The row the Sync tab's status card shows. */
    private fun latestRun(): SyncHistoryEntity = runBlocking { db.syncHistoryDao().getLatest() }!!

    /** The Sync tab's "Sync now" (with Wi-Fi only on, the default). */
    private fun syncNow() = runBlocking { scheduler.triggerManualSync(wifiOnly = true) }

    private fun chain(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(SyncScheduler.UNIQUE_WORK_NAME).get()

    private fun chainIds() = chain().map { it.id }.toSet()

    /** The chain step tagged [tag]. */
    private fun step(tag: String = "sync_fetch"): WorkInfo = chain().single { tag in it.tags }

    private fun fetchTrigger(): String? =
        stepInputs[PlaylistFetchWorker::class.java.name]?.getString(PlaylistFetchWorker.KEY_TRIGGER)

    /** The live daily trigger, or null when there's none. */
    private fun trigger(): WorkInfo? =
        workManager.getWorkInfosForUniqueWork(SyncScheduler.TRIGGER_WORK_NAME).get()
            .singleOrNull { !it.state.isFinished }

    /** Schedules Auto-sync at [hour]:[minute] and runs its trigger now, as WorkManager would then. */
    private fun fireTrigger(hour: Int = 6, minute: Int = 0) {
        scheduler.scheduleDailySync(hour, minute)
        driver.setInitialDelayMet(trigger()!!.id)
    }

    /** A manual sync held with its fetch step RUNNING. */
    private fun holdSyncRunning() {
        stepGate = CompletableDeferred()
        syncNow()
        driver.setAllConstraintsMet(step().id)
        assertEquals(WorkInfo.State.RUNNING, step().state)
    }

    /** The trigger's sync held with its fetch step RUNNING. */
    private fun holdScheduledSyncRunning() {
        fireTrigger()
        stepGate = CompletableDeferred()
        driver.setAllConstraintsMet(step().id)
        assertEquals(WorkInfo.State.RUNNING, step().state)
    }

    /** What the one-time schedule left queued for an upgrading user: the sync itself, hours away. */
    private fun enqueueLegacyChain(): java.util.UUID {
        val legacy = OneTimeWorkRequestBuilder<PlaylistFetchWorker>()
            .setInitialDelay(18, TimeUnit.HOURS)
            .addTag("sync_fetch")
            .build()
        workManager.enqueueUniqueWork(SyncScheduler.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, legacy)
        return legacy.id
    }

    private fun clockAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Clock {
        val zone = ZoneId.of("UTC")
        return Clock.fixed(LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant(), zone)
    }

    /** Delays computed a moment apart differ by the milliseconds between them. */
    private fun assertAbout(expectedMs: Long, actualMs: Long) =
        assertTrue("expected about $expectedMs, got $actualMs", abs(expectedMs - actualMs) < 5_000)
}
