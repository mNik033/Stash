package com.stash.core.data.youtube

import com.stash.core.auth.TokenManager
import com.stash.core.auth.model.AuthService
import com.stash.core.auth.youtube.YouTubeCookieHelper
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.ListeningEventEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.prefs.YouTubeHistoryPreference
import com.stash.data.ytmusic.CanonicalLookup
import com.stash.data.ytmusic.CanonicalMatch
import com.stash.data.ytmusic.InnerTubeClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [YouTubeHistoryScrobbler] kill-switch transitions.
 *
 * The HTTP path is exercised on-device (Task 19). Here we only test the
 * branching logic driven by [PingSubmitter] status codes and [YouTubeScrobblerState].
 */
class YouTubeHistoryScrobblerTest {

    // ---- mocks ----
    private val preference = mock<YouTubeHistoryPreference>()
    private val state = mock<YouTubeScrobblerState>()
    private val listeningEventDao = mock<ListeningEventDao>()
    private val trackDao = mock<TrackDao>()
    private val resolver = mock<YtCanonicalResolver>()
    private val innerTubeClient = mock<InnerTubeClient>()
    private val cookieHelper = mock<YouTubeCookieHelper>()
    private val tokenManager = mock<TokenManager>()

    // Controllable PingSubmitter: tests set this to return their desired HTTP code.
    private var pingStatusCode = 200
    private var pings = 0
    private val fakePingSubmitter: PingSubmitter = PingSubmitter { _, _, _ -> pings++; pingStatusCode }

    // Version code provider — default: same version (no update)
    private var fakeVersionCode = 1
    private var fakeStoredVersionCode = 1

    private lateinit var scrobbler: YouTubeHistoryScrobbler

    /** Event id → attempts charged: what [YouTubeScrobblerState.lookupFailures] holds. */
    private val recorded = mutableMapOf<Long, Int>()

    private fun event(id: Long = 1L) = ListeningEventEntity(
        id = id, trackId = 10L, startedAt = 1000L,
    )

    private fun track() = TrackEntity(
        id = 10L, title = "Song", artist = "Artist",
        youtubeId = "vid123", musicVideoType = "MUSIC_VIDEO_TYPE_ATV",
    )

    @Before
    fun setUp() = runTest {
        whenever(state.currentLastKnownVersionCode()).thenReturn(fakeStoredVersionCode)
        whenever(state.currentDisabledReason()).thenReturn(null)
        whenever(state.incrementConsecutiveFailures()).thenReturn(1)
        // The lookup-failure record, kept in [recorded] as the DataStore would.
        whenever(state.lookupFailures()).thenAnswer { recorded.toMap() }
        whenever(state.setLookupFailure(any(), any())).thenAnswer { recorded[it.getArgument(0)] = it.getArgument(1) }
        whenever(state.clearLookupFailures(any())).thenAnswer {
            recorded.keys.removeAll(it.getArgument<Collection<Long>>(0).toSet())
            Unit
        }
        whenever(tokenManager.getYouTubeCookie()).thenReturn("SAPISID=abc; LOGIN_INFO=xyz")
        whenever(cookieHelper.extractSapiSid(any())).thenReturn("abc")
        whenever(cookieHelper.generateAuthHeader(any())).thenReturn("SAPISIDHASH 1234_hash")
        whenever(resolver.resolve(any()))
            .thenReturn(CanonicalLookup.Found(CanonicalMatch("vid123", thumbnailUrl = null)))
        whenever(innerTubeClient.getPlaybackTracking(any()))
            .thenReturn("https://tracking.youtube.com/ptracking")

        scrobbler = YouTubeHistoryScrobbler(
            preference = preference,
            state = state,
            listeningEventDao = listeningEventDao,
            trackDao = trackDao,
            resolver = resolver,
            innerTubeClient = innerTubeClient,
            cookieHelper = cookieHelper,
            tokenManager = tokenManager,
            pingSubmitter = fakePingSubmitter,
            versionCodeProvider = { fakeVersionCode },
        )
    }

