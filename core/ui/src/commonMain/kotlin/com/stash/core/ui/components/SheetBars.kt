package com.stash.core.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * A ModalBottomSheet's window properties with its status and navigation bars following the sheet's
 * own [containerColor]: light bars (dark icons, a light navigation bar scrim) on a light sheet, dark
 * ones on a dark sheet. For a sheet on the app's elevatedSurface, which isn't one of the scheme's
 * sheet surfaces.
 */
@OptIn(ExperimentalMaterial3Api::class)
expect fun sheetBarsFollow(containerColor: Color): ModalBottomSheetProperties

/** A sheet of [containerColor] takes light bars: it is light, by Material's own cut (luminance over 0.5). */
internal fun lightBarsOn(containerColor: Color): Boolean = containerColor.luminance() > 0.5f

/**
 * Call first inside a ModalBottomSheet's content: the sheet's own colour under the navigation bar, with no system scrim
 * over it.
 */
@Composable
expect fun SheetNavigationBarUnscrimmed()
