package com.stash.core.media.preview

import com.stash.core.common.Log
import com.stash.core.model.TrackItem
import com.stash.data.download.lossless.LosslessAvailability
import com.stash.data.download.lossless.LosslessSourcePreferences
import com.stash.data.download.lossless.LosslessSourceRegistry
import com.stash.data.download.lossless.SourceResult
import com.stash.data.download.lossless.TrackQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lossless-source matches for the search preview, keyed by videoId so
 * [SearchPreviewMediaSource] can reuse a result instead of resolving twice.
 *
 * The visible-row prefetch ([warmUp], called from
 * `LaunchedEffect(track.videoId)` in search/artist-profile rows) is currently
 * a no-op — see its KDoc. 5-min TTL on cached results — Qobuz signed URLs
 * expire in minutes, and a stale entry on tap would 403 mid-stream.
 *
 * Memory bounded by TTL: stale entries get evicted via [cancelStale] on insert.
 */
@Singleton
class AndroidLosslessUrlPrefetcher internal constructor(
    private val registry: LosslessSourceRegistry,
    /** Unused while [warmUp] is a no-op; it gated the warm-up to the user's own account. */
    @Suppress("unused") private val availability: LosslessAvailability,
    private val losslessPrefs: LosslessSourcePreferences,
    /** Where warm-ups and lookups run; tests pass Unconfined or a test dispatcher. */
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
) : LosslessUrlPrefetcher {
    @Inject
    constructor(
        registry: LosslessSourceRegistry,
        availability: LosslessAvailability,
        losslessPrefs: LosslessSourcePreferences,
    ) : this(registry, availability, losslessPrefs, Dispatchers.IO)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val cache = ConcurrentHashMap<String, CachedDeferred>()

    @Suppress("UNUSED_PARAMETER")
    override fun warmUp(track: TrackItem) = Unit

    suspend fun lookup(track: TrackItem): SourceResult? {
        if (!losslessPrefs.enabledNow()) return null
        val key = track.videoId
        cache[key]?.let { if (it.isFresh() && it.deferred.isCompleted) return it.deferred.await() }
        cache[key]?.deferred?.cancel()
        cancelStale()
        val fast = scope.async {
            runCatching { registry.resolve(track.toQuery(), bypassRateLimit = true) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "foreground resolve failed for $key: ${e.message}")
                }
                .getOrNull()
        }
        cache[key] = CachedDeferred(deferred = fast, createdAt = System.currentTimeMillis())
        return fast.await()
    }

    fun cancelStale() {
        val now = System.currentTimeMillis()
        val expired = cache.entries.filter { (_, c) -> now - c.createdAt > TTL_MS }
        expired.forEach { (k, _) -> cache.remove(k) }
        if (expired.isNotEmpty()) Log.d(TAG, "cancelStale: removed ${expired.size} stale entries")
    }

    private fun TrackItem.toQuery() = TrackQuery(
        artist = artist,
        title = title,
        album = album?.takeIf { it.isNotBlank() },
        isrc = null,
        durationMs = durationSeconds.takeIf { it > 0 }?.let { (it * 1_000).toLong() },
    )

    private data class CachedDeferred(
        val deferred: Deferred<SourceResult?>,
        val createdAt: Long,
    ) {
        fun isFresh() = System.currentTimeMillis() - createdAt < TTL_MS
    }

    companion object {
        private const val TAG = "LosslessUrlPrefetcher"

        /** Cache TTL in milliseconds. Qobuz signed URLs expire in ~5 minutes. */
        const val TTL_MS = 5 * 60 * 1_000L
    }
}

/** Factory functions preserving compatibility for tests. */
fun LosslessUrlPrefetcher(
    registry: LosslessSourceRegistry,
    availability: LosslessAvailability,
    losslessPrefs: LosslessSourcePreferences,
): AndroidLosslessUrlPrefetcher = AndroidLosslessUrlPrefetcher(registry, availability, losslessPrefs)

fun LosslessUrlPrefetcher(
    registry: LosslessSourceRegistry,
    availability: LosslessAvailability,
    losslessPrefs: LosslessSourcePreferences,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
): AndroidLosslessUrlPrefetcher = AndroidLosslessUrlPrefetcher(registry, availability, losslessPrefs, dispatcher)
