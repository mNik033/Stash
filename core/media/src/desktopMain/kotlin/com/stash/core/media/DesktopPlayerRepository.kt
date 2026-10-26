package com.stash.core.media

import com.stash.core.data.radio.RadioSeed
import com.stash.core.data.repository.MusicRepository
import com.stash.core.media.engine.PlaybackEngine
import com.stash.core.media.handoff.HandoffQueuePlan
import com.stash.core.model.PlayableItem
import com.stash.core.model.PlaybackSource
import com.stash.core.model.PlayerState
import com.stash.core.model.RadioStartResult
import com.stash.core.model.RepeatMode
import com.stash.core.model.Track
import com.stash.core.model.TrackItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopPlayerRepository @Inject constructor(
    private val engine: PlaybackEngine,
    private val streamResolver: DesktopStreamResolver,
    private val musicRepository: MusicRepository,
) : PlayerRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playJob: Job? = null
    private var unshuffledQueue: List<Track> = emptyList()

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    override val currentPosition: Flow<Long> = flow {
        while (true) {
            val pos = engine.currentPositionMs
            val dur = engine.durationMs
            val state = _playerState.value

            val durationChanged = dur > 0L && state.durationMs != dur
            val positionChanged = state.positionMs != pos
            val trackNeedsDuration = dur > 0L && state.currentTrack?.let { it.durationMs <= 0L } == true

            if (durationChanged || positionChanged || trackNeedsDuration) {
                _playerState.value = state.copy(
                    positionMs = pos,
                    durationMs = if (dur > 0L) dur else state.durationMs,
                    currentTrack = state.currentTrack?.let { track ->
                        if (dur > 0L && (track.durationMs != dur || track.durationMs <= 0L)) track.copy(durationMs = dur) else track
                    }
                )
            }
            emit(pos)
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
                        _playerState.value = _playerState.value.copy(isPlaying = true, isBuffering = false)
                    }
                    is PlaybackEngine.State.Paused -> {
                        _playerState.value = _playerState.value.copy(isPlaying = false, isBuffering = false)
                    }
                    is PlaybackEngine.State.Buffering -> {
                        _playerState.value = _playerState.value.copy(isBuffering = true)
                    }
                    is PlaybackEngine.State.Ended -> {
                        val current = _playerState.value.currentTrack
                        if (_playerState.value.repeatMode == RepeatMode.ONE && current != null) {
                            playTrackItem(current)
                        } else {
                            advanceOnTrackEnded()
                        }
                    }
                    is PlaybackEngine.State.Error -> {
                        _playerState.value = _playerState.value.copy(isPlaying = false, isBuffering = false)
                        _playerState.value.currentTrack?.let { streamResolver.invalidate(it) }
                        _userMessages.emit(state.message)
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun advanceOnTrackEnded() {
        val s = _playerState.value
        val nextIndex = s.currentIndex + 1
        if (nextIndex < s.queue.size) {
            skipToQueueIndex(nextIndex)
        } else if (s.repeatMode == RepeatMode.ALL && s.queue.isNotEmpty()) {
            skipToQueueIndex(0)
        } else {
            playJob?.cancel()
            engine.stop()
            _playerState.value = s.copy(isPlaying = false, isBuffering = false)
        }
    }

    override suspend fun play() {
        val s = _playerState.value
        val state = engine.playbackState.value
        val track = s.currentTrack
        if ((state is PlaybackEngine.State.Idle || state is PlaybackEngine.State.Ended) && track != null) {
            playTrackItem(track)
        } else {
            engine.resume()
            _playerState.value = s.copy(isPlaying = true)
        }
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
        engine.setPlaybackSpeed(speed)
    }

    override suspend fun skipNext() {
        val s = _playerState.value
        if (s.queue.isEmpty()) {
            playJob?.cancel()
            engine.stop()
            _playerState.value = s.copy(isPlaying = false, isBuffering = false)
            return
        }

        val nextIndex = s.currentIndex + 1
        if (nextIndex < s.queue.size) {
            skipToQueueIndex(nextIndex)
        } else if (s.repeatMode == RepeatMode.ALL && s.queue.isNotEmpty()) {
            skipToQueueIndex(0)
        } else {
            playJob?.cancel()
            engine.stop()
            _playerState.value = s.copy(isPlaying = false, isBuffering = false)
        }
    }

    override suspend fun skipPrevious() {
        if (engine.currentPositionMs > 3000L) {
            engine.seekTo(0L)
            _playerState.value = _playerState.value.copy(positionMs = 0L)
            return
        }
        val s = _playerState.value
        val prevIndex = s.currentIndex - 1
        if (prevIndex >= 0 && prevIndex < s.queue.size) {
            skipToQueueIndex(prevIndex)
        } else {
            engine.seekTo(0L)
            _playerState.value = _playerState.value.copy(positionMs = 0L)
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(positionMs = positionMs)
    }

    override suspend fun setQueue(tracks: List<Track>, startIndex: Int, source: PlaybackSource) {
        val safeIndex = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        val current = tracks.getOrNull(safeIndex)

        playJob?.cancel()
        engine.stop()

        _playerState.value = _playerState.value.copy(
            queue = tracks,
            currentIndex = safeIndex,
            currentTrack = current,
            positionMs = 0L,
            durationMs = current?.durationMs ?: 0L,
            isPlaying = current != null,
            isBuffering = current != null,
        )
        if (current != null) {
            playTrackItem(current)
            prefetchNextTrack(tracks, safeIndex)
        }
    }

    private fun playTrackItem(track: Track) {
        playJob?.cancel()
        playJob = scope.launch {
            try {
                val uri = streamResolver.resolveForTrack(track)
                if (!isActive) return@launch
                if (uri.isNullOrBlank()) {
                    _userMessages.emit("Unable to play '${track.title}'. Stream not available.")
                    _playerState.value = _playerState.value.copy(isPlaying = false, isBuffering = false)
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
                if (!isActive) return@launch
                engine.play(item, playWhenReady = true)
                if (track.id > 0L) {
                    runCatching { musicRepository.recordPlay(track.id) }
                }
                _playerState.value = _playerState.value.copy(
                    isPlaying = true,
                    isBuffering = false,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _playerState.value = _playerState.value.copy(isPlaying = false, isBuffering = false)
                _userMessages.emit("Error playing '${track.title}': ${e.message}")
            }
        }
    }

    private fun prefetchNextTrack(queue: List<Track>, currentIndex: Int) {
        val nextIndex = currentIndex + 1
        if (nextIndex in queue.indices) {
            val nextTrack = queue[nextIndex]
            scope.launch(Dispatchers.IO) {
                runCatching { streamResolver.resolveForTrack(nextTrack) }
            }
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
        val newShuffle = !s.isShuffleEnabled
        if (newShuffle) {
            unshuffledQueue = s.queue
            val current = s.currentTrack
            val shuffled = if (current != null) {
                listOf(current) + s.queue.filterIndexed { i, _ -> i != s.currentIndex }.shuffled()
            } else {
                s.queue.shuffled()
            }
            _playerState.value = s.copy(
                isShuffleEnabled = true,
                queue = shuffled,
                currentIndex = 0,
            )
        } else {
            val restored = if (unshuffledQueue.isNotEmpty()) unshuffledQueue else s.queue
            val newIdx = restored.indexOfFirst { it.id == s.currentTrack?.id }.coerceAtLeast(0)
            _playerState.value = s.copy(
                isShuffleEnabled = false,
                queue = restored,
                currentIndex = newIdx,
            )
            unshuffledQueue = emptyList()
        }
    }

    override suspend fun cycleRepeatMode() {
        val s = _playerState.value
        val nextMode = when (s.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playerState.value = s.copy(repeatMode = nextMode)
    }

    override suspend fun removeFromQueue(index: Int) {
        val s = _playerState.value
        if (index !in s.queue.indices) return
        val newQueue = s.queue.toMutableList()
        newQueue.removeAt(index)
        if (newQueue.isEmpty()) {
            playJob?.cancel()
            engine.stop()
            _playerState.value = s.copy(
                queue = emptyList(),
                currentIndex = 0,
                currentTrack = null,
                isPlaying = false,
                isBuffering = false,
            )
            return
        }
        val newIndex = when {
            index < s.currentIndex -> s.currentIndex - 1
            index == s.currentIndex -> s.currentIndex.coerceAtMost(newQueue.size - 1)
            else -> s.currentIndex
        }
        val newTrack = newQueue[newIndex]
        _playerState.value = s.copy(
            queue = newQueue,
            currentIndex = newIndex,
            currentTrack = newTrack,
        )
        if (index == s.currentIndex) {
            playTrackItem(newTrack)
        }
    }

    override suspend fun insertInQueue(index: Int, track: Track): Boolean {
        val s = _playerState.value
        val newQueue = s.queue.toMutableList()
        val clampedIndex = index.coerceIn(0, newQueue.size)
        newQueue.add(clampedIndex, track)
        val newCurrentIndex = newQueue.indexOfFirst { it.id == s.currentTrack?.id }.coerceAtLeast(0)
        _playerState.value = s.copy(queue = newQueue, currentIndex = newCurrentIndex)
        return true
    }

    override fun evictFromQueue(trackIds: Set<Long>) {
        val s = _playerState.value
        val newQueue = s.queue.filterNot { it.id in trackIds }
        if (newQueue.isEmpty()) {
            playJob?.cancel()
            engine.stop()
            _playerState.value = s.copy(
                queue = emptyList(),
                currentIndex = 0,
                currentTrack = null,
                isPlaying = false,
                isBuffering = false,
            )
            return
        }
        val currentTrack = s.currentTrack
        val currentEvicted = currentTrack != null && currentTrack.id in trackIds
        val newIndex = if (currentEvicted) {
            s.currentIndex.coerceAtMost(newQueue.size - 1)
        } else {
            newQueue.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
        }
        val nextTrack = newQueue[newIndex]
        _playerState.value = s.copy(
            queue = newQueue,
            currentIndex = newIndex,
            currentTrack = nextTrack,
        )
        if (currentEvicted) {
            playTrackItem(nextTrack)
        }
    }

    override suspend fun moveInQueue(from: Int, to: Int) {
        val s = _playerState.value
        if (from !in s.queue.indices || to !in s.queue.indices) return
        val newQueue = s.queue.toMutableList()
        val item = newQueue.removeAt(from)
        newQueue.add(to, item)
        val newIdx = newQueue.indexOfFirst { it.id == s.currentTrack?.id }.coerceAtLeast(0)
        _playerState.value = s.copy(queue = newQueue, currentIndex = newIdx)
    }

    override suspend fun skipToQueueIndex(index: Int) {
        val s = _playerState.value
        if (index !in s.queue.indices) return
        val track = s.queue[index]

        playJob?.cancel()
        engine.stop()

        _playerState.value = s.copy(
            currentIndex = index,
            currentTrack = track,
            positionMs = 0L,
            durationMs = track.durationMs,
            isPlaying = true,
            isBuffering = true,
        )
        playTrackItem(track)
        prefetchNextTrack(s.queue, index)
    }

    override suspend fun playTrack(track: Track): StreamRoutingResult {
        playJob?.cancel()
        engine.stop()

        _playerState.value = _playerState.value.copy(
            queue = listOf(track),
            currentIndex = 0,
            currentTrack = track,
            positionMs = 0L,
            durationMs = track.durationMs,
            isPlaying = true,
            isBuffering = true,
        )

        val uri = streamResolver.resolveForTrack(track)
        if (uri.isNullOrBlank()) {
            _playerState.value = _playerState.value.copy(isPlaying = false, isBuffering = false)
            return StreamRoutingResult.NotAvailable
        }

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

        engine.play(playable, playWhenReady = true)
        if (track.id > 0L) {
            runCatching { musicRepository.recordPlay(track.id) }
        }
        _playerState.value = _playerState.value.copy(isPlaying = true, isBuffering = false)
        return StreamRoutingResult.Item(playable)
    }

    override suspend fun playFromStream(item: TrackItem): StreamRoutingResult {
        playJob?.cancel()
        engine.stop()

        val durationMs = (item.durationSeconds * 1000).toLong()
        val track = Track(
            id = -kotlin.math.abs(item.videoId.hashCode().toLong()).coerceAtLeast(1L),
            title = item.title,
            artist = item.artist,
            album = item.album.orEmpty(),
            durationMs = durationMs,
            albumArtUrl = item.thumbnailUrl,
            youtubeId = item.videoId,
            isStreamable = true,
        )
        _playerState.value = _playerState.value.copy(
            queue = listOf(track),
            currentIndex = 0,
            currentTrack = track,
            positionMs = 0L,
            durationMs = durationMs,
            isPlaying = true,
            isBuffering = true,
        )

        val uri = streamResolver.resolveStreamUrl(item.videoId)
        if (uri.isNullOrBlank()) {
            _playerState.value = _playerState.value.copy(isPlaying = false, isBuffering = false)
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
        _playerState.value = _playerState.value.copy(isPlaying = true, isBuffering = false)
        return StreamRoutingResult.Item(playable)
    }

    override fun updateCurrentTrack(track: Track) {
        val state = _playerState.value
        if (state.currentTrack?.id == track.id) {
            val updatedQueue = state.queue.map { if (it.id == track.id) track else it }
            _playerState.value = state.copy(currentTrack = track, queue = updatedQueue)
        }
    }
}
