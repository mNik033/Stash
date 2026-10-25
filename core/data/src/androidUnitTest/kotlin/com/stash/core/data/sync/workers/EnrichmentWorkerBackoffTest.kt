package com.stash.core.data.sync.workers

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.work.WorkerParameters
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.TrackTagDao
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.lastfm.LastFmApiClient
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Both enrichment workers take the newest tracks first and end a run after
 * five failures in a row. When the five newest tracks failed the same way every
 * time (a response that won't parse), every run stopped on them and no older
 * track was ever enriched, so mixes quietly lost their tag signal. Tracks a run
 * failed on now back off, and the next run starts past them.
 */
class EnrichmentWorkerBackoffTest {

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
    private val backoff by lazy {
        LastFmEnrichmentBackoff(PreferenceDataStoreFactory.create { File(tmp.root, "backoff.preferences_pb") })
    }

    /** Library ids, newest first; the five newest always fail. */
    private val library = (1L..20L).toList()
    private val broken = (1L..5L).toSet()
    private val done = mutableSetOf<Long>()

    /** What Last.fm's client gives back when the response won't parse. */
    private val unparseable = IllegalArgumentException("Element class kotlinx.serialization.json.JsonPrimitive is not a JsonObject")

    private fun seedLibrary() {
        library.forEach { id ->
            coEvery { trackDao.getById(id) } returns TrackEntity(id = id, title = "Song $id", artist = "Artist $id")
        }
    }

    private fun idOf(artist: String): Long = artist.removePrefix("Artist ").toLong()

    @Test fun `tags - tracks that keep failing at the head no longer block older tracks`() = runTest {
        seedLibrary()
        coEvery { trackTagDao.findUntaggedDownloadedTrackIds(any()) } answers {
            library.filterNot { it in done }.take(firstArg())
        }
        coEvery { trackTagDao.replaceForTrack(any(), any()) } answers { done += firstArg<Long>() }
        coEvery { apiClient.getTrackTopTags(any(), any()) } answers {
            if (idOf(firstArg()) in broken) Result.failure(unparseable) else Result.success(listOf(LastFmTag("shoegaze", 1f)))
        }
        coEvery { apiClient.getArtistTopTags(any()) } answers {
            if (idOf(firstArg()) in broken) Result.failure(unparseable) else Result.success(emptyList())
        }

        tagWorker().doWork()
        assertTrue("the first run still stops on five failures in a row", done.isEmpty())

        tagWorker().doWork()

        assertEquals("the next run starts past the failing tracks", (6L..20L).toSet(), done)
        coVerify(exactly = 1) { apiClient.getTrackTopTags("Artist 1", any()) }
    }

    @Test fun `track info - tracks that keep failing at the head no longer block older tracks`() = runTest {
        seedLibrary()
        coEvery { trackDao.findTracksNeedingLastfmEnrichment(any()) } answers {
            library.filterNot { it in done }.take(firstArg())
        }
        coEvery { trackDao.setLastfmEnrichment(any(), any(), any(), any(), any()) } answers { done += firstArg<Long>() }
        coEvery { apiClient.getTrackInfo(any(), any(), any()) } answers {
            if (idOf(firstArg()) in broken) {
                Result.failure(unparseable)
            } else {
                Result.success(
                    LastFmTrackInfo(
                        mbid = "m", durationMs = null, listeners = 1, playcount = 1,
                        userPlaycount = 2, userLoved = false, bestImageUrl = null, tags = emptyList(),
                    ),
                )
            }
        }

        infoWorker().doWork()
        assertTrue(done.isEmpty())

        infoWorker().doWork()

        assertEquals((6L..20L).toSet(), done)
        coVerify(exactly = 1) { apiClient.getTrackInfo("Artist 1", any(), any()) }
    }

    @Test fun `a failure between successes is skipped and the run goes on`() = runTest {
        seedLibrary()
        coEvery { trackTagDao.findUntaggedDownloadedTrackIds(any()) } answers {
            library.filterNot { it in done }.take(firstArg())
        }
        coEvery { trackTagDao.replaceForTrack(any(), any()) } answers { done += firstArg<Long>() }
        coEvery { apiClient.getTrackTopTags(any(), any()) } answers {
            if (idOf(firstArg()) == 3L) Result.failure(unparseable) else Result.success(listOf(LastFmTag("dub", 1f)))
        }
        coEvery { apiClient.getArtistTopTags(any()) } returns Result.failure(unparseable)

        tagWorker().doWork()

        assertEquals(library.toSet() - 3L, done)
    }

    private fun tagWorker() = TagEnrichmentWorker(appContext, workerParams, trackDao, trackTagDao, apiClient, credentials, backoff)

    private fun infoWorker() =
        TrackInfoEnrichmentWorker(appContext, workerParams, trackDao, apiClient, credentials, sessionPreference, backoff)
}
