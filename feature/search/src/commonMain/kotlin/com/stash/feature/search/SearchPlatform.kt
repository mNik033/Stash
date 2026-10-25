package com.stash.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun searchViewModel(): SearchViewModel

@Composable
expect fun artistProfileViewModel(): ArtistProfileViewModel

@Composable
expect fun albumDiscoveryViewModel(): AlbumDiscoveryViewModel

@Composable
expect fun rememberSocialPainter(kind: String): Painter?

@Composable
expect fun StatusBarIcons(light: Boolean)
