package com.stash.core.media

import com.stash.core.data.radio.RadioSeed
import com.stash.core.media.engine.PlaybackEngine
import com.stash.core.media.handoff.HandoffQueuePlan
import com.stash.core.model.PlayableItem
import com.stash.core.model.PlaybackSource
import com.stash.core.model.PlayerState
import com.stash.core.model.RadioStartResult
import com.stash.core.model.Track
import com.stash.core.model.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopPlayerRepository @Inject constructor(
    private val engine: PlaybackEngine,
    private val streamResolver: DesktopStreamResolver,
) : PlayerRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    override val currentPosition: Flow<Long> = flow {
        while (true) {
            emit(engine.currentPositionMs)
            delay(250)
        }
    }

    private val _streamingHaltedEvents = MutableSharedFlow<StreamingHaltedEvent>()
    override val streamingHaltedEvents: SharedFlow<StreamingHaltedEvent> = _streamingHaltedEvents.asSharedFlow()

    private val _userMessages = MutableSharedFlow<String>()
    override val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _radioSeedLabel = MutableStateFlow<String?>(null)
    override val radioSeedLabel: StateFlow<String?> = _radioSeedLabel.asStateFlow()

    init {
        scope.launch {
            engine.playbackState.collect { state ->
                when (state) {
                    is PlaybackEngine.State.Playing -> {
                        _playerState.value = _playerState.value.copy(isPlaying = true)
                    }
                    is PlaybackEngine.State.Paused -> {
                        _playerState.value = _playerState.value.copy(isPlaying = false)
                    }
                    is PlaybackEngine.State.Ended -> {
                        skipNext()
                    }
                    is PlaybackEngine.State.Error -> {
                        _playerState.value = _playerState.value.copy(isPlaying = false)
                        _userMessages.emit(state.message)
                    }
                    else -> Unit
                }
            }
        }
    }

    override suspend fun play() {
        engine.resume()
        _playerState.value = _playerState.value.copy(isPlaying = true)
    }

    override suspend fun pause() {
        engine.pause()
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    override fun setVolume(volume: Float) {
        engine.setVolume(volume)
    }

    override fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    override suspend fun skipNext() {
        val s = _playerState.value
        val nextIndex = s.currentIndex + 1
        if (nextIndex < s.queue.size) {
            skipToQueueIndex(nextIndex)
        } else {
            engine.stop()
            _playerState.value = s.copy(isPlaying = false)
        }
    }

    override suspend fun skipPrevious() {
        if (engine.currentPositionMs > 3000L) {
            engine.seekTo(0L)
            return
        }
        val s = _playerState.value
        val prevIndex = s.currentIndex - 1
        if (prevIndex >= 0 && prevIndex < s.queue.size) {
            skipToQueueIndex(prevIndex)
        } else {
            engine.seekTo(0L)
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
    }

    override suspend fun setQueue(tracks: List<Track>, startIndex: Int, source: PlaybackSource) {
        val safeIndex = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        val current = tracks.getOrNull(safeIndex)
        _playerState.value = _playerState.value.copy(
            queue = tracks,
            currentIndex = safeIndex,
            currentTrack = current,
            isPlaying = current != null,
        )
        if (current != null) {
            playTrackItem(current)
        }
    }

    private fun playTrackItem(track: Track) {
        scope.launch {
            val uri = streamResolver.resolveForTrack(track)
            if (uri.isNullOrBlank()) {
                _userMessages.emit("Unable to play '${track.title}'. Stream not available.")
                _playerState.value = _playerState.value.copy(isPlaying = false)
                return@launch
            }
            val item = PlayableItem(
                mediaId = track.id.toString(),
                uri = uri,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                isLocal = track.isDownloaded,
                artworkUri = track.albumArtUrl ?: track.albumArtPath,
            )
            engine.play(item, playWhenReady = true)
        }
    }

    override fun resumeLastQueue() {}

    override suspend fun loadRestoredQueue(): Boolean = false

    override suspend fun restoreHandoff(plan: HandoffQueuePlan): Boolean = false

    override suspend fun shuffleLibrary(): Boolean = false

    override suspend fun addNext(track: Track): Boolean {
        val s = _playerState.value
        val newQueue = s.queue.toMutableList()
        val insertIndex = (s.currentIndex + 1).coerceAtMost(newQueue.size)
        newQueue.add(insertIndex, track)
        _playerState.value = s.copy(queue = newQueue)
        return true
    }

    override suspend fun addToQueue(track: Track): Boolean {
        val s = _playerState.value
        _playerState.value = s.copy(queue = s.queue + track)
        return true
    }

    override suspend fun addToQueue(tracks: List<Track>): Boolean {
        if (tracks.isEmpty()) return false
        val s = _playerState.value
        _playerState.value = s.copy(queue = s.queue + tracks)
        return true
    }

    override suspend fun startRadio(seed: RadioSeed, keepCurrent: Boolean): RadioStartResult =
        RadioStartResult.PlayerNotReady

    override fun stopRadio() {
        _radioSeedLabel.value = null
    }

    override suspend fun toggleShuffle() {
        val s = _playerState.value
        _playerState.value = s.copy(isShuffleEnabled = !s.isShuffleEnabled)
    }

    override suspend fun cycleRepeatMode() {
        val s = _playerState.value
        val nextMode = when (s.repeatMode) {
            com.stash.core.model.RepeatMode.OFF -> com.stash.core.model.RepeatMode.ALL
            com.stash.core.model.RepeatMode.ALL -> com.stash.core.model.RepeatMode.ONE
            com.stash.core.model.RepeatMode.ONE -> com.stash.core.model.RepeatMode.OFF
        }
        _playerState.value = s.copy(repeatMode = nextMode)
    }

    override suspend fun removeFromQueue(index: Int) {
        val s = _playerState.value
        if (index !in s.queue.indices) return
        val newQueue = s.queue.toMutableList()
        newQueue.removeAt(index)
        val newIndex = when {
            index < s.currentIndex -> s.currentIndex - 1
            index == s.currentIndex -> s.currentIndex.coerceAtMost(newQueue.size - 1)
            else -> s.currentIndex
        }
        _playerState.value = s.copy(
            queue = newQueue,
            currentIndex = newIndex,
            currentTrack = newQueue.getOrNull(newIndex),
        )
    }

    override suspend fun insertInQueue(index: Int, track: Track): Boolean {
        val s = _playerState.value
        val newQueue = s.queue.toMutableList()
        val clampedIndex = index.coerceIn(0, newQueue.size)
        newQueue.add(clampedIndex, track)
        val newCurrentIndex = if (clampedIndex <= s.currentIndex) s.currentIndex + 1 else s.currentIndex
        _playerState.value = s.copy(queue = newQueue, currentIndex = newCurrentIndex)
        return true
    }

    override fun evictFromQueue(trackIds: Set<Long>) {
        val s = _playerState.value
        val newQueue = s.queue.filterNot { it.id in trackIds }
        _playerState.value = s.copy(queue = newQueue)
    }

    override suspend fun moveInQueue(from: Int, to: Int) {
        val s = _playerState.value
        if (from !in s.queue.indices || to !in s.queue.indices) return
        val newQueue = s.queue.toMutableList()
        val item = newQueue.removeAt(from)
        newQueue.add(to, item)
        _playerState.value = s.copy(queue = newQueue)
    }

    override suspend fun skipToQueueIndex(index: Int) {
        val s = _playerState.value
        if (index !in s.queue.indices) return
        val track = s.queue[index]
        _playerState.value = s.copy(
            currentIndex = index,
            currentTrack = track,
            isPlaying = true,
        )
        playTrackItem(track)
    }

    override suspend fun playTrack(track: Track): StreamRoutingResult {
        setQueue(listOf(track), 0, PlaybackSource.Unknown)
        val uri = streamResolver.resolveForTrack(track) ?: return StreamRoutingResult.NotAvailable
        val playable = PlayableItem(
            mediaId = track.id.toString(),
            uri = uri,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            isLocal = track.isDownloaded,
            artworkUri = track.albumArtUrl ?: track.albumArtPath,
        )
        return StreamRoutingResult.Item(playable)
    }

    override suspend fun playFromStream(item: TrackItem): StreamRoutingResult {
        val durationMs = (item.durationSeconds * 1000).toLong()
        val track = Track(
            id = item.videoId.hashCode().toLong(),
            title = item.title,
            artist = item.artist,
            album = item.album.orEmpty(),
            durationMs = durationMs,
            albumArtUrl = item.thumbnailUrl,
            youtubeId = item.videoId,
        )
        _playerState.value = _playerState.value.copy(
            queue = listOf(track),
            currentIndex = 0,
            currentTrack = track,
            isPlaying = true,
        )

        val uri = streamResolver.resolveStreamUrl(item.videoId)
        if (uri.isNullOrBlank()) {
            _playerState.value = _playerState.value.copy(isPlaying = false)
            return StreamRoutingResult.NotAvailable
        }

        val playable = PlayableItem(
            mediaId = item.videoId,
            uri = uri,
            title = item.title,
            artist = item.artist,
            album = item.album.orEmpty(),
            durationMs = durationMs,
            artworkUri = item.thumbnailUrl,
        )
        engine.play(playable, playWhenReady = true)
        return StreamRoutingResult.Item(playable)
    }
}
