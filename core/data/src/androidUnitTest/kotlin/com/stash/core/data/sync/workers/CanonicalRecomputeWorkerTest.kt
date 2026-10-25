package com.stash.core.data.sync.workers

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.stash.core.common.strictCanonical
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.library.CanonicalRecompute
import com.stash.core.data.library.CanonicalRecomputeRunner
import com.stash.core.data.library.CanonicalRecomputeState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [CanonicalRecomputeWorker], its runner [CanonicalRecomputeRunner] and its gate
 * [CanonicalRecomputeState]: the re-key runs once per install, one run at a time, resumes where a
 * stopped run left off, and is re-armed by a restored backup without a re-arm ever being lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class CanonicalRecomputeWorkerTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: StashDatabase
    private lateinit var storeScope: CoroutineScope
    private lateinit var state: CanonicalRecomputeState

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, StashDatabase::class.java).allowMainThreadQueries().build()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        state = CanonicalRecomputeState(
            PreferenceDataStoreFactory.create(scope = storeScope) { java.io.File(tmp.root, "canonical_recompute.preferences_pb") },
        )
    }

    @After fun tearDown() {
        storeScope.cancel()
        db.close()
    }

    private fun worker() = TestListenableWorkerBuilder<CanonicalRecomputeWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                CanonicalRecomputeWorker(appContext, workerParameters, CanonicalRecomputeRunner(CanonicalRecompute(db), state))
        })
        .build()

    private suspend fun strictRows(count: Int): List<Long> = db.trackDao().insertAll(
        (1..count).map { TrackEntity(title = "Song $it (feat. X)", artist = "A & B", canonicalTitle = strictCanonical("Song $it (feat. X)"), canonicalArtist = "a b") },
    )

    private suspend fun words(id: Long) = db.trackDao().getById(id)!!.let { it.canonicalTitle to it.canonicalArtist }

    @Test fun `a first run re-keys every strict row and marks the recompute done`() = runBlocking {
        val ids = strictRows(3)
        assertFalse(state.isDone())

        assertTrue(worker().doWork() is ListenableWorker.Result.Success)

        assertTrue(state.isDone())
        assertNull("nothing left to run", state.pendingPass())
        ids.forEachIndexed { i, id -> assertEquals("song ${i + 1}" to "a, b", words(id)) }
    }

    @Test fun `once done it never runs again`() = runBlocking {
        state.markDone(state.pendingPass()!!)
        val id = strictRows(1).single()

        assertTrue(worker().doWork() is ListenableWorker.Result.Success)

        assertEquals("song 1 feat x" to "a b", words(id))
    }

    @Test fun `a stopped run resumes after the saved cursor`() = runBlocking {
        val ids = strictRows(4)
        // A previous run committed the first two rows' batch, then the process died.
        state.saveCursor(state.pendingPass()!!, ids[1])

        assertTrue(worker().doWork() is ListenableWorker.Result.Success)

        assertEquals("rows before the cursor are not read again", "song 1 feat x" to "a b", words(ids[0]))
        assertEquals("song 3" to "a, b", words(ids[2]))
        assertEquals("song 4" to "a, b", words(ids[3]))
        assertTrue(state.isDone())
    }

    @Test fun `a restored backup re-arms it from the first row`() = runBlocking {
        val pass = state.pendingPass()!!
        state.saveCursor(pass, 42L)
        state.markDone(pass)

        state.rearm()

        assertFalse(state.isDone())
        assertEquals(0L, state.pendingPass()!!.cursor)
    }

    @Test fun `a re-arm after a run's last read is not lost`() = runBlocking {
        // A run read its last batch; a merged backup re-arms before the run marks it done.
        val pass = state.pendingPass()!!
        state.saveCursor(pass, 42L)
        state.rearm()

        assertFalse("the run that began before the re-arm can't mark it done", state.markDone(pass))
        assertFalse(state.isDone())
        assertEquals("the restart from the first row stands", 0L, state.pendingPass()!!.cursor)

        state.saveCursor(pass, 99L)
        assertEquals("the old run's cursor can't overwrite it either", 0L, state.pendingPass()!!.cursor)

        assertTrue("the next run, under the new generation, can", state.markDone(state.pendingPass()!!))
        assertTrue(state.isDone())
    }

    @Test fun `a re-arm during a run makes it run again from the first row`() = runBlocking {
        val recompute = mockk<CanonicalRecompute>()
        val startedAfter = mutableListOf<Long>()
        coEvery { recompute.run(any(), any(), any()) } coAnswers {
            val afterId = firstArg<Long>()
            startedAfter += afterId
            // A backup merges while the first run is under way.
            if (startedAfter.size == 1) state.rearm()
            CanonicalRecompute.Outcome(scanned = 0, rekeyed = 0, keptBlocked = 0, lastId = afterId)
        }
        // The first run resumes one that stopped after row 7.
        state.saveCursor(state.pendingPass()!!, 7L)

        assertTrue(CanonicalRecomputeRunner(recompute, state).runIfPending())

        assertEquals("resumed, then again from the first row", listOf(7L, 0L), startedAfter)
        assertTrue(state.isDone())
    }

    @Test fun `the worker and the sync never run it at once, and the second finds it done`() = runBlocking {
        val recompute = mockk<CanonicalRecompute>()
        val release = CompletableDeferred<Unit>()
        var runs = 0
        coEvery { recompute.run(any(), any(), any()) } coAnswers {
            runs++
            release.await()
            CanonicalRecompute.Outcome(scanned = 0, rekeyed = 0, keptBlocked = 0, lastId = 0L)
        }
        val runner = CanonicalRecomputeRunner(recompute, state)

        val worker = async { runner.runIfPending() }
        val sync = async { runner.runIfPending() }
        yield()
        release.complete(Unit)

        assertEquals("exactly one of them ran it", 1, listOf(worker.await(), sync.await()).count { it })
        assertEquals(1, runs)
        assertTrue(state.isDone())
    }

    /**
     * What the stand-in recompute waits on, and never gets. A field, so the test holds the waiting worker: the garbage
     * collector would otherwise fail it mid-test (FutureGarbageCollectedException) and end the RUNNING run.
     */
    private val neverDone = CompletableDeferred<ListenableWorker.Result>()

    @Test fun `startup enqueues one run while pending, and nothing once done`() = runBlocking {
        // The real worker needs Hilt; this stand-in stays RUNNING, like a recompute still under way.
        val neverFinishes = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                object : CoroutineWorker(appContext, workerParameters) {
                    override suspend fun doWork(): ListenableWorker.Result = neverDone.await()
                }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(neverFinishes).build(),
        )
        val workManager = WorkManager.getInstance(context)
        try {
            assertTrue(CanonicalRecomputeWorker.enqueueIfPending(context, state))
            assertTrue("a relaunch while it runs", CanonicalRecomputeWorker.enqueueIfPending(context, state))
            assertEquals(
                "unique work, KEEP: never a second run",
                1,
                workManager.getWorkInfosForUniqueWork(CanonicalRecomputeWorker.WORK_NAME).get().size,
            )

            state.markDone(state.pendingPass()!!)
            assertFalse(CanonicalRecomputeWorker.enqueueIfPending(context, state))
        } finally {
            // Stop the stand-in and close this WorkManager's database while both are alive.
            workManager.cancelAllWork().result.get()
            WorkManagerTestInitHelper.closeWorkDatabase()
        }
    }

    @Test fun `a merge's re-arm queues a run after the one under way, never drops it`() = runBlocking {
        // A stand-in still RUNNING, like a recompute past its last look at the state.
        val neverFinishes = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                object : CoroutineWorker(appContext, workerParameters) {
                    override suspend fun doWork(): ListenableWorker.Result = neverDone.await()
                }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(neverFinishes).build(),
        )
        val workManager = WorkManager.getInstance(context)
        try {
            assertTrue(CanonicalRecomputeWorker.enqueueIfPending(context, state))

            CanonicalRecomputeWorker.enqueueAfterRearm(context)

            val runs = workManager.getWorkInfosForUniqueWork(CanonicalRecomputeWorker.WORK_NAME).get()
            assertEquals("the re-arm's run is kept, not dropped", 2, runs.size)
            assertEquals(
                "and waits for the one under way",
                1,
                runs.count { it.state == androidx.work.WorkInfo.State.BLOCKED },
            )
        } finally {
            workManager.cancelAllWork().result.get()
            WorkManagerTestInitHelper.closeWorkDatabase()
        }
    }
}
