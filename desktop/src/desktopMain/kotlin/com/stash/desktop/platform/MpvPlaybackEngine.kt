package com.stash.desktop.platform

import com.stash.core.media.engine.PlaybackEngine
import com.stash.core.model.PlayableItem
import com.stash.desktop.spike.MpvBinding
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpvPlaybackEngine @Inject constructor() : PlaybackEngine, Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var handle: Pointer? = null
    private var pollerJob: Job? = null

    private val _playbackState = MutableStateFlow<PlaybackEngine.State>(PlaybackEngine.State.Idle)
    override val playbackState: StateFlow<PlaybackEngine.State> = _playbackState.asStateFlow()

    private var _currentPositionMs: Long = 0L
    override val currentPositionMs: Long get() = _currentPositionMs

    private var _durationMs: Long = 0L
    override val durationMs: Long get() = _durationMs

    private var _currentItem: PlayableItem? = null
    override val currentItem: PlayableItem? get() = _currentItem

    val isNativeEngineAvailable: Boolean get() = handle != null

    init {
        val lib = MpvBinding.instance
        if (lib != null) {
            val h = lib.mpv_create()
            if (h != null) {
                lib.mpv_set_option_string(h, "vo", "null")
                lib.mpv_set_option_string(h, "terminal", "no")
                lib.mpv_set_option_string(h, "audio-display", "no")
                lib.mpv_set_option_string(h, "gapless-audio", "yes")
                lib.mpv_set_option_string(h, "ytdl", "yes")
                val status = lib.mpv_initialize(h)
                if (status >= 0) {
                    handle = h
                    println("MpvPlaybackEngine: libmpv handle initialized successfully (handle=$h)")
                } else {
                    val err = lib.mpv_error_string(status)
                    println("MpvPlaybackEngine: mpv_initialize failed: $err ($status)")
                }
            } else {
                println("MpvPlaybackEngine: mpv_create returned null")
            }
        } else {
            println("MpvPlaybackEngine: MpvBinding.instance is null (libmpv not found)")
        }
    }

    override fun play(item: PlayableItem, playWhenReady: Boolean) {
        _currentItem = item
        _currentPositionMs = 0L
        _durationMs = item.durationMs

        println("MpvPlaybackEngine: playing item='${item.title}' uri='${item.uri}' playWhenReady=$playWhenReady")

        val h = handle
        val lib = MpvBinding.instance
        if (h == null || lib == null) {
            val msg = "libmpv native library not available. Please install mpv on your system or place libmpv-2.dll in ~/.stash/bin/"
            println("MpvPlaybackEngine: $msg")
            _playbackState.value = PlaybackEngine.State.Error(msg)
            return
        }

        val code = lib.mpv_command(h, arrayOf("loadfile", item.uri, "replace", null))
        if (code >= 0) {
            if (!playWhenReady) {
                lib.mpv_set_property_string(h, "pause", "yes")
                _playbackState.value = PlaybackEngine.State.Paused
            } else {
                lib.mpv_set_property_string(h, "pause", "no")
                _playbackState.value = PlaybackEngine.State.Playing
                startPoller()
            }
        } else {
            val err = lib.mpv_error_string(code)
            println("MpvPlaybackEngine: loadfile failed with code=$code, error='$err'")
            _playbackState.value = PlaybackEngine.State.Error("Failed to load file in mpv: $err")
        }
    }

    override fun pause() {
        val h = handle
        val lib = MpvBinding.instance
        if (h != null && lib != null) {
            lib.mpv_set_property_string(h, "pause", "yes")
        }
        stopPoller()
        _playbackState.value = PlaybackEngine.State.Paused
    }

    override fun resume() {
        val h = handle
        val lib = MpvBinding.instance
        if (h != null && lib != null) {
            lib.mpv_set_property_string(h, "pause", "no")
            _playbackState.value = PlaybackEngine.State.Playing
            startPoller()
        }
    }

    override fun stop() {
        val h = handle
        val lib = MpvBinding.instance
        if (h != null && lib != null) {
            lib.mpv_command(h, arrayOf("stop", null))
        }
        stopPoller()
        _currentPositionMs = 0L
        _playbackState.value = PlaybackEngine.State.Idle
    }

    override fun seekTo(positionMs: Long) {
        _currentPositionMs = positionMs
        val h = handle
        val lib = MpvBinding.instance
        if (h != null && lib != null) {
            val seconds = positionMs / 1000.0
            lib.mpv_command(h, arrayOf("seek", seconds.toString(), "absolute", null))
        }
    }

    override fun setVolume(volume: Float) {
        val h = handle
        val lib = MpvBinding.instance
        if (h != null && lib != null) {
            val intVol = (volume * 100f).coerceIn(0f, 100f).toInt()
            lib.mpv_set_property_string(h, "volume", intVol.toString())
        }
    }

    private fun startPoller() {
        pollerJob?.cancel()
        pollerJob = scope.launch {
            val h = handle ?: return@launch
            val lib = MpvBinding.instance ?: return@launch
            var hasStartedPlaying = false

            while (isActive) {
                delay(250)
                try {
                    // Query time-pos
                    val posPtr = lib.mpv_get_property_string(h, "time-pos")
                    if (posPtr != null) {
                        val posStr = posPtr.getString(0, "UTF-8")
                        lib.mpv_free(posPtr)
                        posStr.toDoubleOrNull()?.let { sec ->
                            val posMs = (sec * 1000).toLong()
                            _currentPositionMs = posMs
                            if (posMs > 0L) {
                                hasStartedPlaying = true
                            }
                        }
                    }

                    // Query duration
                    val durPtr = lib.mpv_get_property_string(h, "duration")
                    if (durPtr != null) {
                        val durStr = durPtr.getString(0, "UTF-8")
                        lib.mpv_free(durPtr)
                        durStr.toDoubleOrNull()?.let { sec ->
                            val parsedDurMs = (sec * 1000).toLong()
                            if (parsedDurMs > 0L) {
                                _durationMs = parsedDurMs
                            }
                        }
                    }

                    // Query eof-reached
                    val eofPtr = lib.mpv_get_property_string(h, "eof-reached")
                    var isEof = false
                    if (eofPtr != null) {
                        val eofStr = eofPtr.getString(0, "UTF-8")
                        lib.mpv_free(eofPtr)
                        if (eofStr == "yes") {
                            isEof = true
                        }
                    }

                    // Query idle-active (true when playback ended or idle)
                    val idlePtr = lib.mpv_get_property_string(h, "idle-active")
                    var isIdle = false
                    if (idlePtr != null) {
                        val idleStr = idlePtr.getString(0, "UTF-8")
                        lib.mpv_free(idlePtr)
                        if (idleStr == "yes") {
                            isIdle = true
                        } else {
                            hasStartedPlaying = true
                        }
                    }

                    // Only transition to Ended if playback had actively started,
                    // and either reached EOF or returned to idle state.
                    if (hasStartedPlaying && (isEof || isIdle) && _playbackState.value is PlaybackEngine.State.Playing) {
                        println("MpvPlaybackEngine: playback ended (isEof=$isEof, isIdle=$isIdle)")
                        _playbackState.value = PlaybackEngine.State.Ended
                        stopPoller()
                        break
                    }
                } catch (t: Throwable) {
                    println("MpvPlaybackEngine poller warning: ${t.message}")
                }
            }
        }
    }

    private fun stopPoller() {
        pollerJob?.cancel()
        pollerJob = null
    }

    override fun close() {
        stop()
        handle?.let { MpvBinding.instance?.mpv_terminate_destroy(it) }
        handle = null
    }
}
