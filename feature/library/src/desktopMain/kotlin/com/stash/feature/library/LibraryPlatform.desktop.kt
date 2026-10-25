package com.stash.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.stash.core.model.Track
import com.stash.feature.library.allsongs.AllSongsViewModel
import com.stash.feature.library.mixbuilder.MixBuilderViewModel
import com.stash.feature.library.share.ShareMixViewModel
import com.stash.feature.library.share.SharedMixViewModel
import com.stash.feature.library.share.SharedTrackViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

val LocalLibraryViewModel = staticCompositionLocalOf<LibraryViewModel?> { null }
val LocalAlbumDetailViewModel = staticCompositionLocalOf<AlbumDetailViewModel?> { null }
val LocalArtistDetailViewModel = staticCompositionLocalOf<ArtistDetailViewModel?> { null }
val LocalLikedSongsDetailViewModel = staticCompositionLocalOf<LikedSongsDetailViewModel?> { null }
val LocalPlaylistDetailViewModel = staticCompositionLocalOf<PlaylistDetailViewModel?> { null }
val LocalAllSongsViewModel = staticCompositionLocalOf<AllSongsViewModel?> { null }
val LocalMixBuilderViewModel = staticCompositionLocalOf<MixBuilderViewModel?> { null }
val LocalSharedMixViewModel = staticCompositionLocalOf<SharedMixViewModel?> { null }
val LocalSharedTrackViewModel = staticCompositionLocalOf<SharedTrackViewModel?> { null }
val LocalShareMixViewModel = staticCompositionLocalOf<((Long) -> ShareMixViewModel)?> { null }

@Composable
actual fun libraryViewModel(): LibraryViewModel =
    LocalLibraryViewModel.current ?: error("LocalLibraryViewModel not provided")

@Composable
actual fun albumDetailViewModel(): AlbumDetailViewModel =
    LocalAlbumDetailViewModel.current ?: error("LocalAlbumDetailViewModel not provided")

@Composable
actual fun artistDetailViewModel(): ArtistDetailViewModel =
    LocalArtistDetailViewModel.current ?: error("LocalArtistDetailViewModel not provided")

@Composable
actual fun likedSongsDetailViewModel(): LikedSongsDetailViewModel =
    LocalLikedSongsDetailViewModel.current ?: error("LocalLikedSongsDetailViewModel not provided")

@Composable
actual fun playlistDetailViewModel(): PlaylistDetailViewModel =
    LocalPlaylistDetailViewModel.current ?: error("LocalPlaylistDetailViewModel not provided")

@Composable
actual fun allSongsViewModel(): AllSongsViewModel =
    LocalAllSongsViewModel.current ?: error("LocalAllSongsViewModel not provided")

@Composable
actual fun mixBuilderViewModel(): MixBuilderViewModel =
    LocalMixBuilderViewModel.current ?: error("LocalMixBuilderViewModel not provided")

@Composable
actual fun sharedMixViewModel(): SharedMixViewModel =
    LocalSharedMixViewModel.current ?: error("LocalSharedMixViewModel not provided")

@Composable
actual fun sharedTrackViewModel(): SharedTrackViewModel =
    LocalSharedTrackViewModel.current ?: error("LocalSharedTrackViewModel not provided")

@Composable
actual fun shareMixViewModel(playlistId: Long): ShareMixViewModel =
    LocalShareMixViewModel.current?.invoke(playlistId)
        ?: error("LocalShareMixViewModel not provided")

@Composable
actual fun rememberMultipleAudioPicker(onAudioPicked: (List<String>) -> Unit): () -> Unit {
    return remember {
        {
            val chooser = JFileChooser().apply {
                dialogTitle = "Select Audio Files"
                isMultiSelectionEnabled = true
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                onAudioPicked(chooser.selectedFiles.map { it.toURI().toString() })
            }
        }
    }
}

@Composable
actual fun rememberImagePicker(onImagePicked: (String) -> Unit): () -> Unit {
    return remember {
        {
            val dialog = FileDialog(null as Frame?, "Select Image", FileDialog.LOAD).apply {
                isVisible = true
            }
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) {
                onImagePicked(File(dir, file).toURI().toString())
            }
        }
    }
}

@Composable
actual fun rememberFolderPicker(onFolderPicked: (String) -> Unit): () -> Unit {
    return remember {
        {
            val chooser = JFileChooser().apply {
                dialogTitle = "Select Music Folder"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                onFolderPicked(chooser.selectedFile.toURI().toString())
            }
        }
    }
}

@Composable
actual fun rememberSongFileSharer(): (List<Track>) -> Boolean = { false }

@Composable
actual fun rememberAvailableBytes(): Long {
    return remember {
        runCatching {
            File(System.getProperty("user.home")).usableSpace
        }.getOrDefault(Long.MAX_VALUE)
    }
}
