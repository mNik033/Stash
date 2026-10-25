package com.stash.core.media

import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.TrackEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the playback queue to hand back to Media3's
 * [androidx.media3.session.MediaLibraryService.MediaLibrarySession.Callback.onPlaybackResumption]
 * contract when a controller (Bluetooth / Android Auto) asks to resume
 * playback after the process was killed.
 *
 * The decision logic lives here, free of Android `MediaItem` types, so it
 * can be unit-tested on the JVM — the host service is responsible only for
 * turning a [ResumePlan] into `MediaItem`s. This mirrors the
 * [com.stash.core.media.streaming.PrefetchOrchestrator] extraction pattern.
 *
 * Returning `null` means "no restorable queue was persisted" and the caller
 * should fall back to its single-track behaviour (last-played / most
 * recently added).
 */
@Singleton
class PlaybackResumer @Inject constructor(
    private val playbackStateStore: PlaybackStateStore,
    private val trackDao: TrackDao,
) {

    /**
     * The fully-resolved queue to resume into.
     *
     * @property tracks      Ordered queue tracks, ready to be mapped to `MediaItem`s.
     * @property startIndex  Index within [tracks] of the track to start on.
     * @property positionMs  Position within the start track to resume from (>= 0).
     * @property isShuffled  Whether shuffle mode was active when last saved.
     */
    data class ResumePlan(
        val tracks: List<TrackEntity>,
        val startIndex: Int,
        val positionMs: Long,
        val isShuffled: Boolean,
        val repeatMode: com.stash.core.model.RepeatMode,
        val source: com.stash.core.model.PlaybackSource,
    )

    suspend fun buildResumePlan(): ResumePlan? {
        val saved = playbackStateStore.getLastPlaybackState() ?: return null
        if (saved.queueTrackIds.isEmpty()) return null

        // `IN` doesn't preserve order, so re-sort into the saved queue order.
        // Tracks that no longer exist in the DB are silently dropped.
        val byId = trackDao.getByIds(saved.queueTrackIds).associateBy { it.id }
        val ordered = saved.queueTrackIds.mapNotNull { byId[it] }
        if (ordered.isEmpty()) return null

        // Prefer locating the saved current track by id (robust to dropped
        // rows shifting positions); fall back to the saved index clamped
        // into range if that track is gone. The saved index wins when it
        // still holds that track: with the song queued twice, the first
        // match resumed at the EARLIER copy and replayed everything between.
        val startIndex = saved.queueIndex.takeIf { ordered.getOrNull(it)?.id == saved.trackId }
            ?: ordered.indexOfFirst { it.id == saved.trackId }
                .let { if (it >= 0) it else saved.queueIndex.coerceIn(0, ordered.size - 1) }

        return ResumePlan(
            tracks = ordered,
            startIndex = startIndex,
            // The saved position belongs to the saved track. When that track is
            // gone the start is whatever now sits at its index (under shuffle a
            // walk index, so possibly anything): starting THAT song 3:40 in, or
            // auto-advancing past it when it is shorter, was the old result.
            positionMs = if (ordered[startIndex].id == saved.trackId) saved.positionMs.coerceAtLeast(0) else 0L,
            isShuffled = saved.isShuffled,
            repeatMode = saved.repeatMode,
            source = saved.source,
        )
    }
}
