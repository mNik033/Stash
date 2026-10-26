package com.stash.desktop.platform

import com.stash.core.common.Log
import com.stash.core.media.PlayerRepository
import com.stash.core.media.engine.PlaybackEngine
import com.stash.core.model.Track
import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private interface ObjCRuntime : Library {
    fun objc_getClass(name: String): Pointer?
    fun sel_registerName(name: String): Pointer
    fun objc_allocateClassPair(superclass: Pointer, name: String, extraBytes: Long): Pointer?
    fun class_addMethod(cls: Pointer, name: Pointer, imp: Callback, types: String): Boolean
    fun objc_registerClassPair(cls: Pointer)
}

private interface RemoteCommandActionCallback : Callback {
    fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long
}

private interface MainThreadSelectorCallback : Callback {
    fun invoke(self: Pointer?, cmd: Pointer?, arg: Pointer?)
}

/**
 * Bridges macOS `MPNowPlayingInfoCenter` and `MPRemoteCommandCenter` (`MediaPlayer.framework`)
 * to Stash's [PlayerRepository] so that:
 * 1. Currently playing track metadata, cover artwork, and progress appear in macOS Control Center / Now Playing.
 * 2. Hardware media keys (Play/Pause F8, Previous F7, Next F9), AirPods/headphone controls, and
 *    Control Center transport & scrubbing controls drive [PlayerRepository].
 */
