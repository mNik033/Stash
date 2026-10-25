package com.stash.core.media

import com.stash.core.data.radio.RadioSeed
import com.stash.core.media.handoff.HandoffQueuePlan
import com.stash.core.model.PlaybackSource
import com.stash.core.model.PlayerState
import com.stash.core.model.RadioStartResult
import com.stash.core.model.Track
import com.stash.core.model.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopPlayerRepository @Inject constructor() : PlayerRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    override val currentPosition: Flow<Long> = flow { emit(0L) }

    private val _streamingHaltedEvents = MutableSharedFlow<StreamingHaltedEvent>()
    override val streamingHaltedEvents: SharedFlow<StreamingHaltedEvent> = _streamingHaltedEvents.asSharedFlow()

    private val _userMessages = MutableSharedFlow<String>()
    override val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _radioSeedLabel = MutableStateFlow<String?>(null)
    override val radioSeedLabel: StateFlow<String?> = _radioSeedLabel.asStateFlow()

    override suspend fun play() {
        _playerState.value = _playerState.value.copy(isPlaying = true)
    }

    override suspend fun pause() {
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    override fun setVolume(volume: Float) {}

    override fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    override suspend fun skipNext() {}

    override suspend fun skipPrevious() {}

    override suspend fun seekTo(positionMs: Long) {}

    override suspend fun setQueue(tracks: List<Track>, startIndex: Int, source: PlaybackSource) {
        _playerState.value = _playerState.value.copy(
            queue = tracks,
            currentIndex = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)),
            currentTrack = tracks.getOrNull(startIndex),
            isPlaying = tracks.isNotEmpty(),
        )
    }

    override fun resumeLastQueue() {}

    override suspend fun loadRestoredQueue(): Boolean = false

    override suspend fun restoreHandoff(plan: HandoffQueuePlan): Boolean = false

    override suspend fun shuffleLibrary(): Boolean = false

    override suspend fun addNext(track: Track): Boolean = false

    override suspend fun addToQueue(track: Track): Boolean = false

    override suspend fun addToQueue(tracks: List<Track>): Boolean = false

    override suspend fun startRadio(seed: RadioSeed, keepCurrent: Boolean): RadioStartResult =
        RadioStartResult.PlayerNotReady

    override fun stopRadio() {
        _radioSeedLabel.value = null
    }

    override suspend fun toggleShuffle() {}

    override suspend fun cycleRepeatMode() {}

    override suspend fun removeFromQueue(index: Int) {}

    override suspend fun insertInQueue(index: Int, track: Track): Boolean = false

    override fun evictFromQueue(trackIds: Set<Long>) {}

    override suspend fun moveInQueue(from: Int, to: Int) {}

    override suspend fun skipToQueueIndex(index: Int) {}

    override suspend fun playTrack(track: Track): StreamRoutingResult =
        StreamRoutingResult.NotAvailable

    override suspend fun playFromStream(item: TrackItem): StreamRoutingResult =
        StreamRoutingResult.NotAvailable
}
