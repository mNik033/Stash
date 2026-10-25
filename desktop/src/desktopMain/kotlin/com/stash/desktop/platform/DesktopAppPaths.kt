package com.stash.desktop.platform

import com.stash.core.common.platform.AppPaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopAppPaths @Inject constructor() : AppPaths {

    private val os = System.getProperty("os.name").lowercase()
    private val userHome = System.getProperty("user.home") ?: "."

    override val filesDir: File by lazy {
        val dir = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
                File(appData, "Stash/data")
            }
            os.contains("mac") -> {
                File(userHome, "Library/Application Support/Stash/data")
            }
            else -> {
                val xdgData = System.getenv("XDG_DATA_HOME") ?: "$userHome/.local/share"
                File(xdgData, "stash/data")
            }
        }
        dir.apply { mkdirs() }
    }

    override val cacheDir: File by lazy {
        val dir = when {
            os.contains("win") -> {
                val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome/AppData/Local"
                File(localAppData, "Stash/cache")
            }
            os.contains("mac") -> {
                File(userHome, "Library/Caches/Stash")
            }
            else -> {
                val xdgCache = System.getenv("XDG_CACHE_HOME") ?: "$userHome/.cache"
                File(xdgCache, "stash")
            }
        }
        dir.apply { mkdirs() }
    }

    override val noBackupFilesDir: File by lazy {
        val dir = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
                File(appData, "Stash/secure")
            }
            os.contains("mac") -> {
                File(userHome, "Library/Application Support/Stash/secure")
            }
            else -> {
                val xdgData = System.getenv("XDG_DATA_HOME") ?: "$userHome/.local/share"
                File(xdgData, "stash/secure")
            }
        }
        dir.apply { mkdirs() }
    }

    override fun getDatabasePath(name: String): File {
        val dbDir = File(filesDir, "databases").apply { mkdirs() }
        return File(dbDir, name)
    }
}