    // ── Test 1: health starts DISABLED (initial value) ───────────────────────
    // The scrobbler initialises to DISABLED; it transitions only after the
    // combine flow fires. We verify the initial contract here — the reactive
    // transitions are covered by the kill-switch tests below.

    @Test
    fun `health is DISABLED by default before start`() {
        // Initial value is DISABLED — no start() needed.
        assertEquals(YouTubeScrobblerHealth.DISABLED, scrobbler.health.value)
    }

    // ── Test 2: PROTOCOL_BROKEN when disabledReason is set (via kill-switch) ─
    // The kill-switch path is exercised by onProtocolFailure reaching threshold.
    // This test drives it directly via the 5th-failure scenario (covered in
    // Test 5 block). Here we additionally verify via onProtocolFailure side-effect.

    @Test
    fun `health becomes PROTOCOL_BROKEN after kill-switch trips via onProtocolFailure`() = runTest {
        pingStatusCode = 500
        whenever(state.incrementConsecutiveFailures()).thenReturn(5)

        scrobbler.submitForTest(event(), track())

        assertEquals(YouTubeScrobblerHealth.PROTOCOL_BROKEN, scrobbler.health.value)
    }

    // ── Test 3: OK after successful ping resets counter ───────────────────────

    @Test
    fun `onSuccess resets counter and sets health OK`() = runTest {
        pingStatusCode = 200

        scrobbler.submitForTest(event(), track())

        verify(listeningEventDao).markYtScrobbled(1L)
        verify(state).resetConsecutiveFailures()
        assertEquals(YouTubeScrobblerHealth.OK, scrobbler.health.value)
    }

    // ── Test 4: AUTH_FAILED on 401/403, counter NOT incremented ──────────────

    @Test
    fun `401 sets AUTH_FAILED and does not increment failure counter`() = runTest {
        pingStatusCode = 401

        scrobbler.submitForTest(event(), track())

        verify(state, never()).incrementConsecutiveFailures()
        assertEquals(YouTubeScrobblerHealth.AUTH_FAILED, scrobbler.health.value)
    }

    @Test
    fun `403 sets AUTH_FAILED and does not increment failure counter`() = runTest {
        pingStatusCode = 403

        scrobbler.submitForTest(event(), track())

        verify(state, never()).incrementConsecutiveFailures()
        assertEquals(YouTubeScrobblerHealth.AUTH_FAILED, scrobbler.health.value)
    }

    // ── Test 5: Protocol failure increments counter; 5th trips kill-switch ───

    @Test
    fun `protocol failure increments counter and sets OFFLINE before threshold`() = runTest {
        pingStatusCode = 500
        whenever(state.incrementConsecutiveFailures()).thenReturn(3)

        scrobbler.submitForTest(event(), track())

        verify(state).incrementConsecutiveFailures()
        assertEquals(YouTubeScrobblerHealth.OFFLINE, scrobbler.health.value)
    }

    @Test
    fun `5th consecutive protocol failure trips kill-switch to PROTOCOL_BROKEN`() = runTest {
        pingStatusCode = 500
        whenever(state.incrementConsecutiveFailures()).thenReturn(5)

        scrobbler.submitForTest(event(), track())

        verify(state).setDisabledReason("protocol_errors")
        assertEquals(YouTubeScrobblerHealth.PROTOCOL_BROKEN, scrobbler.health.value)
    }

    // ── Test 6: start() clears kill-switch on version bump ───────────────────

