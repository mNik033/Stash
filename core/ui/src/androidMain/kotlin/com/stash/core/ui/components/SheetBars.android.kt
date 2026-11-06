package com.stash.core.ui.components

import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@OptIn(ExperimentalMaterial3Api::class)
actual fun sheetBarsFollow(containerColor: Color): ModalBottomSheetProperties {
    val light = lightBarsOn(containerColor)
    return ModalBottomSheetProperties(isAppearanceLightStatusBars = light, isAppearanceLightNavigationBars = light)
}

@Composable
actual fun SheetNavigationBarUnscrimmed() {
    val view = LocalView.current
    DisposableEffect(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val window = generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
            window?.isNavigationBarContrastEnforced = false
        }
        onDispose { }
    }
}

