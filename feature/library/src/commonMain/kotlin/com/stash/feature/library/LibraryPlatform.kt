package com.stash.feature.library

import androidx.compose.runtime.Composable
import com.stash.core.model.Track
import com.stash.feature.library.allsongs.AllSongsViewModel
import com.stash.feature.library.mixbuilder.MixBuilderViewModel
import com.stash.feature.library.share.ShareMixViewModel
import com.stash.feature.library.share.SharedMixViewModel
import com.stash.feature.library.share.SharedTrackViewModel

@Composable
expect fun libraryViewModel(): LibraryViewModel

@Composable
expect fun albumDetailViewModel(): AlbumDetailViewModel

@Composable
expect fun artistDetailViewModel(): ArtistDetailViewModel

@Composable
expect fun likedSongsDetailViewModel(): LikedSongsDetailViewModel

@Composable
expect fun playlistDetailViewModel(): PlaylistDetailViewModel

@Composable
expect fun allSongsViewModel(): AllSongsViewModel

@Composable
expect fun mixBuilderViewModel(): MixBuilderViewModel

@Composable
expect fun sharedMixViewModel(): SharedMixViewModel

@Composable
expect fun sharedTrackViewModel(): SharedTrackViewModel

@Composable
expect fun shareMixViewModel(playlistId: Long): ShareMixViewModel

@Composable
expect fun rememberMultipleAudioPicker(onAudioPicked: (List<String>) -> Unit): () -> Unit

@Composable
expect fun rememberImagePicker(onImagePicked: (String) -> Unit): () -> Unit

@Composable
expect fun rememberFolderPicker(onFolderPicked: (String) -> Unit): () -> Unit

@Composable
expect fun rememberSongFileSharer(): (List<Track>) -> Boolean

@Composable
expect fun rememberAvailableBytes(): Long
