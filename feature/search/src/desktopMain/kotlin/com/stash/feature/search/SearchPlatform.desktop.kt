package com.stash.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import com.stash.data.ytmusic.model.MoodTile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DesktopEmptyMoodBrowseViewModel : MoodBrowseViewModel {
    override val state: StateFlow<MoodBrowseState> = MutableStateFlow(MoodBrowseState(online = false, loaded = true)).asStateFlow()
    override fun onResume() {}
    override fun onTilesVisible(tiles: List<MoodTile>) {}
}

val LocalSearchViewModel = staticCompositionLocalOf<SearchViewModel?> { null }
val LocalArtistProfileViewModel = staticCompositionLocalOf<ArtistProfileViewModel?> { null }
val LocalAlbumDiscoveryViewModel = staticCompositionLocalOf<AlbumDiscoveryViewModel?> { null }
val LocalMoodBrowseViewModel = staticCompositionLocalOf<MoodBrowseViewModel?> { null }

@Composable
actual fun searchViewModel(): SearchViewModel =
    LocalSearchViewModel.current ?: error("LocalSearchViewModel not provided")

@Composable
actual fun artistProfileViewModel(): ArtistProfileViewModel =
    LocalArtistProfileViewModel.current ?: error("LocalArtistProfileViewModel not provided")

@Composable
actual fun albumDiscoveryViewModel(): AlbumDiscoveryViewModel =
    LocalAlbumDiscoveryViewModel.current ?: error("LocalAlbumDiscoveryViewModel not provided")

@Composable
actual fun moodBrowseViewModel(): MoodBrowseViewModel =
    LocalMoodBrowseViewModel.current ?: DesktopEmptyMoodBrowseViewModel

@Composable
actual fun rememberSocialPainter(kind: String): Painter? = null

@Composable
actual fun StatusBarIcons(light: Boolean) {
    // Desktop has no status bar
}

@Composable
actual fun monthlyListenersLabel(count: Long?): String {
    if (count == null) return ""
    val locale = java.util.Locale.getDefault()
    val short = androidx.compose.runtime.remember(count, locale) { shortMonthlyListeners(count, locale) }
    return "${short.text} monthly listeners"
}

internal actual fun shortMonthlyListeners(count: Long, locale: java.util.Locale): ShortCount {
    val format = java.text.NumberFormat.getCompactNumberInstance(locale, java.text.NumberFormat.Style.SHORT)
    format.maximumFractionDigits = 1
    return ShortCount(text = format.format(count), shown = count)
}
