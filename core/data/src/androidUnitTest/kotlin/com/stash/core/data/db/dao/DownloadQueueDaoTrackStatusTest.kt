package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.DownloadQueueEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.DownloadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [DownloadQueueDao.observeStatusForTrack]: the live status Now Playing's download ring follows,
 * one per track even when the track carries several queue rows.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadQueueDaoTrackStatusTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: DownloadQueueDao

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.downloadQueueDao()
        db.trackDao().insert(TrackEntity(id = 1L, title = "T", artist = "A", canonicalTitle = "t", canonicalArtist = "a"))
        db.trackDao().insert(TrackEntity(id = 2L, title = "U", artist = "A", canonicalTitle = "u", canonicalArtist = "a"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(trackId: Long, status: DownloadStatus) =
        DownloadQueueEntity(trackId = trackId, syncId = null, status = status, searchQuery = "q", userRequested = true)

    @Test
    fun `a track with no queue row has no status`() = runTest {
        assertNull(dao.observeStatusForTrack(1L).first())
    }

    @Test
    fun `follows the row's status as it changes`() = runTest {
        val id = dao.insert(row(1L, DownloadStatus.PENDING))
        assertEquals(DownloadStatus.PENDING, dao.observeStatusForTrack(1L).first())

        dao.claimForDownload(id)
        assertEquals(DownloadStatus.IN_PROGRESS, dao.observeStatusForTrack(1L).first())

        dao.updateStatus(id = id, status = DownloadStatus.FAILED)
        assertEquals(DownloadStatus.FAILED, dao.observeStatusForTrack(1L).first())
    }

    @Test
    fun `of several rows, the one furthest from settled speaks for the track`() = runTest {
        dao.insert(row(1L, DownloadStatus.FAILED))
        dao.insert(row(1L, DownloadStatus.WAITING_FOR_LOSSLESS))
        assertEquals(DownloadStatus.WAITING_FOR_LOSSLESS, dao.observeStatusForTrack(1L).first())

        val tapped = dao.insert(row(1L, DownloadStatus.PENDING))
        assertEquals(DownloadStatus.PENDING, dao.observeStatusForTrack(1L).first())

        dao.claimForDownload(tapped)
        assertEquals(DownloadStatus.IN_PROGRESS, dao.observeStatusForTrack(1L).first())
    }

    @Test
    fun `another track's rows don't count`() = runTest {
        dao.insert(row(2L, DownloadStatus.IN_PROGRESS))
        dao.insert(row(1L, DownloadStatus.SKIPPED))

        assertEquals(DownloadStatus.SKIPPED, dao.observeStatusForTrack(1L).first())
    }
}
