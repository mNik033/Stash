package com.stash.desktop

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

fun main() = application {
    val component = remember { DaggerDesktopComponent.create() }
    var isDarkTheme by remember { mutableStateOf(true) }
    var isAmoledTheme by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Stash",
        state = remember { WindowState(width = 1200.dp, height = 800.dp) },
    ) {
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