@Singleton
class MacOsMediaControls @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val playbackEngine: PlaybackEngine,
    private val okHttpClient: OkHttpClient,
) : Closeable {

    private data class NowPlayingSnapshot(
        val trackId: Long,
        val title: String,
        val artist: String,
        val album: String,
        val artworkSource: String?,
        val durationMs: Long,
        val positionMs: Long,
        val isPlaying: Boolean,
        val speed: Float,
        val queueIndex: Int,
        val queueCount: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)
    private var observerJob: Job? = null
    private var artworkFetchJob: Job? = null

    private var objc: ObjCRuntime? = null
    private var msgSend: Function? = null
    private var mediaPlayerLib: NativeLibrary? = null
    private var targetInstance: Pointer? = null

    private var selPerformOnMain: Pointer? = null
    private var selRegisterOnMain: Pointer? = null
    private var selUpdateOnMain: Pointer? = null
    private var selCleanupOnMain: Pointer? = null

    private var selHandlePlay: Pointer? = null
    private var selHandlePause: Pointer? = null
    private var selHandleTogglePlayPause: Pointer? = null
    private var selHandleStop: Pointer? = null
    private var selHandleNext: Pointer? = null
    private var selHandlePrev: Pointer? = null
    private var selHandleSeek: Pointer? = null

    @Volatile private var latestSnapshot: NowPlayingSnapshot? = null
    @Volatile private var reclaimSessionOnUpdate: Boolean = false
    @Volatile private var lastToggleTimestampMs: Long = 0L

    @Volatile private var cachedArtworkSource: String? = null
    @Volatile private var cachedArtworkBytes: ByteArray? = null
    @Volatile private var inFlightArtworkSource: String? = null

    // Accessed only on the macOS AppKit main thread
    private var mainThreadArtworkSource: String? = null
    private var mainThreadArtworkPtr: Pointer? = null

    // Keep strong references to JNA callbacks so they are never GC'd while registered with Objective-C
    private val playCallback = object : RemoteCommandActionCallback {
        override fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long {
            scope.launch { playerRepository.play() }
            return STATUS_SUCCESS
        }
    }

    private val pauseCallback = object : RemoteCommandActionCallback {
        override fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long {
            scope.launch { playerRepository.pause() }
            return STATUS_SUCCESS
        }
    }

    private val togglePlayPauseCallback = object : RemoteCommandActionCallback {
        override fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long {
            val now = System.currentTimeMillis()
            if (now - lastToggleTimestampMs < 150L) {
                return STATUS_SUCCESS
            }
            lastToggleTimestampMs = now
            scope.launch {
                if (playerRepository.playerState.value.isPlaying) {
                    playerRepository.pause()
                } else {
                    playerRepository.play()
                }
            }
            return STATUS_SUCCESS
        }
    }

    private val stopCallback = object : RemoteCommandActionCallback {
        override fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long {
            scope.launch { playerRepository.pause() }
            return STATUS_SUCCESS
        }
    }

    private val nextCallback = object : RemoteCommandActionCallback {
        override fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long {
            scope.launch { playerRepository.skipNext() }
            return STATUS_SUCCESS
        }
    }

    private val prevCallback = object : RemoteCommandActionCallback {
        override fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long {
            scope.launch { playerRepository.skipPrevious() }
            return STATUS_SUCCESS
        }
    }

    private val seekCallback = object : RemoteCommandActionCallback {
        override fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long {
            val rt = objc ?: return STATUS_COMMAND_FAILED
            val send = msgSend ?: return STATUS_COMMAND_FAILED
            if (event == null || event == Pointer.NULL) return STATUS_COMMAND_FAILED
            return try {
                val posSec = send.invokeDouble(arrayOf(event, rt.sel_registerName("positionTime")))
                val posMs = (posSec * 1000.0).toLong().coerceAtLeast(0L)
                scope.launch { playerRepository.seekTo(posMs) }
                STATUS_SUCCESS
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to handle seek command: ${t.message}")
                STATUS_COMMAND_FAILED
            }
        }
    }

    private val registerOnMainCallback = object : MainThreadSelectorCallback {
        override fun invoke(self: Pointer?, cmd: Pointer?, arg: Pointer?) {
            runCatching { registerRemoteCommandsOnMainThread() }
                .onFailure { Log.w(TAG, "registerRemoteCommandsOnMainThread failed: ${it.message}") }
        }
    }

    private val updateOnMainCallback = object : MainThreadSelectorCallback {
        override fun invoke(self: Pointer?, cmd: Pointer?, arg: Pointer?) {
            runCatching { updateNowPlayingOnMainThread() }
                .onFailure { Log.w(TAG, "updateNowPlayingOnMainThread failed: ${it.message}") }
        }
    }

    private val cleanupOnMainCallback = object : MainThreadSelectorCallback {
        override fun invoke(self: Pointer?, cmd: Pointer?, arg: Pointer?) {
            runCatching { cleanupOnMainThread() }
                .onFailure { Log.w(TAG, "cleanupOnMainThread failed: ${it.message}") }
        }
    }

    fun start() {
        if (!Platform.isMac()) return
        if (!started.compareAndSet(false, true)) return

        try {
            val mpLib = NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer")
            NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit")
            NativeLibrary.getInstance("/System/Library/Frameworks/Foundation.framework/Foundation")
            val rt = Native.load("objc", ObjCRuntime::class.java)
            val send = Function.getFunction("objc", "objc_msgSend")

            mediaPlayerLib = mpLib
            objc = rt
            msgSend = send

            selPerformOnMain = rt.sel_registerName("performSelectorOnMainThread:withObject:waitUntilDone:")
            selRegisterOnMain = rt.sel_registerName("stashRegisterCommands:")
            selUpdateOnMain = rt.sel_registerName("stashUpdateNowPlaying:")
            selCleanupOnMain = rt.sel_registerName("stashCleanup:")

            selHandlePlay = rt.sel_registerName("stashHandlePlay:")
            selHandlePause = rt.sel_registerName("stashHandlePause:")
            selHandleTogglePlayPause = rt.sel_registerName("stashHandleTogglePlayPause:")
            selHandleStop = rt.sel_registerName("stashHandleStop:")
            selHandleNext = rt.sel_registerName("stashHandleNext:")
            selHandlePrev = rt.sel_registerName("stashHandlePrev:")
            selHandleSeek = rt.sel_registerName("stashHandleSeek:")

            var targetCls = rt.objc_getClass(TARGET_CLASS_NAME)
            if (targetCls == null) {
                val nsObjectCls = rt.objc_getClass("NSObject") ?: return
                val allocated = rt.objc_allocateClassPair(nsObjectCls, TARGET_CLASS_NAME, 0L)
                if (allocated != null) {
                    rt.class_addMethod(allocated, selRegisterOnMain!!, registerOnMainCallback, "v@:@")
                    rt.class_addMethod(allocated, selUpdateOnMain!!, updateOnMainCallback, "v@:@")
                    rt.class_addMethod(allocated, selCleanupOnMain!!, cleanupOnMainCallback, "v@:@")
                    rt.class_addMethod(allocated, selHandlePlay!!, playCallback, "q@:@")
                    rt.class_addMethod(allocated, selHandlePause!!, pauseCallback, "q@:@")
                    rt.class_addMethod(allocated, selHandleTogglePlayPause!!, togglePlayPauseCallback, "q@:@")
                    rt.class_addMethod(allocated, selHandleStop!!, stopCallback, "q@:@")
                    rt.class_addMethod(allocated, selHandleNext!!, nextCallback, "q@:@")
                    rt.class_addMethod(allocated, selHandlePrev!!, prevCallback, "q@:@")
                    rt.class_addMethod(allocated, selHandleSeek!!, seekCallback, "q@:@")
                    rt.objc_registerClassPair(allocated)
                    targetCls = allocated
                }
            }

            if (targetCls == null) {
                Log.w(TAG, "Unable to allocate Objective-C class $TARGET_CLASS_NAME")
                return
            }

            val allocObj = send.invokePointer(arrayOf(targetCls, rt.sel_registerName("alloc")))
            val instance = send.invokePointer(arrayOf(allocObj, rt.sel_registerName("init")))
            targetInstance = instance

            dispatchToMainThread(selRegisterOnMain, waitUntilDone = false)
            startObservingPlayerState()
            Log.i(TAG, "macOS Now Playing and MPRemoteCommandCenter initialized")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize macOS media controls: ${t.message}")
        }
    }

    private fun resolveArtworkSource(track: Track): String? {
        track.albumArtPath?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        track.albumArtUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        track.youtubeId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return "https://i.ytimg.com/vi/$it/hqdefault.jpg"
        }
        return null
    }

    private fun ensureArtworkLoadedAsync(source: String?) {
        if (source.isNullOrBlank()) {
            artworkFetchJob?.cancel()
            inFlightArtworkSource = null
            cachedArtworkSource = null
            cachedArtworkBytes = null
            return
        }
        if (source == cachedArtworkSource || source == inFlightArtworkSource) {
            return
        }
        artworkFetchJob?.cancel()
        inFlightArtworkSource = source
        artworkFetchJob = scope.launch(Dispatchers.IO) {
            val bytes = runCatching { fetchArtworkBytes(source) }.getOrNull()
            if (!isActive) return@launch
            if (bytes != null && bytes.isNotEmpty()) {
                cachedArtworkSource = source
                cachedArtworkBytes = bytes
                inFlightArtworkSource = null
                dispatchToMainThread(selUpdateOnMain, waitUntilDone = false)
            } else {
                inFlightArtworkSource = null
            }
        }
    }

    private fun fetchArtworkBytes(source: String): ByteArray? {
        if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) {
            val req = Request.Builder().url(source).build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                return resp.body?.bytes()
            }
        }
        val cleanPath = if (source.startsWith("file:")) {
            val stripped = source.substringBefore('?')
            runCatching { File(URI(stripped)).absolutePath }
                .getOrElse { stripped.removePrefix("file://").removePrefix("file:") }
        } else {
            source
        }
        val file = File(cleanPath)
        return if (file.isFile) file.readBytes() else null
    }

    private fun startObservingPlayerState() {
        observerJob?.cancel()
        observerJob = scope.launch {
            var lastReportedSnapshot: NowPlayingSnapshot? = null
            var lastReportedWallTimeMs = 0L

            while (isActive) {
                val state = playerRepository.playerState.value
                val track = state.currentTrack
                val speed = playerRepository.playbackSpeed.value
                val enginePos = playbackEngine.currentPositionMs
                val engineDur = playbackEngine.durationMs

                val posMs = if (enginePos > 0L) enginePos else state.positionMs
                val durMs = when {
                    engineDur > 0L -> engineDur
                    state.durationMs > 0L -> state.durationMs
                    track != null && track.durationMs > 0L -> track.durationMs
                    else -> 0L
                }

                if (track == null) {
                    ensureArtworkLoadedAsync(null)
                    if (lastReportedSnapshot != null) {
                        lastReportedSnapshot = null
                        latestSnapshot = null
                        dispatchToMainThread(selUpdateOnMain, waitUntilDone = false)
                    }
                } else {
                    val artSource = resolveArtworkSource(track)
                    ensureArtworkLoadedAsync(artSource)

                    val snapshot = NowPlayingSnapshot(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        artworkSource = artSource,
                        durationMs = durMs,
                        positionMs = posMs,
                        isPlaying = state.isPlaying,
                        speed = speed,
                        queueIndex = state.currentIndex.coerceAtLeast(0),
                        queueCount = state.queue.size.coerceAtLeast(1),
                    )

                    val now = System.currentTimeMillis()
                    val prev = lastReportedSnapshot
                    val trackChanged = prev == null ||
                        prev.trackId != snapshot.trackId ||
                        prev.title != snapshot.title ||
                        prev.artist != snapshot.artist ||
                        prev.album != snapshot.album ||
                        prev.artworkSource != snapshot.artworkSource
                    val playStateChanged = prev == null || prev.isPlaying != snapshot.isPlaying
                    val durationChanged = prev == null ||
                        (snapshot.durationMs > 0L && abs(prev.durationMs - snapshot.durationMs) > 500L)
                    val speedChanged = prev == null || prev.speed != snapshot.speed

                    val elapsedSinceLastReportMs = (now - lastReportedWallTimeMs).coerceAtLeast(0L)
                    val expectedPosMs = if (prev != null && prev.isPlaying) {
                        prev.positionMs + (elapsedSinceLastReportMs * prev.speed).toLong()
                    } else {
                        prev?.positionMs ?: 0L
                    }
                    val positionJumped = prev != null && abs(snapshot.positionMs - expectedPosMs) > 2000L

                    if (trackChanged || playStateChanged || durationChanged || speedChanged || positionJumped) {
                        if (trackChanged || (playStateChanged && snapshot.isPlaying)) {
                            reclaimSessionOnUpdate = true
                        }
                        lastReportedSnapshot = snapshot
                        lastReportedWallTimeMs = now
                        latestSnapshot = snapshot
                        dispatchToMainThread(selUpdateOnMain, waitUntilDone = false)
                    }
                }

                delay(250L)
            }
        }
    }

    private fun dispatchToMainThread(selector: Pointer?, waitUntilDone: Boolean) {
        val send = msgSend ?: return
        val target = targetInstance ?: return
        val performSel = selPerformOnMain ?: return
        if (selector == null) return
        send.invokeVoid(arrayOf(target, performSel, selector, Pointer.NULL, waitUntilDone))
    }

    private fun registerRemoteCommandsOnMainThread() {
        val rt = objc ?: return
        val send = msgSend ?: return
        val target = targetInstance ?: return

        val rccClass = rt.objc_getClass("MPRemoteCommandCenter") ?: return
        val rcc = send.invokePointer(arrayOf(rccClass, rt.sel_registerName("sharedCommandCenter"))) ?: return

        val selSetEnabled = rt.sel_registerName("setEnabled:")
        val selRemoveTarget = rt.sel_registerName("removeTarget:")
        val selAddTargetAction = rt.sel_registerName("addTarget:action:")

        fun bindCommand(commandGetter: String, actionSel: Pointer?) {
            if (actionSel == null) return
            val cmd = send.invokePointer(arrayOf(rcc, rt.sel_registerName(commandGetter))) ?: return
            send.invokeVoid(arrayOf(cmd, selRemoveTarget, Pointer.NULL))
            send.invokeVoid(arrayOf(cmd, selSetEnabled, true))
            send.invokeVoid(arrayOf(cmd, selAddTargetAction, target, actionSel))
        }

        fun disableCommand(commandGetter: String) {
            val cmd = send.invokePointer(arrayOf(rcc, rt.sel_registerName(commandGetter))) ?: return
            send.invokeVoid(arrayOf(cmd, selRemoveTarget, Pointer.NULL))
            send.invokeVoid(arrayOf(cmd, selSetEnabled, false))
        }

        bindCommand("playCommand", selHandlePlay)
        bindCommand("pauseCommand", selHandlePause)
        bindCommand("togglePlayPauseCommand", selHandleTogglePlayPause)
        bindCommand("stopCommand", selHandleStop)
        bindCommand("nextTrackCommand", selHandleNext)
        bindCommand("previousTrackCommand", selHandlePrev)
        bindCommand("changePlaybackPositionCommand", selHandleSeek)

        // Disable podcast/rating commands so macOS Control Center displays music Next/Prev buttons
        disableCommand("skipForwardCommand")
        disableCommand("skipBackwardCommand")
        disableCommand("seekForwardCommand")
        disableCommand("seekBackwardCommand")
        disableCommand("ratingCommand")
        disableCommand("likeCommand")
        disableCommand("dislikeCommand")
        disableCommand("bookmarkCommand")
    }

    private fun releaseMainThreadArtwork() {
        val rt = objc ?: return
        val send = msgSend ?: return
        val ptr = mainThreadArtworkPtr
        if (ptr != null && ptr != Pointer.NULL) {
            send.invokeVoid(arrayOf(ptr, rt.sel_registerName("release")))
        }
        mainThreadArtworkPtr = null
        mainThreadArtworkSource = null
    }

    private fun getOrCreateMainThreadArtwork(desiredSource: String?): Pointer? {
        if (desiredSource.isNullOrBlank()) {
            releaseMainThreadArtwork()
            return null
        }
        if (desiredSource == mainThreadArtworkSource && mainThreadArtworkPtr != null) {
            return mainThreadArtworkPtr
        }
        val bytesSource = cachedArtworkSource
        val bytes = cachedArtworkBytes
        if (bytesSource != desiredSource || bytes == null || bytes.isEmpty()) {
            // Bytes for this track are still downloading; clear stale artwork from previous track
            releaseMainThreadArtwork()
            return null
        }

        releaseMainThreadArtwork()

        val rt = objc ?: return null
        val send = msgSend ?: return null

        val mem = Memory(bytes.size.toLong())
        mem.write(0L, bytes, 0, bytes.size)

        val nsDataCls = rt.objc_getClass("NSData") ?: return null
        val nsData = send.invokePointer(
            arrayOf(nsDataCls, rt.sel_registerName("dataWithBytes:length:"), mem, bytes.size.toLong())
        ) ?: return null

        val nsImageCls = rt.objc_getClass("NSImage") ?: return null
        val imgAlloc = send.invokePointer(arrayOf(nsImageCls, rt.sel_registerName("alloc"))) ?: return null
        val nsImage = send.invokePointer(arrayOf(imgAlloc, rt.sel_registerName("initWithData:"), nsData))
        if (nsImage == null || nsImage == Pointer.NULL) {
            return null
        }

        val artworkCls = rt.objc_getClass("MPMediaItemArtwork") ?: run {
            send.invokeVoid(arrayOf(nsImage, rt.sel_registerName("release")))
            return null
        }
        val artworkAlloc = send.invokePointer(arrayOf(artworkCls, rt.sel_registerName("alloc"))) ?: run {
            send.invokeVoid(arrayOf(nsImage, rt.sel_registerName("release")))
            return null
        }
        val artwork = send.invokePointer(arrayOf(artworkAlloc, rt.sel_registerName("initWithImage:"), nsImage))
        // MPMediaItemArtwork retains nsImage; release our alloc reference
        send.invokeVoid(arrayOf(nsImage, rt.sel_registerName("release")))

        if (artwork == null || artwork == Pointer.NULL) {
            return null
        }

        mainThreadArtworkSource = desiredSource
        mainThreadArtworkPtr = artwork
        return artwork
    }

    private fun updateNowPlayingOnMainThread() {
        val rt = objc ?: return
        val send = msgSend ?: return
        val mpLib = mediaPlayerLib ?: return

        val npicClass = rt.objc_getClass("MPNowPlayingInfoCenter") ?: return
        val npic = send.invokePointer(arrayOf(npicClass, rt.sel_registerName("defaultCenter"))) ?: return
        val selSetNowPlayingInfo = rt.sel_registerName("setNowPlayingInfo:")
        val selSetPlaybackState = rt.sel_registerName("setPlaybackState:")

        val snapshot = latestSnapshot
        if (snapshot == null) {
            releaseMainThreadArtwork()
            send.invokeVoid(arrayOf(npic, selSetNowPlayingInfo, Pointer.NULL))
            send.invokeVoid(arrayOf(npic, selSetPlaybackState, PLAYBACK_STATE_STOPPED))
            return
        }

        val dictClass = rt.objc_getClass("NSMutableDictionary") ?: return
        val dict = send.invokePointer(arrayOf(dictClass, rt.sel_registerName("dictionary"))) ?: return
        val selSetObject = rt.sel_registerName("setObject:forKey:")

        val nsStringClass = rt.objc_getClass("NSString") ?: return
        val selStringWithUTF8 = rt.sel_registerName("stringWithUTF8String:")

        val nsNumberClass = rt.objc_getClass("NSNumber") ?: return
        val selNumberWithDouble = rt.sel_registerName("numberWithDouble:")
        val selNumberWithULong = rt.sel_registerName("numberWithUnsignedLong:")

        fun putString(globalKeyName: String, value: String) {
            if (value.isBlank()) return
            val keyPtr = runCatching { mpLib.getGlobalVariableAddress(globalKeyName).getPointer(0) }.getOrNull() ?: return
            val strPtr = send.invokePointer(arrayOf(nsStringClass, selStringWithUTF8, value)) ?: return
            send.invokeVoid(arrayOf(dict, selSetObject, strPtr, keyPtr))
        }

        fun putDouble(globalKeyName: String, value: Double) {
            val keyPtr = runCatching { mpLib.getGlobalVariableAddress(globalKeyName).getPointer(0) }.getOrNull() ?: return
            val numPtr = send.invokePointer(arrayOf(nsNumberClass, selNumberWithDouble, value)) ?: return
            send.invokeVoid(arrayOf(dict, selSetObject, numPtr, keyPtr))
        }

        fun putULong(globalKeyName: String, value: Long) {
            val keyPtr = runCatching { mpLib.getGlobalVariableAddress(globalKeyName).getPointer(0) }.getOrNull() ?: return
            val numPtr = send.invokePointer(arrayOf(nsNumberClass, selNumberWithULong, value)) ?: return
            send.invokeVoid(arrayOf(dict, selSetObject, numPtr, keyPtr))
        }

        putString("MPMediaItemPropertyTitle", snapshot.title)
        putString("MPMediaItemPropertyArtist", snapshot.artist)
        putString("MPMediaItemPropertyAlbumTitle", snapshot.album)
        if (snapshot.durationMs > 0L) {
            putDouble("MPMediaItemPropertyPlaybackDuration", snapshot.durationMs / 1000.0)
        }
        putDouble("MPNowPlayingInfoPropertyElapsedPlaybackTime", snapshot.positionMs / 1000.0)
        putDouble("MPNowPlayingInfoPropertyPlaybackRate", if (snapshot.isPlaying) snapshot.speed.toDouble() else 0.0)
        putDouble("MPNowPlayingInfoPropertyDefaultPlaybackRate", 1.0)
        putULong("MPNowPlayingInfoPropertyMediaType", MEDIA_TYPE_AUDIO)
        putULong("MPNowPlayingInfoPropertyPlaybackQueueIndex", snapshot.queueIndex.toLong())
        putULong("MPNowPlayingInfoPropertyPlaybackQueueCount", snapshot.queueCount.toLong())

        val artworkPtr = getOrCreateMainThreadArtwork(snapshot.artworkSource)
        if (artworkPtr != null && artworkPtr != Pointer.NULL) {
            val artworkKey = runCatching {
                mpLib.getGlobalVariableAddress("MPMediaItemPropertyArtwork").getPointer(0)
            }.getOrNull()
            if (artworkKey != null) {
                send.invokeVoid(arrayOf(dict, selSetObject, artworkPtr, artworkKey))
            }
        }

        if (snapshot.isPlaying) {
            if (reclaimSessionOnUpdate) {
                reclaimSessionOnUpdate = false
                registerRemoteCommandsOnMainThread()
                // Toggling Paused -> Playing ensures macOS mediaremoted claims the active session
                send.invokeVoid(arrayOf(npic, selSetPlaybackState, PLAYBACK_STATE_PAUSED))
            }
            send.invokeVoid(arrayOf(npic, selSetPlaybackState, PLAYBACK_STATE_PLAYING))
        } else {
            send.invokeVoid(arrayOf(npic, selSetPlaybackState, PLAYBACK_STATE_PAUSED))
        }

        send.invokeVoid(arrayOf(npic, selSetNowPlayingInfo, dict))
    }

    private fun cleanupOnMainThread() {
        releaseMainThreadArtwork()

        val rt = objc ?: return
        val send = msgSend ?: return
        val target = targetInstance ?: return

        val rccClass = rt.objc_getClass("MPRemoteCommandCenter")
        val rcc = rccClass?.let { send.invokePointer(arrayOf(it, rt.sel_registerName("sharedCommandCenter"))) }
        if (rcc != null) {
            val selRemoveTarget = rt.sel_registerName("removeTarget:")
            val commands = listOf(
                "playCommand",
                "pauseCommand",
                "togglePlayPauseCommand",
                "stopCommand",
                "nextTrackCommand",
                "previousTrackCommand",
                "changePlaybackPositionCommand",
            )
            for (cmdName in commands) {
                val cmd = send.invokePointer(arrayOf(rcc, rt.sel_registerName(cmdName)))
                if (cmd != null) {
                    send.invokeVoid(arrayOf(cmd, selRemoveTarget, target))
                }
            }
        }

        val npicClass = rt.objc_getClass("MPNowPlayingInfoCenter")
        val npic = npicClass?.let { send.invokePointer(arrayOf(it, rt.sel_registerName("defaultCenter"))) }
        if (npic != null) {
            send.invokeVoid(arrayOf(npic, rt.sel_registerName("setNowPlayingInfo:"), Pointer.NULL))
            send.invokeVoid(arrayOf(npic, rt.sel_registerName("setPlaybackState:"), PLAYBACK_STATE_STOPPED))
        }
    }

    override fun close() {
        observerJob?.cancel()
        observerJob = null
        artworkFetchJob?.cancel()
        artworkFetchJob = null
        if (started.compareAndSet(true, false)) {
            runCatching { dispatchToMainThread(selCleanupOnMain, waitUntilDone = true) }
        }
    }

    companion object {
        private const val TAG = "MacOsMediaControls"
        private const val TARGET_CLASS_NAME = "StashMacOsRemoteCommandTarget"

        private const val STATUS_SUCCESS = 0L
        private const val STATUS_COMMAND_FAILED = 200L

        private const val MEDIA_TYPE_AUDIO = 1L
        private const val PLAYBACK_STATE_PLAYING = 1L
        private const val PLAYBACK_STATE_PAUSED = 2L
        private const val PLAYBACK_STATE_STOPPED = 3L
    }
}
