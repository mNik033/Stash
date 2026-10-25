package com.stash.core.media.actions

import com.stash.core.common.TrackCanonical
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.data.radio.RadioSeed
import com.stash.core.data.repository.MusicRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.media.StreamRoutingResult
import com.stash.core.media.preview.PreviewState
import com.stash.core.model.MusicSource
import com.stash.core.model.Playlist
import com.stash.core.model.RadioStartResult
import com.stash.core.model.Track
import com.stash.core.model.TrackItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopTrackActionsDelegate @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val musicRepository: MusicRepository,
    private val trackDao: TrackDao,
    private val streamingPreference: StreamingPreference,
) : TrackActionsDelegate {

    private val _previewState = MutableStateFlow<PreviewState>(PreviewState.Idle)
    override val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()

    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    override val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    override val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    private val _previewLoadingId = MutableStateFlow<String?>(null)
    override val previewLoadingId: StateFlow<String?> = _previewLoadingId.asStateFlow()

    private val _waitingForLosslessIds = MutableStateFlow<Set<String>>(emptySet())
    override val waitingForLosslessIds: StateFlow<Set<String>> = _waitingForLosslessIds.asStateFlow()

    override val inListenTogether: Boolean = false

    override val userPlaylists: Flow<List<Playlist>> =
        musicRepository.getUserCreatedPlaylists()

    private var boundScope: CoroutineScope? = null

    override fun bindToScope(scope: CoroutineScope) {
        boundScope = scope
    }

    private fun scope(): CoroutineScope =
        checkNotNull(boundScope) { "TrackActionsDelegate used before bindToScope" }

    override fun previewTrack(track: TrackItem) {
        val videoId = track.videoId
        if (_previewLoadingId.value == videoId) return
        _previewLoadingId.value = videoId
        scope().launch {
            try {
                val result = playerRepository.playFromStream(track)
                when (result) {
                    is StreamRoutingResult.Item -> Unit
                    StreamRoutingResult.Deduped -> Unit
                    StreamRoutingResult.NotAvailable ->
                        _userMessages.emit("Couldn't find this track.")
                    StreamRoutingResult.CellularRefused ->
                        _userMessages.emit("Streaming on cellular is off in Settings.")
                    StreamRoutingResult.NoConnectivity ->
                        _userMessages.emit("You're offline — can't stream this track.")
                }
            } finally {
                _previewLoadingId.value = null
            }
        }
    }

    override fun stopPreview() {
        _previewLoadingId.value = null
    }

    override fun downloadTrack(item: TrackItem) {
        _userMessages.tryEmit("Downloading on desktop is coming soon")
    }

    override suspend fun refreshDownloadedIds(videoIds: Collection<String>) {
        if (videoIds.isEmpty()) return
        val downloaded = videoIds.filter { id ->
            trackDao.findByYoutubeId(id)?.isDownloaded == true
        }.toSet()
        _downloadedIds.update { it + downloaded }
    }

    override fun playNext(item: TrackItem) {
        scope().launch {
            try {
                val added = playerRepository.addNext(item.toDomainTrack())
                _userMessages.tryEmit(if (added) "Playing next" else "Couldn't add to queue")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _userMessages.tryEmit("Couldn't add to queue")
            }
        }
    }

    override fun startRadio(item: TrackItem) {
        scope().launch {
            val started = playerRepository.startRadio(
                RadioSeed.Song(
                    title = item.title,
                    artist = item.artist,
                    ytVideoId = item.videoId,
                )
            ) is RadioStartResult.Started
            if (!started) _userMessages.tryEmit("Couldn't start a radio from this — try again in a moment.")
        }
    }

    override fun addToQueue(item: TrackItem) {
        scope().launch {
            try {
                val added = playerRepository.addToQueue(item.toDomainTrack())
                _userMessages.tryEmit(if (added) "Added to queue" else "Couldn't add to queue")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _userMessages.tryEmit("Couldn't add to queue")
            }
        }
    }

    private suspend fun ensureTrackPersisted(item: TrackItem): Long? {
        val existing = trackDao.findByYoutubeId(item.videoId)
        return existing?.id ?: trackDao.insert(
            TrackEntity(
                title = item.title,
                artist = item.artist,
                album = item.album.orEmpty(),
                albumArtist = item.albumArtist.orEmpty(),
                youtubeId = item.videoId,
                canonicalTitle = TrackCanonical.title(item.title),
                canonicalArtist = TrackCanonical.artist(item.artist),
                durationMs = (item.durationSeconds * 1000).toLong(),
                source = MusicSource.YOUTUBE,
                albumArtUrl = item.thumbnailUrl,
            )
        )
    }

    override fun addToPlaylist(item: TrackItem, playlistId: Long) {
        scope().launch {
            try {
                val trackId = ensureTrackPersisted(item) ?: run {
                    _userMessages.tryEmit("Couldn't add to playlist")
                    return@launch
                }
                musicRepository.addTrackToPlaylist(trackId, playlistId)
                _userMessages.tryEmit("Added to playlist")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _userMessages.tryEmit("Couldn't add to playlist")
            }
        }
    }

    override fun createPlaylistAndAdd(item: TrackItem, name: String) {
        scope().launch {
            try {
                val trackId = ensureTrackPersisted(item) ?: run {
                    _userMessages.tryEmit("Couldn't add to playlist")
                    return@launch
                }
                val playlistId = musicRepository.createPlaylist(name)
                musicRepository.addTrackToPlaylist(trackId, playlistId)
                _userMessages.tryEmit("Added to playlist")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _userMessages.tryEmit("Couldn't add to playlist")
            }
        }
    }

    override fun onOwnerCleared() {
        stopPreview()
    }

    private fun TrackItem.toDomainTrack(): Track =
        Track(
            id = videoId.hashCode().toLong(),
            title = title,
            artist = artist,
            album = album.orEmpty(),
            durationMs = (durationSeconds * 1000).toLong(),
            albumArtUrl = thumbnailUrl,
            youtubeId = videoId,
            source = MusicSource.YOUTUBE,
            isStreamable = true,
        )
}
