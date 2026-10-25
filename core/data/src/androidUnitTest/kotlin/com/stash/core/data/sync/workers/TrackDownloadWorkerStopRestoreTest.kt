package com.stash.core.data.sync.workers

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.DownloadQueueEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.files.LocalFileOps
import com.stash.core.data.files.TrackFileReplacement
import com.stash.core.data.sync.DownloadJobRegistry
import com.stash.core.data.sync.TrackDownloader
import com.stash.core.model.DownloadStatus
import com.stash.core.model.MusicSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A track the YouTube-library backfill handed back for a better copy
 * (markForReplacement: not downloaded, file kept) got its kept file back as
 * its download on FAILED, on Deferred and on a cancel after the commit, but
 * not when the download was stopped before it returned anything: the song
 * dropped out of the Library with its file unseen on disk. Every stop now
 * restores it, in both of TrackDownloadWorker's modes. A real Room database
 * and real files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TrackDownloadWorkerStopRestoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: StashDatabase
    private val trackDownloader = mockk<TrackDownloader>()
    private lateinit var keptFile: File

    @Before fun setUp() {
        // The workers build their notification's Cancel action through WorkManager.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, StashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        keptFile = tmp.newFile("lacrymosa.opus").apply { writeBytes(ByteArray(20_000)) }
    }

    @After fun tearDown() { db.close() }

    // ── Single-track mode ────────────────────────────────────────────────

    @Test fun `single track - a replacement stopped mid-download keeps its file`() = runBlocking {
        val (trackId, queueId) = trackHandedBackForReplacement()
        // WorkManager stops the work while the download runs.
        coEvery { trackDownloader.downloadTrack(any(), any()) } throws CancellationException("stopped")

        try {
            singleTrackWorker(queueId).doWork()
        } catch (expected: CancellationException) {
        }

        assertKeptFileIsTheDownload(trackId)
        assertEquals("the claim is handed back", DownloadStatus.PENDING, db.downloadQueueDao().getStatusById(queueId))
    }

    @Test fun `single track - a replacement the user cancels mid-download keeps its file`() = runBlocking {
        val (trackId, queueId) = trackHandedBackForReplacement()
        coEvery { trackDownloader.downloadTrack(any(), any()) } coAnswers {
            db.downloadQueueDao().markCancelledIfActive(queueId, System.currentTimeMillis())
            throw CancellationException("cancelled")
        }

        val result = singleTrackWorker(queueId).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertKeptFileIsTheDownload(trackId)
    }

    @Test fun `single track - a first download stopped mid-way restores nothing`() = runBlocking {
        val trackId = db.trackDao().insert(song())
        val queueId = db.downloadQueueDao().insert(DownloadQueueEntity(trackId = trackId, userRequested = true))
        coEvery { trackDownloader.downloadTrack(any(), any()) } throws CancellationException("stopped")

        try {
            singleTrackWorker(queueId).doWork()
        } catch (expected: CancellationException) {
        }

        assertFalse(db.trackDao().getById(trackId)!!.isDownloaded)
    }

    // ── Chain mode ───────────────────────────────────────────────────────

    @Test fun `chain - a replacement the user cancels mid-download keeps its file`() = runBlocking {
        val (trackId, queueId) = trackHandedBackForReplacement()
        coEvery { trackDownloader.downloadTrack(any(), any()) } coAnswers {
            db.downloadQueueDao().markCancelledIfActive(queueId, System.currentTimeMillis())
            throw CancellationException("cancelled")
        }

        chainWorker().doWork()

        assertKeptFileIsTheDownload(trackId)
    }

    @Test fun `chain - a replacement stopped mid-download keeps its file`() = runBlocking {
        val (trackId, _) = trackHandedBackForReplacement()
        // Not the user: the row stays IN_PROGRESS for the next run to reset.
        coEvery { trackDownloader.downloadTrack(any(), any()) } throws CancellationException("stopped")

        chainWorker().doWork()

        assertKeptFileIsTheDownload(trackId)
    }

    private suspend fun assertKeptFileIsTheDownload(trackId: Long) {
        val row = db.trackDao().getById(trackId)!!
        assertTrue("back in the Library on its kept file", row.isDownloaded)
        assertEquals(keptFile.absolutePath, row.filePath)
        assertTrue(keptFile.exists())
    }

    /** A downloaded track the backfill handed back for a better copy, and its queue row. */
    private suspend fun trackHandedBackForReplacement(): Pair<Long, Long> {
        val trackId = db.trackDao().insert(song(filePath = keptFile.absolutePath, isDownloaded = true))
        db.trackDao().markForReplacement(trackId)
        val queueId = db.downloadQueueDao().insert(DownloadQueueEntity(trackId = trackId, userRequested = true))
        return trackId to queueId
    }

    /** BOTH: what chain mode asks for when no service is connected. */
    private fun song(filePath: String? = null, isDownloaded: Boolean = false) = TrackEntity(
        title = "Lacrymosa", artist = "Evanescence", durationMs = 1000L, source = MusicSource.BOTH,
        canonicalTitle = "lacrymosa", canonicalArtist = "evanescence",
        filePath = filePath, isDownloaded = isDownloaded,
    )

    private fun singleTrackWorker(queueId: Long) = buildWorker(workDataOf(TrackDownloadWorker.KEY_QUEUE_ID to queueId))

    private fun chainWorker() = buildWorker(workDataOf(DiffWorker.KEY_SYNC_ID to 1L))

    private fun buildWorker(input: androidx.work.Data): TrackDownloadWorker =
        TestListenableWorkerBuilder<TrackDownloadWorker>(context)
            .setInputData(input)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = TrackDownloadWorker(
                    appContext, workerParameters,
                    downloadQueueDao = db.downloadQueueDao(),
                    trackDao = db.trackDao(),
                    syncHistoryDao = mockk(relaxed = true),
                    syncStateManager = mockk(relaxed = true),
                    syncNotificationManager = mockk(relaxed = true),
                    trackDownloader = trackDownloader,
                    tokenManager = mockk(relaxed = true),
                    audioDurationExtractor = mockk(relaxed = true),
                    blocklistGuard = mockk(relaxed = true),
                    streamingPreference = mockk(relaxed = true),
                    classifier = mockk(relaxed = true),
                    reconciliationUseCase = mockk(relaxed = true),
                    fileExistenceSessionFactory = mockk(relaxed = true),
                    fileAdopter = mockk(relaxed = true),
                    syncLog = mockk(relaxed = true),
                    flacUpgradeSweeper = mockk(relaxed = true),
                    downloadJobs = DownloadJobRegistry(),
                    fileReplacement = TrackFileReplacement(db.trackDao(), LocalFileOps(appContext)),
                )
            })
            .build()
}
