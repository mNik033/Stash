package com.stash.desktop.platform

import com.stash.core.common.Log
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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpvPlaybackEngine @Inject constructor() : PlaybackEngine, Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nativeLock = Any()

    @Volatile private var handle: Pointer? = null
    private var pollerJob: Job? = null

    private val _playbackState = MutableStateFlow<PlaybackEngine.State>(PlaybackEngine.State.Idle)
    override val playbackState: StateFlow<PlaybackEngine.State> = _playbackState.asStateFlow()

    @Volatile private var _currentPositionMs: Long = 0L
    override val currentPositionMs: Long get() = _currentPositionMs

    @Volatile private var _durationMs: Long = 0L
    override val durationMs: Long get() = _durationMs

    @Volatile private var _currentItem: PlayableItem? = null
    override val currentItem: PlayableItem? get() = _currentItem

    val isNativeEngineAvailable: Boolean get() = handle != null

    init {
        val lib = MpvBinding.instance
        if (lib != null) {
            MpvBinding.ensureNumericLocaleC()
            val h = lib.mpv_create()
            if (h != null) {
                lib.mpv_set_option_string(h, "config", "no")
                lib.mpv_set_option_string(h, "load-scripts", "no")
                lib.mpv_set_option_string(h, "ytdl", "no")
                lib.mpv_set_option_string(h, "load-unsafe-playlists", "no")
                lib.mpv_set_option_string(h, "vo", "null")
                lib.mpv_set_option_string(h, "vid", "no")
                lib.mpv_set_option_string(h, "terminal", "no")
                lib.mpv_set_option_string(h, "audio-display", "no")
                lib.mpv_set_option_string(h, "gapless-audio", "yes")
                lib.mpv_set_option_string(h, "media-controls", "yes")
                // On macOS, libmpv's internal Swift RemoteCommandCenter requires NSApp to be
                // mpv's own Application subclass (AppHub.isApplication), so its EventHelper fails
                // to initialize in a JVM host and leaves a broken Now Playing / RemoteCommandCenter
                // registration. Disable libmpv's media keys on macOS and use MacOsMediaControls instead.
                lib.mpv_set_option_string(
                    h,
                    "input-media-keys",
                    if (com.sun.jna.Platform.isMac()) "no" else "yes"
                )
                val status = lib.mpv_initialize(h)
                if (status >= 0) {
                    handle = h
                    Log.i(TAG, "libmpv initialized successfully via '${MpvBinding.libraryName}'")
                } else {
                    val err = lib.mpv_error_string(status)
                    lib.mpv_terminate_destroy(h)
                    Log.e(TAG, "mpv_initialize failed: $err ($status)")
                }
            } else {
                Log.e(TAG, "mpv_create returned null")
            }
        } else {
            Log.w(TAG, "MpvBinding.instance is null (libmpv not found)")
        }
    }

    fun attachWindowHandle(hwnd: Long) {
        if (!com.sun.jna.Platform.isWindows()) return
        synchronized(nativeLock) {
            val h = handle ?: return
            val lib = MpvBinding.instance ?: return
            lib.mpv_set_property_string(h, "wid", hwnd.toString())
            Log.i(TAG, "attached to window handle wid=$hwnd")
        }
    }

    private fun isSafeUriOrPath(uri: String): Boolean {
        if (uri.startsWith("https://", ignoreCase = true) || uri.startsWith("http://", ignoreCase = true)) {
            return true
        }
        val cleanPath = if (uri.startsWith("file:")) {
            val stripped = uri.substringBefore('?')
            runCatching { File(java.net.URI(stripped)).absolutePath }
                .getOrElse { stripped.removePrefix("file://").removePrefix("file:") }
        } else {
            uri
        }
        return File(cleanPath).exists()
    }

    override fun play(item: PlayableItem, playWhenReady: Boolean) {
        stopPoller()
        _currentItem = item
        _currentPositionMs = 0L
        _durationMs = item.durationMs

        if (!isSafeUriOrPath(item.uri)) {
            _playbackState.value = PlaybackEngine.State.Error("Unsupported or missing media URI")
            return
        }

        val cleanUri = if (item.uri.startsWith("file:")) {
            val stripped = item.uri.substringBefore('?')
            runCatching { File(java.net.URI(stripped)).absolutePath }
                .getOrElse { stripped.removePrefix("file://").removePrefix("file:") }
        } else {
            item.uri
        }

        val code = synchronized(nativeLock) {
            val h = handle
            val lib = MpvBinding.instance
            if (h == null || lib == null) {
                val msg = "libmpv native library not available. Please install mpv on your system (e.g., 'brew install mpv' on macOS)."
                Log.e(TAG, "Cannot play '${item.title}': $msg")
                _playbackState.value = PlaybackEngine.State.Error(msg)
                return
            }
            val titleStr = if (item.artist.isNotBlank()) "${item.title} - ${item.artist}" else item.title
            lib.mpv_set_property_string(h, "force-media-title", titleStr)
            lib.mpv_set_property_string(h, "title", titleStr)
            lib.mpv_set_property_string(h, "pause", if (playWhenReady) "no" else "yes")
            lib.mpv_command(h, arrayOf("loadfile", cleanUri, "replace", null))
        }

        if (code >= 0) {
            if (!playWhenReady) {
                _playbackState.value = PlaybackEngine.State.Paused
            } else {
                _playbackState.value = PlaybackEngine.State.Buffering
                startPoller()
            }
        } else {
            val err = MpvBinding.instance?.mpv_error_string(code) ?: "code $code"
            Log.w(TAG, "loadfile failed: $err")
            _playbackState.value = PlaybackEngine.State.Error("Failed to load media in mpv: $err")
        }
    }

    override fun pause() {
        synchronized(nativeLock) {
            val h = handle
            val lib = MpvBinding.instance
            if (h != null && lib != null) {
                lib.mpv_set_property_string(h, "pause", "yes")
            }
        }
        _playbackState.value = PlaybackEngine.State.Paused
        // Keep poller running while Paused so external resume via Windows SMTC /
        // hardware media keys is detected and updates _playbackState back to Playing.
        startPoller()
    }

    override fun resume() {
        val canResume = synchronized(nativeLock) {
            val h = handle
            val lib = MpvBinding.instance
            if (h != null && lib != null) {
                lib.mpv_set_property_string(h, "pause", "no")
                true
            } else {
                false
            }
        }
        if (canResume) {
            _playbackState.value = PlaybackEngine.State.Playing
            startPoller()
        }
    }

    override fun stop() {
        stopPoller()
        synchronized(nativeLock) {
            val h = handle
            val lib = MpvBinding.instance
            if (h != null && lib != null) {
                lib.mpv_command(h, arrayOf("stop", null))
            }
        }
        _currentPositionMs = 0L
        _playbackState.value = PlaybackEngine.State.Idle
    }

    override fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        _currentPositionMs = clamped
        synchronized(nativeLock) {
            val h = handle
            val lib = MpvBinding.instance
            if (h != null && lib != null) {
                val seconds = clamped / 1000.0
                lib.mpv_command(h, arrayOf("seek", seconds.toString(), "absolute", null))
            }
        }
    }

    override fun setVolume(volume: Float) {
        synchronized(nativeLock) {
            val h = handle
            val lib = MpvBinding.instance
            if (h != null && lib != null) {
                val intVol = (volume * 100f).coerceIn(0f, 100f).toInt()
                lib.mpv_set_property_string(h, "volume", intVol.toString())
            }
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        synchronized(nativeLock) {
            val h = handle
            val lib = MpvBinding.instance
            if (h != null && lib != null) {
                val clamped = speed.coerceIn(0.25f, 4.0f)
                lib.mpv_set_property_string(h, "speed", clamped.toString())
            }
        }
    }

    private fun readProperty(lib: com.stash.desktop.spike.MpvLib, h: Pointer, name: String): String? {
        val ptr = lib.mpv_get_property_string(h, name) ?: return null
        return try {
            ptr.getString(0, "UTF-8")
        } finally {
            lib.mpv_free(ptr)
        }
    }

    private fun startPoller() {
        pollerJob?.cancel()
        pollerJob = scope.launch {
            var hasStartedPlaying = false
            var idleTicksBeforeStart = 0

            while (isActive) {
                delay(if (_playbackState.value is PlaybackEngine.State.Paused) 400L else 200L)
                var shouldBreak = false
                synchronized(nativeLock) {
                    val h = handle ?: run { shouldBreak = true; return@synchronized }
                    val lib = MpvBinding.instance ?: run { shouldBreak = true; return@synchronized }

                    // Sync external play/pause from SMTC or keyboard
                    val pauseStr = readProperty(lib, h, "pause")
                    val isPaused = (pauseStr == "yes")
                    if (isPaused && _playbackState.value is PlaybackEngine.State.Playing) {
                        _playbackState.value = PlaybackEngine.State.Paused
                    } else if (!isPaused && _playbackState.value is PlaybackEngine.State.Paused) {
                        _playbackState.value = PlaybackEngine.State.Playing
                    }

                    val posStr = readProperty(lib, h, "time-pos")
                    val posSec = posStr?.toDoubleOrNull()
                    if (posSec != null) {
                        _currentPositionMs = (posSec * 1000).toLong().coerceAtLeast(0L)
                        if (!hasStartedPlaying) {
                            hasStartedPlaying = true
                            if (!isPaused) {
                                _playbackState.value = PlaybackEngine.State.Playing
                            }
                        }
                    }

                    val durStr = readProperty(lib, h, "duration")
                    durStr?.toDoubleOrNull()?.let { sec ->
                        val parsedDurMs = (sec * 1000).toLong()
                        if (parsedDurMs > 0L) {
                            _durationMs = parsedDurMs
                        }
                    }

                    val idleStr = readProperty(lib, h, "idle-active")
                    if (idleStr == "yes") {
                        if (hasStartedPlaying) {
                            _playbackState.value = PlaybackEngine.State.Ended
                            shouldBreak = true
                        } else {
                            idleTicksBeforeStart++
                            // Wait up to ~3 seconds (15 * 200ms) for async loadfile to begin before declaring failure
                            if (idleTicksBeforeStart >= 15) {
                                _playbackState.value = PlaybackEngine.State.Error(
                                    "Playback failed to start for '${_currentItem?.title.orEmpty()}'"
                                )
                                shouldBreak = true
                            }
                        }
                    } else {
                        idleTicksBeforeStart = 0
                    }
                }
                if (shouldBreak) break
            }
        }
    }

    private fun stopPoller() {
        pollerJob?.cancel()
        pollerJob = null
    }

    override fun close() {
        stopPoller()
        synchronized(nativeLock) {
            val h = handle
            val lib = MpvBinding.instance
            if (h != null && lib != null) {
                lib.mpv_command(h, arrayOf("stop", null))
                lib.mpv_terminate_destroy(h)
            }
            handle = null
        }
    }

    companion object {
        private const val TAG = "MpvPlaybackEngine"
    }
}
