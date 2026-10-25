package com.stash.core.data.blocklist

import com.stash.core.common.Log
import androidx.room.withTransaction
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.PlaylistDao
import com.stash.core.data.db.dao.TrackBlocklistDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.fileUsedByAnotherTrack
import com.stash.core.data.db.entity.TrackBlocklistEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.library.CanonicalRecomputeState
import com.stash.core.data.sync.TrackMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [BlocklistGuard].
 */
@Singleton
class AndroidBlocklistGuard @Inject constructor(
    private val database: StashDatabase,
    private val blocklistDao: TrackBlocklistDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val fileDeleter: FileDeleter,
    private val matcher: TrackMatcher,
    private val canonicalRecompute: CanonicalRecomputeState,
) : BlocklistGuard {

    /** Identity check by raw fields. Used by sync workers reading from snapshots. */
    override suspend fun isBlocked(
        artist: String,
        title: String,
        spotifyUri: String?,
        youtubeId: String?,
    ): Boolean {
        val key = BlocklistKey.of(artist, title, matcher)
        return blocklistDao.isBlocked(key, spotifyUri, youtubeId)
    }

    /** Identity check by trackId (looks up the row, then forwards). */
    override suspend fun isBlockedByTrackId(trackId: Long): Boolean {
        val track = trackDao.getById(trackId) ?: return false
        return isBlocked(track.artist, track.title, track.spotifyUri, track.youtubeId)
    }

    /**
     * Whether a row stored under these canonical words would have a blocked key.
     */
    override suspend fun isKeyBlocked(canonicalTitle: String, canonicalArtist: String): Boolean =
        blocklistDao.findByKey(BlocklistKey.fromStoredCanonicals(canonicalArtist, canonicalTitle)) != null

    /**
     * Atomic block. Inserts a blocklist entry, deletes references and files.
     */
    override suspend fun block(track: TrackEntity, source: BlockSource) {
        val key = BlocklistKey.of(track.artist, track.title, matcher)
        val entry = TrackBlocklistEntity(
            canonicalKey = key,
            artist = track.artist,
            title = track.title,
            spotifyUri = track.spotifyUri,
            youtubeId = track.youtubeId,
            blockedAt = System.currentTimeMillis(),
            blockedFrom = source.name,
        )
        // Not a file another song still plays from (two rows can share one).
        track.filePath?.takeUnless { trackDao.fileUsedByAnotherTrack(it, track.id) }?.let(fileDeleter::delete)
        fileDeleter.delete(track.albumArtPath)

        val playlistIds = database.withTransaction {
            val heldBy = playlistDao.playlistIdsForTrack(track.id)
            blocklistDao.insert(entry)
            playlistDao.deleteAllCrossRefsForTrack(track.id)
            downloadQueueDao.deleteByTrackId(track.id)
            trackDao.deleteById(track.id)
            playlistDao.forgetSpotifyMirrorStamps()
            heldBy
        }
        playlistIds.forEach { id ->
            playlistDao.updateTrackCount(id, trackDao.getByPlaylist(id, includeStreamable = true).first().size)
        }
        Log.d(TAG, "Blocked '${track.artist} - ${track.title}' (id=${track.id}, source=$source)")
    }

    /**
     * Remove the block.
     */
    override suspend fun unblock(canonicalKey: String) {
        database.withTransaction {
            blocklistDao.deleteByKey(canonicalKey)
            playlistDao.forgetSpotifyMirrorStamps()
        }
        canonicalRecompute.rearm()
    }

    /** UI feed for the Blocked Songs viewer. */
    override fun observeBlocklist(): Flow<List<TrackBlocklistEntity>> = blocklistDao.observeAll()

    /** Reactive count for the Sync-tab "Blocked Songs" badge. */
    override fun observeCount(): Flow<Int> = blocklistDao.observeCount()

    private companion object {
        const val TAG = "BlocklistGuard"
    }
}

fun BlocklistGuard(
    database: StashDatabase,
    blocklistDao: TrackBlocklistDao,
    trackDao: TrackDao,
    playlistDao: PlaylistDao,
    downloadQueueDao: DownloadQueueDao,
    fileDeleter: FileDeleter,
    matcher: TrackMatcher,
    canonicalRecompute: CanonicalRecomputeState,
): BlocklistGuard = AndroidBlocklistGuard(
    database, blocklistDao, trackDao, playlistDao, downloadQueueDao, fileDeleter, matcher, canonicalRecompute
)
