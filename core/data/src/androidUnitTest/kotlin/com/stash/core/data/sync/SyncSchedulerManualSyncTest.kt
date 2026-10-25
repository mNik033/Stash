package com.stash.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
import com.stash.core.data.db.dao.SyncHistoryDao
import com.stash.core.data.sync.workers.DiffWorker
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Only [SyncScheduler] knows whether the user tapped "Sync now". It tells the
 * diff step, whose kept-playlist sweep (#474) then downloads on any network like
 * the sync's own downloads; a scheduled sync keeps the Sync tab's Wi-Fi rule.
 *
 * Offline, "Sync now" is queued and the Sync tab says "Waiting for network"; it
 * used to sit on "Authenticating…" for as long as the phone stayed offline. When
 * it finally runs, its downloads follow the Wi-Fi rule like a scheduled sync's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SyncSchedulerManualSyncTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val state = SyncStateManager()
    private lateinit var scheduler: SyncScheduler

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        scheduler = SyncScheduler(context, state, mockk<SyncHistoryDao>(relaxed = true))
    }

    /** Closed here, not by the garbage collector in the middle of another test. */
    @After fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test fun `a manual sync tells the diff step, a scheduled one does not`() {
        scheduler.networkAvailable = { true }

        runBlocking { scheduler.triggerManualSync(wifiOnly = true) }
        assertTrue(diffStepInput().getBoolean(DiffWorker.KEY_MANUAL_SYNC, false))

        // A scheduled sync never starts over another one (#520), so the manual chain goes first.
        runBlocking { scheduler.cancelSync() }
        runBlocking { scheduler.startScheduledSync(wifiOnly = false) }
        assertFalse(diffStepInput().getBoolean(DiffWorker.KEY_MANUAL_SYNC, true))
    }

    @Test fun `online, Sync now authenticates at once and downloads on any network`() {
        scheduler.networkAvailable = { true }

        runBlocking { scheduler.triggerManualSync(wifiOnly = true) }

        assertEquals(SyncPhase.Authenticating, state.phase.value)
        assertEquals(NetworkType.CONNECTED, step("sync_fetch").constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, step("sync_download").constraints.requiredNetworkType)
    }

    @Test fun `offline, Sync now waits for a network, then downloads on Wi-Fi only as set`() {
        scheduler.networkAvailable = { false }

        runBlocking { scheduler.triggerManualSync(wifiOnly = true) }

        assertEquals(SyncPhase.WaitingForNetwork, state.phase.value)
        // Queued, not refused: the fetch starts on any connection...
        assertEquals(WorkInfo.State.ENQUEUED, step("sync_fetch").state)
        assertEquals(NetworkType.CONNECTED, step("sync_fetch").constraints.requiredNetworkType)
        // ...but by then "now, on any network" no longer holds for the downloads.
        assertEquals(NetworkType.UNMETERED, step("sync_download").constraints.requiredNetworkType)
        assertFalse(diffStepInput().getBoolean(DiffWorker.KEY_MANUAL_SYNC, true))
    }

    @Test fun `offline with Wi-Fi only off, the queued sync downloads on any network`() {
        scheduler.networkAvailable = { false }

        runBlocking { scheduler.triggerManualSync(wifiOnly = false) }

        assertEquals(SyncPhase.WaitingForNetwork, state.phase.value)
        assertEquals(NetworkType.CONNECTED, step("sync_download").constraints.requiredNetworkType)
    }

    @Test fun `Cancel on a sync waiting for a network drops it`() {
        scheduler.networkAvailable = { false }
        runBlocking { scheduler.triggerManualSync(wifiOnly = true) }

        runBlocking { scheduler.cancelSync() }

        assertEquals(SyncPhase.Idle, state.phase.value)
        assertEquals(WorkInfo.State.CANCELLED, step("sync_fetch").state)
    }

    private fun step(tag: String): WorkInfo =
        WorkManager.getInstance(context).getWorkInfosByTag(tag).get().last()

    /** The live diff step's own input (WorkManager merges the fetch step's output in at run time). */
    private fun diffStepInput(): Data {
        val id = WorkManager.getInstance(context).getWorkInfosByTag("sync_diff").get()
            .single { !it.state.isFinished }.id
        return WorkManagerImpl.getInstance(context).workDatabase.workSpecDao().getWorkSpec(id.toString())!!.input
    }
}
