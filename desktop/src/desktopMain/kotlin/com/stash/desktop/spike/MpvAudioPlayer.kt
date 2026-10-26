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
        val localAppData = System.getenv("LOCALAPPDATA")?.replace('\\', '/') ?: "$userHome/AppData/Local"
        val explicitPath = System.getProperty("stash.mpv.path")
        val resourcesDir = System.getProperty("compose.application.resources.dir")

        val candidatePaths = mutableListOf<String>()

        if (!explicitPath.isNullOrBlank()) {
            candidatePaths.add(explicitPath)
        }

        if (!resourcesDir.isNullOrBlank()) {
            when {
                Platform.isWindows() -> {
                    candidatePaths.add("$resourcesDir/libmpv-2.dll")
                    candidatePaths.add("$resourcesDir/mpv-2.dll")
                }
                Platform.isMac() -> {
                    candidatePaths.add("$resourcesDir/libmpv.dylib")
                }
                else -> {
                    candidatePaths.add("$resourcesDir/libmpv.so.2")
                    candidatePaths.add("$resourcesDir/libmpv.so")
                }
            }
        }

        when {
            Platform.isWindows() -> {
                candidatePaths.add("$localAppData/Stash/bin/libmpv-2.dll")
                candidatePaths.add("$localAppData/Stash/bin/mpv-2.dll")
                candidatePaths.add("$userHome/.stash/bin/libmpv-2.dll")
                candidatePaths.add("$userHome/.stash/bin/mpv-2.dll")
            }
            Platform.isMac() -> {
                candidatePaths.add("/opt/homebrew/lib/libmpv.dylib")
                candidatePaths.add("/usr/local/lib/libmpv.dylib")
            }
            else -> {
                candidatePaths.add("/usr/lib/x86_64-linux-gnu/libmpv.so.2")
                candidatePaths.add("/usr/lib/libmpv.so.2")
                candidatePaths.add("/usr/local/lib/libmpv.so.2")
                candidatePaths.add("/usr/lib/libmpv.so")
            }
        }

        var loadedLib: MpvLib? = null
        var loadedName: String? = null

        // Try absolute files first
        for (path in candidatePaths) {
            val file = File(path)
            if (file.isFile) {
                try {
                    loadedLib = Native.load(file.absolutePath, MpvLib::class.java)
                    loadedName = file.absolutePath
                    println("MpvBinding: successfully loaded native library from '$loadedName'")
                    break
                } catch (e: UnsatisfiedLinkError) {
                    println("MpvBinding: candidate '$path' unsatisfied link: ${e.message}")
                } catch (t: Throwable) {
                    println("MpvBinding: candidate '$path' threw: ${t.message}")
                }
            }
        }

        // On Linux / macOS, if not loaded from absolute path, fallback to system linker names
        if (loadedLib == null && !Platform.isWindows()) {
            val fallbackNames = if (Platform.isMac()) {
                listOf("mpv.2", "mpv", "libmpv.2", "libmpv")
            } else {
                listOf("libmpv.so.2", "libmpv.so", "mpv")
            }
            for (name in fallbackNames) {
                try {
                    loadedLib = Native.load(name, MpvLib::class.java)
                    loadedName = name
                    println("MpvBinding: successfully loaded native library from fallback '$name'")
                    break
                } catch (e: UnsatisfiedLinkError) {
                    println("MpvBinding: fallback '$name' unsatisfied link: ${e.message}")
                } catch (t: Throwable) {
                    println("MpvBinding: fallback '$name' threw: ${t.message}")
                }
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
                lib.mpv_set_option_string(h, "config", "no")
                lib.mpv_set_option_string(h, "load-scripts", "no")
                lib.mpv_set_option_string(h, "ytdl", "no")
                lib.mpv_set_option_string(h, "load-unsafe-playlists", "no")
                lib.mpv_set_option_string(h, "vo", "null") // Headless / audio only
                lib.mpv_set_option_string(h, "terminal", "no")
                val status = lib.mpv_initialize(h)
                if (status >= 0) {
                    handle = h
                    statusMessage = "libmpv initialized via '${MpvBinding.libraryName}'"
                } else {
                    val err = lib.mpv_error_string(status)
                    lib.mpv_terminate_destroy(h)
                    statusMessage = "libmpv initialize failed: $err"
                }
            } else {
                statusMessage = "mpv_create returned null handle"
            }
        } else {
            val hint = if (Platform.isMac()) {
                "Run 'brew install mpv' or package libmpv.dylib in app bundle"
            } else {
                "Place 'libmpv-2.dll' in %LOCALAPPDATA%\\Stash\\bin\\ or configure -Dstash.mpv.path"
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
