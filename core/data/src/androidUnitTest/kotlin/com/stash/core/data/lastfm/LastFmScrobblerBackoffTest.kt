package com.stash.core.data.lastfm

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.ListeningEventEntity
import com.stash.core.data.db.entity.TrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The scrobble queue drains whenever a listen is recorded. Offline — the
 * mode a lot of this app's users actually live in — every one of those
 * submissions is doomed, and the old drain sent the WHOLE backlog anyway:
 * up to 100 HTTP requests per recorded listen, each paying DNS plus a
 * connect timeout and holding the radio awake, with no backoff, no attempt
 * ceiling and no kill switch. Twenty tracks played offline could mean
 * thousands of guaranteed-to-fail requests.
 *
 * Two rules fix that, and these tests pin them: a pass stops at its first
 * failure instead of marching through the rest, and consecutive failures
 * open the shared [LastFmRateLimitGate] so later passes skip the network
 * entirely until the cooldown elapses.
 */
class LastFmScrobblerBackoffTest {

    private val session = LastFmSession(username = "u", sessionKey = "sk")

    private fun event(id: Long) = ListeningEventEntity(
        id = id,
        trackId = id,
        startedAt = 1_000_000L + id,
        scrobbled = false,
        completedAt = 1_000_000L + id,
    )

    private fun track(id: Long) = TrackEntity(
        id = id,
        title = "t$id",
        artist = "a$id",
        album = "al",
        durationMs = 180_000,
    )

    private class Harness(
        val apiClient: LastFmApiClient = mockk(relaxed = true),
        val listeningEventDao: ListeningEventDao = mockk(relaxed = true),
        val trackDao: TrackDao = mockk(relaxed = true),
        val gate: LastFmRateLimitGate = LastFmRateLimitGate(),
        val sessionPreference: LastFmSessionPreference = mockk(relaxed = true),
    )

    private fun scrobbler(h: Harness): LastFmScrobbler {
        val sessionPreference = h.sessionPreference
        every { sessionPreference.session } returns flowOf(session)
        every { sessionPreference.firstArtistOnly } returns flowOf(false)
        val credentials: LastFmCredentials = mockk()
        every { credentials.isConfigured } returns true
        return LastFmScrobbler(
            apiClient = h.apiClient,
            sessionPreference = sessionPreference,
            listeningEventDao = h.listeningEventDao,
            trackDao = h.trackDao,
            credentials = credentials,
            rateLimitGate = h.gate,
        )
    }

    @Test
    fun `a failing submission abandons the rest of the pass`() = runTest {
        val h = Harness()
        val pending = (1L..10L).map { event(it) }
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns pending
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.failure(java.net.UnknownHostException("offline"))

        scrobbler(h).drainNow()

        // One doomed request, not ten. Offline, the second through tenth
        // cannot succeed for any reason the first did not already prove.
        coVerify(exactly = 1) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an open breaker skips the network entirely`() = runTest {
        val h = Harness()
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns listOf(event(1))
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.failure(java.net.UnknownHostException("offline"))

        // Automatic passes (the collector's): a manual sync is a deliberate
        // probe and goes out anyway, see the test below.
        val s = scrobbler(h)
        s.drainQueue(session) // trips the breaker
        s.drainQueue(session) // must not reach the network
        s.drainQueue(session)

        coVerify(exactly = 1) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a manual sync during the backoff still tries and says the plays are held`() = runTest {
        val h = Harness()
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns listOf(event(1))
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.failure(java.net.UnknownHostException("offline"))

        val s = scrobbler(h)
        s.drainQueue(session) // an automatic pass fails and opens the breaker
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returnsMany listOf(listOf(event(1)), listOf(event(1)), emptyList())

        val result = s.drainNow() // the network is back; the user taps

        coVerify(exactly = 2) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
        assertThat(result.submitted).isEqualTo(1)
        assertThat(result.held).isFalse()

        // And a tap that fails says so instead of "No new scrobbles to send".
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.failure(java.net.UnknownHostException("offline"))
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns listOf(event(1))
        val failed = s.drainNow()
        assertThat(failed.held).isTrue()
        assertThat(failed.submitted).isEqualTo(0)
    }

    @Test
    fun `a healthy pass submits the whole backlog`() = runTest {
        val h = Harness()
        val pending = (1L..10L).map { event(it) }
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns pending
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.success(Unit)

        scrobbler(h).drainNow()

        // Backing off must never cost a user their history when the network
        // is fine: every pending row still goes.
        coVerify(exactly = 10) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a success after failures reopens the path`() = runTest {
        val h = Harness()
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns listOf(event(1))
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.failure(java.net.UnknownHostException("offline"))

        val s = scrobbler(h)
        s.drainNow()

        // Network comes back, and the gate is manually closed the way a
        // successful read elsewhere in the app would close it.
        h.gate.recordSuccess(LastFmScrobbler.SCROBBLE_GATE_KEY)
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        s.drainNow()

        coVerify(exactly = 2) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a manual sync during an automatic pass does not scrobble the same plays twice`() = runTest {
        val h = Harness()
        val all = (1L..3L).map { event(it) }
        val scrobbled = mutableSetOf<Long>()
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } answers { all.filter { it.id !in scrobbled } }
        coEvery { h.listeningEventDao.markScrobbled(any()) } answers { scrobbled += firstArg<Long>() }
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        // The first submission hangs until both passes have started.
        val lastFmAnswers = CompletableDeferred<Unit>()
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } coAnswers {
            lastFmAnswers.await()
            Result.success(Unit)
        }

        val s = scrobbler(h)
        val automatic = async { s.drainNow() }
        val manual = async { s.drainNow() }
        runCurrent()
        lastFmAnswers.complete(Unit)
        automatic.await()
        manual.await()

        // Each play once. Without single-flight both passes held rows 1-3.
        coVerify(exactly = 3) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a revoked session disconnects Last-fm instead of retrying forever`() = runTest {
        val h = Harness()
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns (1L..3L).map { event(it) }
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.failure(LastFmApiException(9, "Invalid session key"))

        scrobbler(h).drainNow()

        coVerify(exactly = 1) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { h.sessionPreference.clear() }
        // The plays stay queued for after the user reconnects.
        coVerify(exactly = 0) { h.listeningEventDao.markScrobbled(any()) }
    }

    @Test
    fun `a play Last-fm refuses does not block the plays behind it`() = runTest {
        val h = Harness()
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns (1L..3L).map { event(it) }
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        coEvery {
            h.apiClient.scrobble(any(), "a1", any(), any(), any())
        } returns Result.failure(LastFmApiException(6, "Invalid parameters"))

        scrobbler(h).drainNow()

        coVerify(exactly = 3) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
        coVerify { h.listeningEventDao.markScrobbled(1L) }
        coVerify { h.listeningEventDao.markScrobbled(3L) }
        coVerify(exactly = 0) { h.sessionPreference.clear() }
    }

    @Test
    fun `a Last-fm outage still backs off and keeps the play`() = runTest {
        val h = Harness()
        coEvery { h.listeningEventDao.pendingScrobbles(any()) } returns (1L..3L).map { event(it) }
        coEvery { h.trackDao.getById(any()) } answers { track(firstArg()) }
        coEvery {
            h.apiClient.scrobble(any(), any(), any(), any(), any())
        } returns Result.failure(LastFmApiException(16, "Temporary error"))

        val s = scrobbler(h)
        s.drainQueue(session)
        s.drainQueue(session) // breaker open

        coVerify(exactly = 1) { h.apiClient.scrobble(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { h.listeningEventDao.markScrobbled(any()) }
    }
}