    @Test
    fun `start() clears kill-switch when versionCode is newer than stored`() = runTest {
        fakeVersionCode = 10
        // Re-create scrobbler so versionCodeProvider picks up the new value,
        // and state reports the old stored version.
        whenever(state.currentLastKnownVersionCode()).thenReturn(5)

        val freshScrobbler = YouTubeHistoryScrobbler(
            preference = preference,
            state = state,
            listeningEventDao = listeningEventDao,
            trackDao = trackDao,
            resolver = resolver,
            innerTubeClient = innerTubeClient,
            cookieHelper = cookieHelper,
            tokenManager = tokenManager,
            pingSubmitter = fakePingSubmitter,
            versionCodeProvider = { fakeVersionCode },
        )

        freshScrobbler.maybeClearKillSwitchOnUpdateForTest()

        verify(state).setDisabledReason(null)
        verify(state).resetConsecutiveFailures()
        verify(state).setLastKnownVersionCode(10)
    }

    @Test
    fun `start() does NOT clear kill-switch when versionCode is unchanged`() = runTest {
        fakeVersionCode = 5
        whenever(state.currentLastKnownVersionCode()).thenReturn(5)

        val freshScrobbler = YouTubeHistoryScrobbler(
            preference = preference,
            state = state,
            listeningEventDao = listeningEventDao,
            trackDao = trackDao,
            resolver = resolver,
            innerTubeClient = innerTubeClient,
            cookieHelper = cookieHelper,
            tokenManager = tokenManager,
            pingSubmitter = fakePingSubmitter,
            versionCodeProvider = { fakeVersionCode },
        )

        freshScrobbler.maybeClearKillSwitchOnUpdateForTest()

        verify(state, never()).setDisabledReason(any())
        verify(state, never()).resetConsecutiveFailures()
    }

    // ── Drain pass: the opt-in cutoff, failures that end it, opt-out mid-pass ─

    private suspend fun stubPending(vararg events: ListeningEventEntity) {
        whenever(preference.sendPlaysSinceMs(any())).thenReturn(CUTOFF)
        whenever(listeningEventDao.pendingYtScrobbles(CUTOFF, 50)).thenReturn(events.toList())
        whenever(trackDao.getById(10L)).thenReturn(track())
    }

    @Test
    fun `drain asks only for plays since the opt-in instant`() = runTest {
        stubPending(event(1L))

        scrobbler.drainQueue()

        // Every play ever recorded has yt_scrobbled = 0; without the cutoff,
        // turning the feature on sent the user's whole history.
        verify(listeningEventDao).pendingYtScrobbles(sinceMs = CUTOFF, limit = 50)
        verify(listeningEventDao).markYtScrobbled(1L)
    }

    @Test
    fun `drain sends nothing when there is no cutoff`() = runTest {
        whenever(preference.sendPlaysSinceMs(any())).thenReturn(null)

        scrobbler.drainQueue()

        verify(listeningEventDao, never()).pendingYtScrobbles(any(), any())
        assertEquals(0, pings)
    }

    @Test
    fun `a failed search keeps the play queued and ends the pass`() = runTest {
        stubPending(event(1L), event(2L), event(3L))
        whenever(resolver.resolve(any())).thenReturn(CanonicalLookup.Failed)

        scrobbler.drainQueue()

        verify(listeningEventDao, never()).markYtScrobbled(any())
        // The second play's search tells offline from a failure of the first
        // play alone: both unanswered, so the pass ends there.
        verify(resolver, times(2)).resolve(any())
        assertEquals(YouTubeScrobblerHealth.OFFLINE, scrobbler.health.value)
        assertEquals("moved back, nothing charged while offline", mapOf(1L to 0, 2L to 0), recorded)
    }

    @Test
    fun `no canonical match drops only that play and the pass goes on`() = runTest {
        stubPending(event(1L), event(2L))
        whenever(resolver.resolve(any())).thenReturn(CanonicalLookup.NoMatch)

        scrobbler.drainQueue()

        verify(listeningEventDao).markYtScrobbled(1L)
        verify(listeningEventDao).markYtScrobbled(2L)
        assertEquals(0, pings)
    }

