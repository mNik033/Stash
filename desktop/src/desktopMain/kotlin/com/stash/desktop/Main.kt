package com.stash.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.stash.core.ui.theme.StashTheme
import com.stash.desktop.di.DaggerDesktopComponent
import com.stash.desktop.ui.DesktopScaffold
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.WString

private interface WindowsShell32 : Library {
    fun SetCurrentProcessExplicitAppUserModelID(appID: WString): Int

    companion object {
        val INSTANCE: WindowsShell32? = try {
            if (Platform.isWindows()) {
                Native.load("shell32", WindowsShell32::class.java)
            } else null
        } catch (_: Throwable) { null }
    }
}

fun main() {
    if (Platform.isWindows()) {
        try {
            WindowsShell32.INSTANCE?.SetCurrentProcessExplicitAppUserModelID(WString("Stash.MusicPlayer"))
        } catch (_: Throwable) {
            // Ignore if not supported
        }
    }

    application {
        val component = remember { DaggerDesktopComponent.create() }
        var isDarkTheme by remember { mutableStateOf(true) }
        var isAmoledTheme by remember { mutableStateOf(false) }

        Window(
            onCloseRequest = {
                runCatching { component.mpvPlaybackEngine().close() }
                exitApplication()
            },
            title = "Stash",
            state = remember { WindowState(width = 1200.dp, height = 800.dp) },
        ) {
            val hwnd = remember {
                if (Platform.isWindows()) {
                    try {
                        Native.getWindowID(this.window)
                    } catch (_: Throwable) { 0L }
                } else {
                    0L
                }
            }
            LaunchedEffect(hwnd) {
                if (hwnd != 0L && Platform.isWindows()) {
                    component.mpvPlaybackEngine().attachWindowHandle(hwnd)
                }
            }

            StashTheme(
                darkTheme = isDarkTheme,
                amoled = isAmoledTheme,
            ) {
                DesktopScaffold(
                    component = component,
                    isDarkTheme = isDarkTheme,
                    isAmoledTheme = isAmoledTheme,
                    onThemeChanged = { dark, amoled ->
                        isDarkTheme = dark
                        isAmoledTheme = amoled
                    },
                )
            }
        }
    }
}
