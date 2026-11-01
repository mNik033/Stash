package com.stash.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.stash.core.model.Track
import com.stash.feature.library.allsongs.AllSongsViewModel
import com.stash.feature.library.mixbuilder.MixBuilderViewModel
import com.stash.feature.library.share.ShareMixViewModel
import com.stash.feature.library.share.SharedMixViewModel
import com.stash.feature.library.share.SharedTrackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

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
    val scope = rememberCoroutineScope()
    return remember {
        {
            scope.launch(Dispatchers.IO) {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Select Audio Files"
                    isMultiSelectionEnabled = true
                    fileFilter = FileNameExtensionFilter(
                        "Audio Files (*.mp3, *.flac, *.m4a, *.wav, *.opus, *.ogg, *.aac)",
                        "mp3", "flac", "m4a", "wav", "opus", "ogg", "aac", "alac", "aiff"
                    )
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val uris = chooser.selectedFiles.map { it.toURI().toString() }
                    withContext(Dispatchers.Main) {
                        onAudioPicked(uris)
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberImagePicker(onImagePicked: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember {
        {
            scope.launch(Dispatchers.IO) {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Select Playlist Artwork"
                    fileFilter = FileNameExtensionFilter(
                        "Images (*.png, *.jpg, *.jpeg, *.webp)",
                        "png", "jpg", "jpeg", "webp"
                    )
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val uri = chooser.selectedFile.toURI().toString()
                    withContext(Dispatchers.Main) {
                        onImagePicked(uri)
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberFolderPicker(onFolderPicked: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember {
        {
            scope.launch(Dispatchers.IO) {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Select Music Folder"
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val uri = chooser.selectedFile.toURI().toString()
                    withContext(Dispatchers.Main) {
                        onFolderPicked(uri)
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberSongFileSharer(): (List<Track>) -> Boolean {
    return remember {
        { tracks ->
            val files = tracks.mapNotNull { t -> t.filePath?.let { File(it) } }.filter { it.exists() }
            if (files.isNotEmpty() && java.awt.Desktop.isDesktopSupported()) {
                val desktop = java.awt.Desktop.getDesktop()
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE_FILE_DIR)) {
                    desktop.browseFileDirectory(files.first())
                    true
                } else if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(files.first().parentFile)
                    true
                } else false
            } else false
        }
    }
}

@Composable
actual fun rememberAvailableBytes(): Long {
    return remember {
        runCatching {
            File(System.getProperty("user.home")).usableSpace
        }.getOrDefault(Long.MAX_VALUE)
    }
}
