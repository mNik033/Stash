package com.stash.core.data.blocklist

import com.stash.core.data.db.dao.TrackBlocklistDao
import com.stash.core.data.db.entity.TrackBlocklistEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.sync.TrackMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopBlocklistGuard @Inject constructor(
    private val blocklistDao: TrackBlocklistDao,
    private val matcher: TrackMatcher,
) : BlocklistGuard {
    override suspend fun isBlocked(
        artist: String,
        title: String,
        spotifyUri: String?,
        youtubeId: String?,
    ): Boolean {
        val key = BlocklistKey.of(artist, title, matcher)
        return blocklistDao.isBlocked(key, spotifyUri, youtubeId)
    }

    override suspend fun isBlockedByTrackId(trackId: Long): Boolean = false

    override suspend fun isKeyBlocked(canonicalTitle: String, canonicalArtist: String): Boolean =
        blocklistDao.findByKey(BlocklistKey.fromStoredCanonicals(canonicalArtist, canonicalTitle)) != null

    override suspend fun block(track: TrackEntity, source: BlockSource) {}

    override suspend fun unblock(canonicalKey: String) {
        blocklistDao.deleteByKey(canonicalKey)
    }

    override fun observeBlocklist(): Flow<List<TrackBlocklistEntity>> = blocklistDao.observeAll()

    override fun observeCount(): Flow<Int> = blocklistDao.observeCount()
}
