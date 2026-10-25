package com.stash.core.data.blocklist

import com.stash.core.data.db.entity.TrackBlocklistEntity
import com.stash.core.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * v0.9.15: Where a block originated. Used for telemetry attribution and
 * the Settings UI's "blocked from N" label.
 */
enum class BlockSource {
    NOW_PLAYING, CONTEXT_MENU, PLAYLIST_DELETE,
    MIGRATION_V19, INTEGRITY_WORKER, FAILED_DOWNLOADS, OTHER
}

/**
 * v0.9.15: Single chokepoint for blocklist enforcement. Every track-source
 * flow (sync, mix, discovery, search, download, swap, failed-match) consults
 * this guard via [isBlocked] before inserting / queueing / linking.
 */
interface BlocklistGuard {
    /** Identity check by raw fields. Used by sync workers reading from snapshots. */
    suspend fun isBlocked(
        artist: String,
        title: String,
        spotifyUri: String?,
        youtubeId: String?,
    ): Boolean

    /** Identity check by trackId (looks up the row, then forwards). */
    suspend fun isBlockedByTrackId(trackId: Long): Boolean

    /**
     * Whether a row stored under these canonical words would have a blocked key.
     */
    suspend fun isKeyBlocked(canonicalTitle: String, canonicalArtist: String): Boolean

    /**
     * Atomic block. Inserts a blocklist entry, deletes references and files.
     */
    suspend fun block(track: TrackEntity, source: BlockSource)

    /**
     * Remove the block.
     */
    suspend fun unblock(canonicalKey: String)

    /** UI feed for the Blocked Songs viewer. */
    fun observeBlocklist(): Flow<List<TrackBlocklistEntity>>

    /** Reactive count for the Sync-tab "Blocked Songs" badge. */
    fun observeCount(): Flow<Int>
}
