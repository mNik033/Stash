package com.stash.core.data.sync.workers

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.TrackTagDao
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.lastfm.LastFmApiClient
import com.stash.core.data.lastfm.LastFmApiException
import com.stash.core.data.lastfm.LastFmCredentials
import com.stash.core.data.lastfm.LastFmEnrichmentBackoff
import com.stash.core.data.lastfm.LastFmSession
import com.stash.core.data.lastfm.LastFmSessionPreference
import com.stash.core.data.lastfm.LastFmTag
import com.stash.core.data.lastfm.LastFmTrackInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Both Last.fm enrichment workers write a PERMANENT "nothing to find" sentinel
 * (`__untaggable__` / mbid = "") so a track Last.fm doesn't know isn't asked
 * about every day. They used to write it on ANY failed call, so a short
 * network drop or a 429 burst poisoned every track it touched for good. Only a
 * real answer (empty, or Last.fm's "not found") may produce the sentinel now.
 */
class EnrichmentWorkerTransientFailureTest {

    @get:Rule val tmp = TemporaryFolder()

    private val appContext: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val trackTagDao: TrackTagDao = mockk(relaxed = true)
    private val apiClient: LastFmApiClient = mockk()
    private val credentials = LastFmCredentials(apiKey = "key", apiSecret = "secret")
    private val sessionPreference: LastFmSessionPreference = mockk {
        every { session } returns flowOf(LastFmSession(username = "me", sessionKey = "sk"))
    }

    private val notFound = LastFmApiException(LastFmApiException.NOT_FOUND, "Track not found")

    private val backoff by lazy {
        LastFmEnrichmentBackoff(PreferenceDataStoreFactory.create { File(tmp.root, "backoff.preferences_pb") })
    }

    private fun seedTrack(id: Long) {
        coEvery { trackDao.getById(id) } returns TrackEntity(id = id, title = "Song $id", artist = "Artist $id")
    }

    // ── TagEnrichmentWorker ──────────────────────────────────────────────

    private fun tagWorker() = TagEnrichmentWorker(appContext, workerParams, trackDao, trackTagDao, apiClient, credentials, backoff)

    @Test fun `tags - a transient failure writes no sentinel`() = runTest {
        coEvery { trackTagDao.findUntaggedDownloadedTrackIds(any()) } returns listOf(1L)
        seedTrack(1L)
        coEvery { apiClient.getTrackTopTags(any(), any()) } returns Result.failure(IOException("offline"))
        coEvery { apiClient.getArtistTopTags(any()) } returns Result.failure(IOException("offline"))

        val result = tagWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { trackTagDao.replaceForTrack(any(), any()) }
    }

    @Test fun `tags - a transient track failure is not settled by an empty artist answer`() = runTest {
        coEvery { trackTagDao.findUntaggedDownloadedTrackIds(any()) } returns listOf(1L)
        seedTrack(1L)
        coEvery { apiClient.getTrackTopTags(any(), any()) } returns Result.failure(IOException("offline"))
        coEvery { apiClient.getArtistTopTags(any()) } returns Result.success(emptyList())

        tagWorker().doWork()

        coVerify(exactly = 0) { trackTagDao.replaceForTrack(any(), any()) }
    }

    @Test fun `tags - Last_fm not knowing the track or artist still writes the sentinel`() = runTest {
        coEvery { trackTagDao.findUntaggedDownloadedTrackIds(any()) } returns listOf(1L)
        seedTrack(1L)
        coEvery { apiClient.getTrackTopTags(any(), any()) } returns Result.failure(notFound)
        coEvery { apiClient.getArtistTopTags(any()) } returns Result.success(emptyList())

        tagWorker().doWork()

        coVerify(exactly = 1) {
            trackTagDao.replaceForTrack(1L, match { rows -> rows.single().tag == "__untaggable__" })
        }
    }

    @Test fun `tags - artist fallback still lands when the track call failed`() = runTest {
        coEvery { trackTagDao.findUntaggedDownloadedTrackIds(any()) } returns listOf(1L)
        seedTrack(1L)
        coEvery { apiClient.getTrackTopTags(any(), any()) } returns Result.failure(IOException("offline"))
        coEvery { apiClient.getArtistTopTags(any()) } returns Result.success(listOf(LastFmTag("shoegaze", 1f)))

        tagWorker().doWork()

        coVerify(exactly = 1) {
            trackTagDao.replaceForTrack(1L, match { rows -> rows.single().tag == "shoegaze" })
        }
    }

    @Test fun `tags - an outage ends the batch early instead of burning every request`() = runTest {
        val ids = (1L..20L).toList()
        coEvery { trackTagDao.findUntaggedDownloadedTrackIds(any()) } returns ids
        ids.forEach(::seedTrack)
        coEvery { apiClient.getTrackTopTags(any(), any()) } returns Result.failure(IOException("offline"))
        coEvery { apiClient.getArtistTopTags(any()) } returns Result.failure(IOException("offline"))

        tagWorker().doWork()

        coVerify(exactly = 5) { apiClient.getTrackTopTags(any(), any()) }
    }

    // ── TrackInfoEnrichmentWorker ────────────────────────────────────────

    private fun infoWorker() = TrackInfoEnrichmentWorker(appContext, workerParams, trackDao, apiClient, credentials, sessionPreference, backoff)

    @Test fun `track info - a transient failure writes no sentinel`() = runTest {
        coEvery { trackDao.findTracksNeedingLastfmEnrichment(any()) } returns listOf(1L)
        seedTrack(1L)
        coEvery { apiClient.getTrackInfo(any(), any(), any()) } returns Result.failure(IOException("offline"))

        val result = infoWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { trackDao.setLastfmEnrichment(any(), any(), any(), any(), any()) }
    }

    @Test fun `track info - Last_fm not knowing the track still writes the sentinel`() = runTest {
        coEvery { trackDao.findTracksNeedingLastfmEnrichment(any()) } returns listOf(1L)
        seedTrack(1L)
        coEvery { apiClient.getTrackInfo(any(), any(), any()) } returns Result.failure(notFound)

        infoWorker().doWork()

        coVerify(exactly = 1) {
            trackDao.setLastfmEnrichment(trackId = 1L, mbid = "", userPlaycount = 0, listeners = null, userLoved = false)
        }
    }

    @Test fun `track info - an answer is stored`() = runTest {
        coEvery { trackDao.findTracksNeedingLastfmEnrichment(any()) } returns listOf(1L)
        seedTrack(1L)
        coEvery { apiClient.getTrackInfo(any(), any(), any()) } returns Result.success(
            LastFmTrackInfo(
                mbid = "abc", durationMs = null, listeners = 10, playcount = 20,
                userPlaycount = 3, userLoved = true, bestImageUrl = null, tags = emptyList(),
            )
        )

        infoWorker().doWork()

        coVerify(exactly = 1) {
            trackDao.setLastfmEnrichment(trackId = 1L, mbid = "abc", userPlaycount = 3, listeners = 10L, userLoved = true)
        }
    }
}
