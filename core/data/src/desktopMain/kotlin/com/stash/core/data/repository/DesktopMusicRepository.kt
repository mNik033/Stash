package com.stash.core.data.repository

import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.db.dao.AlbumSummary
import com.stash.core.data.db.dao.ArtistSummary
import com.stash.core.data.db.dao.DiscoveryQueueDao
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.PlaylistDao
import com.stash.core.data.db.dao.SharedMixDao
import com.stash.core.data.db.dao.StashMixRecipeDao
import com.stash.core.data.db.dao.SyncHistoryDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.TrackLikeState
import com.stash.core.data.db.dao.UnmatchedTrackView
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.data.db.entity.PlaylistTrackCrossRef
import com.stash.core.data.db.entity.SyncHistoryEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.library.canonicalWordsForSave
import com.stash.core.data.mapper.toDomain
import com.stash.core.data.mapper.toEntity
import com.stash.core.data.prefs.LibraryScopePreference
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.model.DownloadStatus
import com.stash.core.model.DownloadWaitReason
import com.stash.core.model.MusicSource
import com.stash.core.model.Playlist
import com.stash.core.model.PlaylistType
import com.stash.core.model.Track
import com.stash.core.model.share.SharedTrack
import com.stash.core.model.share.toTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopMusicRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val syncHistoryDao: SyncHistoryDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val discoveryQueueDao: DiscoveryQueueDao,
    private val blocklistGuard: BlocklistGuard,
    private val streamingPreference: StreamingPreference,
    private val libraryScopePreference: LibraryScopePreference,
    private val sharedMixDao: SharedMixDao,
    private val stashMixRecipeDao: StashMixRecipeDao,
) : MusicRepository {

    private val _trackDeletions = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 64)
    override val trackDeletions: SharedFlow<Long> = _trackDeletions.asSharedFlow()

    override fun getAllTracks(): Flow<List<Track>> =
        trackDao.getLibraryByDateAdded()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun getLibraryTracksIncludingOnline(): Flow<List<Track>> =
        trackDao.getLibraryWithOnlineByDateAdded()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun getTracksByArtist(artist: String): Flow<List<Track>> =
        trackDao.getByArtist(artist, includeNotDownloaded = true, libraryOnly = false)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun getTracksByAlbum(album: String, artist: String): Flow<List<Track>> =
        trackDao.getByAlbum(album, includeNotDownloaded = true, libraryOnly = false)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun getTracksByPlaylist(playlistId: Long): Flow<List<Track>> =
        trackDao.getByPlaylist(playlistId, includeStreamable = true)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun getAllArtists(): Flow<List<ArtistSummary>> =
        trackDao.getAllArtists(includeNotDownloaded = true, libraryOnly = false)
            .flowOn(Dispatchers.Default)

    override fun getAllAlbums(): Flow<List<AlbumSummary>> =
        trackDao.getAllAlbums(includeNotDownloaded = true, libraryOnly = false)
            .flowOn(Dispatchers.Default)

    override fun getRecentlyAdded(limit: Int): Flow<List<Track>> =
        trackDao.getRecentlyAdded(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun getMostPlayed(limit: Int): Flow<List<Track>> =
        trackDao.getMostPlayed(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun search(query: String): Flow<List<Track>> {
        val sanitized = query.trim()
        if (sanitized.isEmpty()) return flowOf(emptyList())
        return trackDao.search(sanitized, includeStreamable = true)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)
    }

    override suspend fun findByYoutubeIds(videoIds: Collection<String>): List<Track> =
        videoIds.mapNotNull { trackDao.findByYoutubeId(it)?.toDomain() }

    override suspend fun backfillAlbumForTracks(
        videoIds: Collection<String>,
        album: String,
        albumArtist: String,
    ) {
        videoIds.forEach { videoId ->
            val existing = trackDao.findByYoutubeId(videoId) ?: return@forEach
            if (existing.album.isEmpty() && album.isNotEmpty()) {
                trackDao.updateAlbumIfEmpty(existing.id, album)
            }
            if (existing.albumArtist.isEmpty() && albumArtist.isNotEmpty()) {
                trackDao.updateAlbumArtistIfEmpty(existing.id, albumArtist)
            }
        }
    }

    override suspend fun applyStashMixesEnabled(enabled: Boolean) {}

    override suspend fun getAllDownloadedTracks(): List<Track> =
        trackDao.getAllDownloaded().map { it.toDomain() }

    override fun getTrackCount(): Flow<Int> = trackDao.getTotalCount(includeStreamable = true)
    override fun getTotalStorageBytes(): Flow<Long> = trackDao.getTotalStorageBytes()
    override fun getFlacTrackCount(): Flow<Int> = trackDao.getFlacCount()
    override fun getFlacStorageBytes(): Flow<Long> = trackDao.getFlacStorageBytes()
    override fun getSpotifyDownloadedCount(): Flow<Int> = trackDao.getSpotifyDownloadedCount()
    override fun getYouTubeDownloadedCount(): Flow<Int> = trackDao.getYouTubeDownloadedCount()

    override fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllVisible(includeStreamable = true)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override suspend fun getPlaylistWithTracks(id: Long): Playlist? {
        val pw = playlistDao.getPlaylistWithTracks(id) ?: return null
        return pw.playlist.toDomain().copy(tracks = pw.tracks.map { it.toDomain() })
    }

    override fun observePlaylist(id: Long): Flow<Playlist?> =
        playlistDao.getByIdFlow(id).map { it?.toDomain() }

    override fun getPlaylistsByType(type: PlaylistType): Flow<List<Playlist>> =
        playlistDao.getByType(type).map { list -> list.map { it.toDomain() } }

    override fun observeLikeState(trackId: Long): Flow<TrackLikeState?> =
        trackDao.observeLikeState(trackId)

    override fun observeTrackById(trackId: Long): Flow<Track?> =
        trackDao.observeById(trackId).map { it?.toDomain() }

    override fun observeTrackByYoutubeId(youtubeId: String): Flow<Track?> =
        trackDao.observeByYoutubeId(youtubeId).map { it?.toDomain() }

    override fun observeDownloadStatus(trackId: Long): Flow<DownloadStatus?> = emptyFlow()

    override suspend fun recordPlay(trackId: Long) {
        trackDao.incrementPlayCount(trackId)
        trackDao.updateLastPlayed(trackId, System.currentTimeMillis())
    }

    override suspend fun insertTrack(track: Track): Long =
        trackDao.insert(track.toEntity())

    override suspend fun ensureTrackPersisted(track: Track): Long {
        val syntheticId = !track.youtubeId.isNullOrBlank() &&
            (track.id == track.youtubeId.hashCode().toLong() || track.id < 0L)
        if (track.id > 0L && !syntheticId) {
            val existing = trackDao.getById(track.id)
            if (existing != null) return existing.id
        }
        val ytId = track.youtubeId
        if (!ytId.isNullOrBlank()) {
            val byYt = trackDao.findByYoutubeId(ytId)
            if (byYt != null) return byYt.id
        }
        val spUri = track.spotifyUri
        if (!spUri.isNullOrBlank()) {
            val bySp = trackDao.findBySpotifyUri(spUri)
            if (bySp != null) return bySp.id
        }
        val (cTitle, cArtist) = blocklistGuard.canonicalWordsForSave(track.title, track.artist)
        return trackDao.insert(
            track.toEntity().copy(
                id = 0L,
                canonicalTitle = cTitle,
                canonicalArtist = cArtist,
                isStreamable = true,
            )
        )
    }

    override suspend fun ensureExactTrackPersisted(track: SharedTrack): Long {
        val yt = track.youtubeId?.takeIf { it.isNotBlank() }?.let { trackDao.findByYoutubeId(it) }
        if (yt != null) return yt.id
        val spUri = track.spotifyId?.takeIf { it.isNotBlank() }?.let { "spotify:track:$it" }
        if (spUri != null) {
            val sp = trackDao.findBySpotifyUri(spUri)
            if (sp != null) return sp.id
        }
        val isrcMatch = track.isrc?.takeIf { it.isNotBlank() }?.let { trackDao.findByIsrc(it) }
        if (isrcMatch != null) return isrcMatch.id

        val domainTrack = track.toTrack()
        val (cTitle, cArtist) = blocklistGuard.canonicalWordsForSave(domainTrack.title, domainTrack.artist)
        return trackDao.insert(
            domainTrack.toEntity().copy(
                id = 0L,
                canonicalTitle = cTitle,
                canonicalArtist = cArtist,
                isStreamable = true,
            )
        )
    }

    override suspend fun deleteTrack(track: Track): Boolean {
        track.filePath?.let { path ->
            runCatching { File(path).delete() }
        }
        trackDao.delete(track.toEntity())
        _trackDeletions.emit(track.id)
        return true
    }

    override suspend fun queueDownload(trackId: Long): Boolean = false
    override suspend fun downloadWaitReason(trackId: Long): DownloadWaitReason? = null

    override suspend fun removeDownload(trackId: Long) {
        val entity = trackDao.getById(trackId) ?: return
        entity.filePath?.let { runCatching { File(it).delete() } }
        trackDao.clearDownloadState(trackId)
    }

    override suspend fun queueDownloadsForPlaylist(playlistId: Long, background: Boolean): Int = 0
    override suspend fun queueKeptPlaylists(manualSync: Boolean): Int = 0
    override suspend fun resumeWaitingDownloads(tap: Boolean) {}

    override suspend fun setPlaylistDownload(playlistId: Long, on: Boolean) {
        playlistDao.setKeepOffline(playlistId, on)
    }

    override suspend fun removeDownloadsForPlaylist(playlistId: Long): Int = 0

    override suspend fun removePlaylist(playlist: Playlist) {
        playlistDao.delete(playlist.toEntity())
    }

    override suspend fun ensureDownloadsMixSeeded(): Long =
        playlistDao.ensurePlaylist(
            PlaylistEntity(
                name = "Your Downloads",
                type = PlaylistType.DOWNLOADS_MIX,
                source = MusicSource.BOTH,
                sourceId = "stash_downloads_mix",
                isActive = true,
                syncEnabled = false,
                keepOffline = false,
            )
        )

    override suspend fun linkTrackToDownloadsMix(trackId: Long) {
        val playlistId = ensureDownloadsMixSeeded()
        val pos = playlistDao.getNextPosition(playlistId)
        playlistDao.insertCrossRef(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = pos,
                addedAt = Instant.now(),
            )
        )
    }

    override suspend fun updatePlaylistArtUrl(playlistId: Long, artUrl: String?) {
        playlistDao.updateArtUrl(playlistId, artUrl)
    }

    override suspend fun setPlaylistPinned(playlistId: Long, pinned: Boolean) {
        playlistDao.setPinned(playlistId, pinned)
    }

    override suspend fun setPlaylistPinnedToHome(playlistId: Long, pinnedAt: Long?) {
        playlistDao.setPinnedToHome(playlistId, pinnedAt)
    }

    override suspend fun cleanOrphanedMixTracks(): Int = 0
    override suspend fun cancelPendingDownloadsForSource(source: String): Int = 0

    override suspend fun createPlaylist(name: String): Long =
        playlistDao.insert(
            PlaylistEntity(
                name = name,
                sourceId = "custom_${java.util.UUID.randomUUID()}",
                type = PlaylistType.CUSTOM,
                source = MusicSource.BOTH,
                isActive = true,
                syncEnabled = true,
                keepOffline = false,
            )
        )

    override suspend fun ensureCustomPlaylist(name: String, sourceId: String, artUrl: String?): Long =
        playlistDao.ensurePlaylist(
            PlaylistEntity(
                name = name,
                sourceId = sourceId,
                artUrl = artUrl,
                type = PlaylistType.CUSTOM,
                source = MusicSource.BOTH,
                isActive = true,
                syncEnabled = true,
                keepOffline = false,
            )
        )

    private suspend fun recountPlaylistTracks(playlistId: Long) {
        playlistDao.updateTrackCount(
            playlistId,
            trackDao.countByPlaylist(playlistId, includeStreamable = true),
        )
    }

    override suspend fun addTrackToPlaylist(trackId: Long, playlistId: Long) {
        if (trackDao.getById(trackId) == null || playlistDao.getById(playlistId) == null) return
        val pos = playlistDao.getNextPosition(playlistId)
        playlistDao.insertCrossRef(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = pos,
                addedAt = Instant.now(),
                locallyAdded = true,
            )
        )
        recountPlaylistTracks(playlistId)
    }

    override suspend fun addTracksToPlaylist(trackIds: List<Long>, playlistId: Long) {
        if (playlistDao.getById(playlistId) == null) return
        var pos = playlistDao.getNextPosition(playlistId)
        trackIds.forEach { trackId ->
            if (trackDao.getById(trackId) != null) {
                playlistDao.insertCrossRef(
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = trackId,
                        position = pos++,
                        addedAt = Instant.now(),
                        locallyAdded = true,
                    )
                )
            }
        }
        recountPlaylistTracks(playlistId)
    }

    override suspend fun createPlaylistWithTracks(name: String, trackIds: List<Long>): Long {
        val playlistId = createPlaylist(name)
        addTracksToPlaylist(trackIds, playlistId)
        return playlistId
    }

    override suspend fun removeTrackFromPlaylist(trackId: Long, playlistId: Long) {
        playlistDao.softDeleteTrackFromPlaylist(playlistId, trackId)
        recountPlaylistTracks(playlistId)
    }

    override fun getUserCreatedPlaylists(): Flow<List<Playlist>> =
        playlistDao.getUserCreatedPlaylists().map { list -> list.map { it.toDomain() } }

    override fun getPickablePlaylists(): Flow<List<Playlist>> =
        playlistDao.getPickablePlaylists().map { list -> list.map { it.toDomain() } }

    override fun getUnmatchedTracks(): Flow<List<UnmatchedTrackView>> = emptyFlow()
    override fun getUnmatchedCount(): Flow<Int> = flowOf(0)
    override suspend fun dismissMatch(trackId: Long) {
        trackDao.dismissMatch(trackId)
    }

    override suspend fun setMatchFlagged(trackId: Long, flagged: Boolean) {
        trackDao.updateMatchFlagged(trackId, flagged)
    }

    override fun getFlaggedTracks(): Flow<List<TrackEntity>> = trackDao.getFlaggedTracks()
    override fun getFlaggedCount(): Flow<Int> = trackDao.getFlaggedCount()

    override suspend fun removeTrackFromPlaylistAndMaybeDelete(
        trackId: Long,
        fromPlaylistId: Long,
        alsoBlacklist: Boolean,
    ): MusicRepository.CascadeRemovalSummary {
        playlistDao.softDeleteTrackFromPlaylist(fromPlaylistId, trackId)
        recountPlaylistTracks(fromPlaylistId)
        return MusicRepository.CascadeRemovalSummary(
            deleted = 0,
            keptProtected = 1,
            keptElsewhere = 0,
            blacklisted = 0,
        )
    }

    override suspend fun deletePlaylistWithCascade(
        playlistId: Long,
        alsoBlacklist: Boolean,
    ): MusicRepository.CascadeRemovalSummary {
        playlistDao.getById(playlistId)?.let { playlistDao.delete(it) }
        return MusicRepository.CascadeRemovalSummary(
            deleted = 0,
            keptProtected = 0,
            keptElsewhere = 0,
            blacklisted = 0,
        )
    }

    override suspend fun isTrackProtectedExcluding(trackId: Long, excludePlaylistId: Long): Boolean =
        trackDao.isTrackInProtectedPlaylistExcluding(trackId, excludePlaylistId)

    override suspend fun blacklistTrack(trackId: Long) {}
    override suspend fun unblacklistTrack(trackId: Long) {}

    override suspend fun getLatestSync(): SyncHistoryEntity? = syncHistoryDao.getLatest()
    override fun observeLatestSync(): Flow<SyncHistoryEntity?> = syncHistoryDao.observeLatest()
    override fun getAllSyncHistory(): Flow<List<SyncHistoryEntity>> = syncHistoryDao.observeAll()

    override suspend fun applyStreamingMode(enabled: Boolean) {
        streamingPreference.setEnabled(enabled)
    }
}
