package com.stash.data.download.backfill

import android.content.Context
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.MusicSource
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The backfill queues a wrong-version (music video) download again, but never
 * deletes its file: that file is the song's only copy until the replacement is
 * committed and validated, and the replacement may defer (no lossless copy
 * with the YouTube fallback off) or fail (a video taken down). It used to
 * delete the file first, so either left the song with nothing.
 */
class YtLibraryBackfillWorkerSharedFileTest {

    @get:Rule val tmp = TemporaryFolder()

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val downloadQueueDao: DownloadQueueDao = mockk(relaxed = true)

    @Test
    fun `a wrong-version file is kept while its replacement is queued`() = runTest {
        val file = tmp.newFile("lacrymosa.opus")
        coEvery { trackDao.getYtSourceVideoTitleCandidates() } returns listOf(musicVideo(file.absolutePath))

        worker().doWork()

        assertTrue(file.exists())
        coVerify(exactly = 1) { trackDao.markForReplacement(7L) }
        coVerify(exactly = 0) { trackDao.resetForReDownload(any()) }
        coVerify(exactly = 1) { downloadQueueDao.insert(match { it.trackId == 7L && it.youtubeUrl == null }) }
    }

    /** The user picked this video in Failed Matches (#531); a re-download would fetch it again. */
    @Test
    fun `a picked song is left alone`() = runTest {
        val file = tmp.newFile("lacrymosa.opus")
        coEvery { trackDao.getYtSourceVideoTitleCandidates() } returns
            listOf(musicVideo(file.absolutePath).copy(matchPickedAt = 1_000L))

        worker().doWork()

        assertTrue(file.exists())
        coVerify(exactly = 0) { trackDao.markForReplacement(any()) }
        coVerify(exactly = 0) { downloadQueueDao.insert(any()) }
    }

    /** Already known to be a music video, so no InnerTube lookup is made. */
    private fun musicVideo(filePath: String) = TrackEntity(
        id = 7L, title = "Lacrymosa (Official Video)", artist = "Evanescence", source = MusicSource.YOUTUBE,
        youtubeId = "omv-video", musicVideoType = "OMV", filePath = filePath, isDownloaded = true,
    )

    /** The foreground notification needs WorkManager; it is not what these tests are about. */
    private fun worker(): YtLibraryBackfillWorker {
        val worker = spyk(
            YtLibraryBackfillWorker(
                appContext = mockk<Context>(relaxed = true),
                params = mockk<WorkerParameters>(relaxed = true),
                trackDao = trackDao,
                downloadQueueDao = downloadQueueDao,
                searchExecutor = mockk(relaxed = true),
                syncNotificationManager = mockk(relaxed = true),
                syncScheduler = mockk(relaxed = true),
                canonicalizer = mockk(relaxed = true),
                syncPreferencesManager = mockk(relaxed = true) {
                    every { preferences } returns kotlinx.coroutines.flow.flowOf(com.stash.core.data.sync.SyncPreferences())
                },
            ),
            recordPrivateCalls = true,
        )
        every { worker["createForegroundInfo"](any<String>(), any<String>(), any<Float>()) } returns mockk<ForegroundInfo>()
        coEvery { worker.setForeground(any()) } just Runs
        return worker
    }
}
