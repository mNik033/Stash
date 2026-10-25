package com.stash.core.data.sync.workers

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkerParameters
import com.stash.core.data.blocklist.FileDeleter
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.TrackBlocklistDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.MusicSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The integrity pass deletes leaked blocked songs, file first. Two rows can
 * share a file (an import that took a downloaded song's file before imports
 * picked a name of their own), so the file goes only with the last row on it.
 */
class BlocklistIntegrityWorkerTest {

    private val database: StashDatabase = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val blocklistDao: TrackBlocklistDao = mockk(relaxed = true)
    private val downloadQueueDao: DownloadQueueDao = mockk(relaxed = true)
    private val fileDeleter: FileDeleter = mockk(relaxed = true)
    private val paths = mutableMapOf<Long, String?>()

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any>()) } coAnswers {
            secondArg<suspend () -> Any>().invoke()
        }
        coEvery { trackDao.countOtherTracksWithFilePath(any(), any()) } answers {
            val path = firstArg<String>()
            val trackId = secondArg<Long>()
            paths.count { (id, p) -> id != trackId && p == path }
        }
        coEvery { trackDao.deleteById(any()) } answers { paths.remove(firstArg()) }
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `a blocked song's file stays while a song that is not blocked plays from it`() = runTest {
        paths[1L] = FILE
        paths[2L] = FILE
        coEvery { blocklistDao.getAllKeys() } returns listOf("roberta flack|killing me softly")
        coEvery { trackDao.getAllForIntegrityScan() } returns listOf(
            entity(1L, "roberta flack", "killing me softly"),
            entity(2L, "roberta flack", "killing me softly with his song"),
        )

        worker().doWork()

        coVerify(exactly = 1) { trackDao.deleteById(1L) }
        coVerify(exactly = 0) { fileDeleter.delete(FILE) }
    }

    @Test
    fun `a file two blocked songs share goes with the second`() = runTest {
        paths[1L] = FILE
        paths[2L] = FILE
        coEvery { blocklistDao.getAllKeys() } returns listOf("roberta flack|killing me softly")
        coEvery { trackDao.getAllForIntegrityScan() } returns listOf(
            entity(1L, "roberta flack", "killing me softly"),
            entity(2L, "roberta flack", "killing me softly"),
        )

        worker().doWork()

        coVerify(exactly = 1) { fileDeleter.delete(FILE) }
    }

    @Test
    fun `a straggler the user holds is kept with its file, one nobody holds goes`() = runTest {
        // The block action deleted the song the user blocked; these rows only share its loose key.
        coEvery { blocklistDao.getAllKeys() } returns listOf("roberta flack|killing me softly")
        fun straggler(id: Long) = entity(id, "roberta flack", "killing me softly")
            .copy(title = "Killing Me Softly (Live)", filePath = "/music/$id.flac")
        val liked = straggler(1L).copy(stashLikedAt = 5L)
        val savedToSpotify = straggler(2L).copy(spotifySavedAt = 5L)
        val picked = straggler(3L).copy(matchPickedAt = 6L)
        val inLikedSongs = straggler(4L)
        val requested = straggler(5L)
        val nobodys = straggler(6L)
        coEvery { trackDao.isTrackInProtectedPlaylist(any()) } returns false
        coEvery { trackDao.isTrackInProtectedPlaylist(4L) } returns true
        coEvery { downloadQueueDao.hasUserRequest(any()) } returns false
        coEvery { downloadQueueDao.hasUserRequest(5L) } returns true
        coEvery { trackDao.getAllForIntegrityScan() } returns listOf(liked, savedToSpotify, picked, inLikedSongs, requested, nobodys)

        worker().doWork()

        for (kept in 1L..5L) {
            coVerify(exactly = 0) { trackDao.deleteById(kept) }
            coVerify(exactly = 0) { fileDeleter.delete("/music/$kept.flac") }
        }
        coVerify(exactly = 1) { trackDao.deleteById(6L) }
        coVerify(exactly = 1) { fileDeleter.delete("/music/6.flac") }
    }

    private fun entity(id: Long, canonicalArtist: String, canonicalTitle: String) = TrackEntity(
        id = id, title = canonicalTitle, artist = canonicalArtist, source = MusicSource.LOCAL,
        canonicalArtist = canonicalArtist, canonicalTitle = canonicalTitle,
        filePath = FILE, isDownloaded = true,
    )

    private fun worker() = BlocklistIntegrityWorker(
        appContext = mockk<Context>(relaxed = true),
        params = mockk<WorkerParameters>(relaxed = true),
        database = database,
        trackDao = trackDao,
        blocklistDao = blocklistDao,
        playlistDao = mockk(relaxed = true),
        downloadQueueDao = downloadQueueDao,
        fileDeleter = fileDeleter,
    )

    private companion object {
        const val FILE = "/music/roberta-flack/singles/killing-me-softly-with-his-song.flac"
    }
}
