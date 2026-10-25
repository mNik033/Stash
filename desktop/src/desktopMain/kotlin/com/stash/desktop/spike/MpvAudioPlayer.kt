package com.stash.desktop.spike

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import java.io.File

interface MpvLib : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_command(handle: Pointer, args: Array<String?>): Int
    fun mpv_command_string(handle: Pointer, args: String): Int
    fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int
    fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int
    fun mpv_get_property_string(handle: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer?)
    fun mpv_terminate_destroy(handle: Pointer)
    fun mpv_error_string(error: Int): String
}

object MpvBinding {
    val libraryName: String?
    val instance: MpvLib?

    init {
        val userHome = System.getProperty("user.home")?.replace('\\', '/') ?: ""
        val candidateNames = when {
            Platform.isMac() -> listOf(
                "/opt/homebrew/lib/libmpv.dylib",
                "/usr/local/lib/libmpv.dylib",
                "mpv.2",
                "mpv",
                "libmpv.2",
                "libmpv"
            )
            Platform.isWindows() -> listOf(
                "$userHome/.stash/bin/libmpv-2.dll",
                "$userHome/.stash/bin/mpv-2.dll",
                "libmpv-2",
                "mpv-2",
                "mpv"
            )
            else -> listOf("mpv", "libmpv.so.2", "libmpv.so")
        }

        var loadedLib: MpvLib? = null
        var loadedName: String? = null

        for (name in candidateNames) {
            try {
                loadedLib = Native.load(name, MpvLib::class.java)
                loadedName = name
                println("MpvBinding: successfully loaded native library from '$name'")
                break
            } catch (e: UnsatisfiedLinkError) {
                // Try next candidate
                println("MpvBinding: candidate '$name' unsatisfied link: ${e.message}")
            } catch (t: Throwable) {
                println("MpvBinding: candidate '$name' threw: ${t.message}")
            }
        }

        instance = loadedLib
        libraryName = loadedName
    }
}

class MpvAudioPlayer {
    private var handle: Pointer? = null

    val isAvailable: Boolean
        get() = handle != null

    val statusMessage: String

    init {
        val lib = MpvBinding.instance
        if (lib != null) {
            val h = lib.mpv_create()
            if (h != null) {
                lib.mpv_set_option_string(h, "vo", "null") // Headless / audio only
                lib.mpv_set_option_string(h, "terminal", "no")
                val status = lib.mpv_initialize(h)
                if (status >= 0) {
                    handle = h
                    statusMessage = "libmpv initialized via '${MpvBinding.libraryName}'"
                } else {
                    val err = lib.mpv_error_string(status)
                    statusMessage = "libmpv initialize failed: $err"
                }
            } else {
                statusMessage = "mpv_create returned null handle"
            }
        } else {
            val hint = if (Platform.isMac()) {
                "Run 'brew install mpv' or package libmpv.dylib in app bundle"
            } else {
                "Place 'mpv-2.dll' in PATH or application directory"
            }
            statusMessage = "libmpv native library not found. ($hint)"
        }
    }

    fun play(pathOrUrl: String): Result<Unit> {
        val h = handle ?: return Result.failure(IllegalStateException("libmpv not initialized"))
        val lib = MpvBinding.instance ?: return Result.failure(IllegalStateException("libmpv not loaded"))
        val code = lib.mpv_command(h, arrayOf("loadfile", pathOrUrl, null))
        return if (code >= 0) {
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException("mpv loadfile error: ${lib.mpv_error_string(code)}"))
        }
    }

    fun setPause(paused: Boolean) {
        val h = handle ?: return
        MpvBinding.instance?.mpv_set_property_string(h, "pause", if (paused) "yes" else "no")
    }

    fun setVolume(volume: Int) { // 0 - 100
        val h = handle ?: return
        MpvBinding.instance?.mpv_set_property_string(h, "volume", volume.coerceIn(0, 100).toString())
    }

    fun stop() {
        val h = handle ?: return
        MpvBinding.instance?.mpv_command(h, arrayOf("stop", null))
    }

    fun close() {
        handle?.let { MpvBinding.instance?.mpv_terminate_destroy(it) }
        handle = null
    }
}
