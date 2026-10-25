package com.stash.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

actual val SpaceGrotesk: FontFamily = FontFamily(
    Font("font/space_grotesk_semibold.ttf", FontWeight.SemiBold),
    Font("font/space_grotesk_bold.ttf", FontWeight.Bold),
)

actual val Inter: FontFamily = FontFamily(
    Font("font/inter_regular.ttf", FontWeight.Normal),
    Font("font/inter_medium.ttf", FontWeight.Medium),
    Font("font/inter_semibold.ttf", FontWeight.SemiBold),
)
