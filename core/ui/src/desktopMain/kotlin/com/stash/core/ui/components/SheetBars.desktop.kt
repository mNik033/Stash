package com.stash.core.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
actual fun sheetBarsFollow(containerColor: Color): ModalBottomSheetProperties =
    ModalBottomSheetProperties()

@Composable
actual fun SheetNavigationBarUnscrimmed() {
    // Desktop has no navigation bar scrim
}

