package com.stash.core.media.engine

import com.stash.core.model.PlayableItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Neutral playback engine contract, decoupling queue, routing, and playback state
 * management from platform audio backends (Media3 on Android, libmpv on Desktop).
 */
interface PlaybackEngine {

    sealed class State {
        data object Idle : State()
        data object Buffering : State()
        data object Playing : State()
        data object Paused : State()
        data object Ended : State()
        data class Error(val message: String, val cause: Throwable? = null) : State()
    }

    /** Observable flow of the engine's playback state. */
    val playbackState: StateFlow<State>

    /** Current playback position in milliseconds. */
    val currentPositionMs: Long

    /** Duration in milliseconds of the active track, or 0 if unknown/live. */
    val durationMs: Long

    /** Currently loaded item, or null if idle. */
    val currentItem: PlayableItem?

    /** Prepare and start playing [item]. */
    fun play(item: PlayableItem, playWhenReady: Boolean = true)

    /** Pause active playback. */
    fun pause()

    /** Resume paused playback. */
    fun resume()

    /** Stop playback and release immediate resources. */
    fun stop()

    /** Seek to [positionMs] in the active track. */
    fun seekTo(positionMs: Long)

    /** Set linear audio volume in the range [0.0f, 1.0f]. */
    fun setVolume(volume: Float)

    /** Set playback speed multiplier (e.g. 1.0f). */
    fun setPlaybackSpeed(speed: Float) {}
}