    @Test
    fun `an auth failure ends the pass after one ping`() = runTest {
        stubPending(event(1L), event(2L), event(3L))
        pingStatusCode = 401

        scrobbler.drainQueue()

        assertEquals(1, pings)
        assertEquals(YouTubeScrobblerHealth.AUTH_FAILED, scrobbler.health.value)
    }

    @Test
    fun `the kill-switch tripping mid-pass ends it`() = runTest {
        stubPending(event(1L), event(2L), event(3L))
        pingStatusCode = 500
        whenever(state.incrementConsecutiveFailures()).thenReturn(5)

        scrobbler.drainQueue()

        assertEquals(1, pings)
        verify(state).setDisabledReason("protocol_errors")
    }

    @Test
    fun `an already-tripped kill-switch sends nothing`() = runTest {
        stubPending(event(1L), event(2L))
        whenever(state.currentDisabledReason()).thenReturn("protocol_errors")

        scrobbler.drainQueue()

        assertEquals(0, pings)
    }

    @Test
    fun `turning the feature off mid-pass stops the next ping`() = runTest {
        stubPending(event(1L), event(2L), event(3L))
        // Read once for the batch, once before event 1, then the user turns it off.
        whenever(preference.sendPlaysSinceMs(any())).thenReturn(CUTOFF, CUTOFF, null)

        scrobbler.drainQueue()

        assertEquals(1, pings)
        verify(listeningEventDao).markYtScrobbled(1L)
        verify(listeningEventDao, never()).markYtScrobbled(2L)
    }

    @Test
    fun `turning it off and on again mid-pass skips plays from before the new opt-in`() = runTest {
        stubPending(event(1L), event(2L))
        whenever(preference.sendPlaysSinceMs(any())).thenReturn(CUTOFF, CUTOFF, 2_000L)

        scrobbler.drainQueue()

        assertEquals(1, pings)
        verify(listeningEventDao, never()).markYtScrobbled(2L)
    }

    // ── A play whose lookup keeps failing can't block the queue ─────────────

    /** Plays of tracks of their own (track id = 10 × event id), queued oldest first. */
    private suspend fun stubQueue(vararg ids: Long) {
        whenever(preference.sendPlaysSinceMs(any())).thenReturn(CUTOFF)
        whenever(listeningEventDao.pendingYtScrobbles(eq(CUTOFF), any()))
            .thenReturn(ids.map { ListeningEventEntity(id = it, trackId = it * 10, startedAt = 1000L + it) })
        ids.forEach { id ->
            whenever(trackDao.getById(id * 10)).thenReturn(track().copy(id = id * 10, title = "Song $id"))
        }
    }

    /** The lookup of event [id]'s track answers [lookup]; every other one finds its song. */
    private suspend fun lookupOf(id: Long, lookup: () -> CanonicalLookup) {
        whenever(resolver.resolve(any())).thenAnswer {
            if (it.getArgument<TrackEntity>(0).id == id * 10) lookup()
            else CanonicalLookup.Found(CanonicalMatch("vid123", thumbnailUrl = null))
        }
    }

    @Test
    fun `a play whose search fails alone no longer holds back the plays behind it`() = runTest {
        stubQueue(1L, 2L, 3L)
        lookupOf(1L) { CanonicalLookup.Failed }

        scrobbler.drainQueue()

        verify(listeningEventDao).markYtScrobbled(2L)
        verify(listeningEventDao).markYtScrobbled(3L)
        verify(listeningEventDao, never()).markYtScrobbled(1L)
        // The next play's search answered, so the failure was the first play's own.
        assertEquals(mapOf(1L to 1), recorded)
        assertEquals(YouTubeScrobblerHealth.OK, scrobbler.health.value)
    }

