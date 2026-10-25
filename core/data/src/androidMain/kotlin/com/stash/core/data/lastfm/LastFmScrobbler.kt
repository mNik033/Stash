package com.stash.core.data.lastfm

import com.stash.core.common.Log
import com.stash.core.common.primaryArtist
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.ListeningEventEntity
import com.stash.core.data.db.entity.TrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls pending listening events out of [ListeningEventDao], resolves
 * the Track metadata, and submits them to Last.fm as scrobbles. Marks
 * each event `scrobbled=true` on success; failures are retried at next
 * trigger (app start, new listen event, manual "Sync scrobbles" button).
 *
 * Runs only when the user has connected Last.fm (session key present).
 * Safe to construct-and-inject unconditionally â€” [start] is a no-op
 * until a session key appears, so users without Last.fm pay nothing.
 *
 * The current implementation drains the queue every time a new listen
 * event is recorded (via a Flow of the pending count). That's fine for
 * this scale (< 100 pending at a time typically); for heavier loads a
 * WorkManager-backed retry-with-backoff would be appropriate.
 */
@Singleton
class LastFmScrobbler @Inject constructor(
    private val apiClient: LastFmApiClient,
    private val sessionPreference: LastFmSessionPreference,
    private val listeningEventDao: ListeningEventDao,
    private val trackDao: TrackDao,
    private val credentials: LastFmCredentials,
    private val rateLimitGate: LastFmRateLimitGate,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * One drain at a time. The automatic pass (on connect and on every listen)
     * and the Settings "Sync scrobbles now" button both drain; run together, the
     * two passes loaded the same unscrobbled rows and each submitted them, and
     * Last.fm keeps duplicate scrobbles. A pass that waits here reloads the
     * queue once it runs, so it only sends what the other pass left.
     */
    private val drainLock = Mutex()

    /** Must be called once from Application.onCreate. */
    fun start() {
        if (!credentials.isConfigured) return
        scope.launch {
            combine(
                sessionPreference.session,
                listeningEventDao.pendingScrobbleCount().distinctUntilChanged(),
            ) { session, _ ->
                session
            }.collect { session ->
                if (session != null) drainQueue(session)
            }
        }
    }

    /**
     * Manually drain the pending-scrobble queue once, on demand. Used by
     * the Settings "Sync scrobbles now" button â€” after the user finishes
     * the Last.fm connect handshake, they can tap this to push the full
     * backlog of accumulated plays. When the session is null the call is
     * a cheap no-op so the UI can surface a "Connect first" message.
     *
     * A tap is a deliberate probe, so it goes out even while the automatic
     * drain is backing off: the user taps because the network is back.
     * Honouring the breaker here made the button do nothing for up to an hour
     * after one failed pass and report "No new scrobbles to send" beside a
     * card still showing plays queued.
     */
    suspend fun drainNow(): DrainResult {
        val session = runCatching { sessionPreference.session.firstOrNull() }.getOrNull()
            ?: return DrainResult(submitted = 0, sessionPresent = false)
        val before = runCatching { listeningEventDao.pendingScrobbles(limit = Int.MAX_VALUE) }
            .getOrElse { emptyList() }
            .size
        val end = drainQueue(session, manual = true)
        val after = runCatching { listeningEventDao.pendingScrobbles(limit = Int.MAX_VALUE) }
            .getOrElse { emptyList() }
            .size
        return DrainResult(
            submitted = (before - after).coerceAtLeast(0),
            sessionPresent = end != PassEnd.SESSION_REVOKED,
            held = end == PassEnd.HELD,
        )
    }

    /**
     * Fire-and-forget now-playing notification. Call this when the user
     * starts playing a track (including repeats â€” each play start is a
     * fresh now-playing ping). No retry on failure; Last.fm treats
     * now-playing as best-effort.
     */
    suspend fun notifyNowPlaying(artist: String, track: String, album: String? = null) {
        val session = runCatching { sessionPreference.session.firstOrNull() }.getOrNull()
            ?: return
        val submitArtist = scrobbleArtist(artist)
        runCatching {
            apiClient.updateNowPlaying(
                sessionKey = session.sessionKey,
                artist = submitArtist,
                track = track,
                album = album,
            )
        }.onFailure {
            Log.w(TAG, "now-playing update failed", it)
        }
    }

    /**
     * Result of a [drainNow] invocation, surfaced to the Settings UI.
     *
     * @property held true when the pass stopped on a failure (offline, Last.fm
     *   down) and left plays queued for a later retry, so the UI can say so
     *   instead of "No new scrobbles to send".
     */
    data class DrainResult(
        val submitted: Int,
        val sessionPresent: Boolean,
        val held: Boolean = false,
    )

    /** How a drain pass ended. */
    internal enum class PassEnd {
        /** Everything it loaded was handled (or there was nothing to send). */
        DRAINED,

        /** Stopped on a failure, or skipped by the backoff; plays stay queued. */
        HELD,

        /** Last.fm rejected the session key, which is now cleared. */
        SESSION_REVOKED,
    }

    /**
     * Submits up to 100 pending events per pass. Last.fm allows
     * `track.scrobble` to submit up to 50 events in one call via the
     * `artist[0]`/`track[0]`/etc. array syntax, but the simpler per-call
     * submission works fine for the low volumes we expect here.
     */
    // Internal so tests can run an automatic pass ([manual] false) directly.
    internal suspend fun drainQueue(session: LastFmSession, manual: Boolean = false): PassEnd =
        drainLock.withLock { drainQueueLocked(session, manual) }

    private suspend fun drainQueueLocked(session: LastFmSession, manual: Boolean): PassEnd {
        // Offline, every submission in this pass is doomed, and a pass runs
        // on EVERY recorded listen. Backing off after consecutive failures
        // turns a session's worth of guaranteed-to-fail requests â€” each one
        // paying DNS plus a connect timeout and holding the radio awake â€”
        // into one probe per cooldown. Rows are never dropped, only deferred.
        // A manual sync is that probe, so it skips the check (see [drainNow]).
        val now = System.currentTimeMillis()
        if (!manual && rateLimitGate.isOpen(SCROBBLE_GATE_KEY, now)) return PassEnd.HELD

        val pending = runCatching { listeningEventDao.pendingScrobbles(limit = 100) }
            .getOrElse {
                Log.w(TAG, "Failed to load pending scrobbles", it)
                return PassEnd.HELD
            }
        for (event in pending) {
            val track = runCatching { trackDao.getById(event.trackId) }.getOrNull()
            if (track == null) {
                // Track was deleted between recording and scrobbling â€” mark as
                // scrobbled so we stop retrying a dead row.
                runCatching { listeningEventDao.markScrobbled(event.id) }
                continue
            }
            when (submit(session, event, track)) {
                SubmitOutcome.HANDLED -> Unit
                SubmitOutcome.RETRY_LATER -> {
                    // Stop at the first failure rather than marching the rest of
                    // the backlog into the same wall: nothing about row 2 can
                    // succeed for a reason row 1 did not already disprove. The
                    // remaining rows stay pending for the next trigger.
                    rateLimitGate.recordRateLimited(SCROBBLE_GATE_KEY, now)
                    return PassEnd.HELD
                }
                SubmitOutcome.SESSION_REVOKED -> {
                    forgetRevokedSession(session)
                    return PassEnd.SESSION_REVOKED
                }
            }
        }
        rateLimitGate.recordSuccess(SCROBBLE_GATE_KEY)
        return PassEnd.DRAINED
    }

    /** What one [submit] means for the rest of the pass. */
    private enum class SubmitOutcome {
        /** Scrobbled, or permanently refused for this row alone: the pass goes on. */
        HANDLED,

        /** A failure the next row would hit too (offline, outage, throttling). */
        RETRY_LATER,

        /** Last.fm no longer accepts the stored session key. */
        SESSION_REVOKED,
    }

    private suspend fun submit(
        session: LastFmSession,
        event: ListeningEventEntity,
        track: TrackEntity,
    ): SubmitOutcome {
        val result = apiClient.scrobble(
            sessionKey = session.sessionKey,
            artist = scrobbleArtist(track.artist),
            track = track.title,
            album = track.album.takeIf { it.isNotBlank() },
            timestampEpochSeconds = event.startedAt / 1000,
        )
        if (result.isSuccess) {
            runCatching { listeningEventDao.markScrobbled(event.id) }
            return SubmitOutcome.HANDLED
        }
        val error = result.exceptionOrNull()
        Log.w(TAG, "Scrobble failed for event ${event.id}", error)
        return when ((error as? LastFmApiException)?.errorCode) {
            // Invalid session key: the user revoked Stash at last.fm, or a
            // password reset ended the session. Session keys never expire on
            // their own, so retrying cannot help; before, this backed off and
            // retried forever while Settings still said "Connected".
            ERROR_INVALID_SESSION -> SubmitOutcome.SESSION_REVOKED
            // Invalid parameters / resource: Last.fm refuses this play itself
            // (a blank artist or title, say). It is marked handled so it cannot
            // sit at the head of the queue, oldest first, and block every
            // later play for good.
            ERROR_INVALID_PARAMETERS, ERROR_INVALID_RESOURCE -> {
                runCatching { listeningEventDao.markScrobbled(event.id) }
                SubmitOutcome.HANDLED
            }
            // Everything else (network, 5xx, 11/16 outages, 29 throttling, and
            // app-key errors that are not the user's to fix) leaves the row
            // queued for the next trigger.
            else -> SubmitOutcome.RETRY_LATER
        }
    }

    /**
     * Disconnects Last.fm after it rejected the session key, so Settings shows
     * "Connect" instead of a queue that can never drain. The plays stay queued
     * and go out once the user reconnects. Only the session that failed is
     * cleared: a reconnect made while this pass was running is left alone.
     */
    private suspend fun forgetRevokedSession(session: LastFmSession) {
        Log.w(TAG, "Last.fm rejected the session key; disconnecting so the user can reconnect")
        try {
            if (sessionPreference.session.firstOrNull()?.sessionKey == session.sessionKey) {
                sessionPreference.clear()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not clear the revoked Last.fm session", e)
        }
    }

    /**
     * Applies the "only first artist" preference, if the user enabled it.
     */
    private suspend fun scrobbleArtist(artist: String): String =
        if (sessionPreference.firstArtistOnly.firstOrNull() == true) artist.primaryArtist() else artist

    companion object {
        private const val TAG = "LastFmScrobbler"

        /**
         * Breaker key for scrobble submission. Distinct from the read-path
         * API keys sharing [LastFmRateLimitGate], so a throttled read key
         * never blocks writes and a dead network never blocks reads.
         */
        const val SCROBBLE_GATE_KEY = "scrobble-submit"

        /** Last.fm API error codes (https://www.last.fm/api/errorcodes) the drain acts on. */
        private const val ERROR_INVALID_PARAMETERS = 6
        private const val ERROR_INVALID_RESOURCE = 7
        private const val ERROR_INVALID_SESSION = 9
    }
}