    @Test
    fun `a lookup that throws is the play's own failure, not offline`() = runTest {
        stubQueue(1L, 2L)
        lookupOf(1L) { throw IllegalArgumentException("Element class JsonPrimitive is not a JsonObject") }

        scrobbler.drainQueue()

        verify(listeningEventDao).markYtScrobbled(2L)
        assertEquals(mapOf(1L to 1), recorded)
        assertEquals(YouTubeScrobblerHealth.OK, scrobbler.health.value)
    }

    @Test
    fun `a play whose lookup failed before goes after the others`() = runTest {
        recorded[1L] = 1
        stubQueue(1L, 2L, 3L)

        scrobbler.drainQueue()

        // Room for the recorded play on top of a full batch.
        verify(listeningEventDao).pendingYtScrobbles(sinceMs = CUTOFF, limit = 51)
        val order = inOrder(listeningEventDao)
        order.verify(listeningEventDao).markYtScrobbled(2L)
        order.verify(listeningEventDao).markYtScrobbled(3L)
        order.verify(listeningEventDao).markYtScrobbled(1L)
        assertEquals("sent, so forgotten", emptyMap<Long, Int>(), recorded)
    }

    @Test
    fun `a play whose lookup keeps failing is dropped after its last attempt`() = runTest {
        recorded[1L] = YouTubeHistoryScrobbler.MAX_LOOKUP_ATTEMPTS - 1
        stubQueue(1L, 2L)
        lookupOf(1L) { CanonicalLookup.Failed }

        scrobbler.drainQueue()

        verify(listeningEventDao).markYtScrobbled(2L)
        verify(listeningEventDao).markYtScrobbled(1L)
        assertEquals(emptyMap<Long, Int>(), recorded)
    }

    @Test
    fun `time offline charges no attempts`() = runTest {
        recorded[1L] = YouTubeHistoryScrobbler.MAX_LOOKUP_ATTEMPTS - 1
        stubQueue(1L)
        lookupOf(1L) { CanonicalLookup.Failed }

        scrobbler.drainQueue()

        verify(listeningEventDao, never()).markYtScrobbled(any())
        assertEquals(mapOf(1L to YouTubeHistoryScrobbler.MAX_LOOKUP_ATTEMPTS - 1), recorded)
        assertEquals(YouTubeScrobblerHealth.OFFLINE, scrobbler.health.value)
    }

    @Test
    fun `a connection lost mid-pass charges a few plays once and ends the pass`() = runTest {
        stubQueue(1L, 2L, 3L, 4L, 5L, 6L)
        var searches = 0
        whenever(resolver.resolve(any())).thenAnswer {
            if (++searches == 1) CanonicalLookup.Found(CanonicalMatch("vid123", thumbnailUrl = null))
            else CanonicalLookup.Failed
        }

        scrobbler.drainQueue()

        assertEquals(4, searches)
        assertEquals(mapOf(2L to 1, 3L to 1, 4L to 1), recorded)
    }

    @Test
    fun `a recorded play no longer queued is forgotten`() = runTest {
        recorded[9L] = 2
        stubQueue(1L)

        scrobbler.drainQueue()

        assertEquals(emptyMap<Long, Int>(), recorded)
    }

    @Test
    fun `the lookup-failure record keeps the newest plays and skips junk`() {
        val max = YouTubeScrobblerState.MAX_LOOKUP_FAILURES.toLong()
        val entries = (1L..max + 1).associateWith { 1 }

        val kept = YouTubeScrobblerState.decodeLookupFailures(YouTubeScrobblerState.encodeLookupFailures(entries))

        assertEquals(YouTubeScrobblerState.MAX_LOOKUP_FAILURES, kept.size)
        assertFalse("the oldest play goes", 1L in kept)
        assertEquals(mapOf(7L to 2), YouTubeScrobblerState.decodeLookupFailures(setOf("7:2", "x", "8:", ":1")))
    }

    private companion object {
        /** Before [event]'s startedAt (1000), so its plays are eligible. */
        const val CUTOFF = 500L
    }
}
