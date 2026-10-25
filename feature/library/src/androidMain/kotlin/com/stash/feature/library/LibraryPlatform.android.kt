package com.stash.feature.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.stash.core.model.Track
import com.stash.feature.library.allsongs.AllSongsViewModel
import com.stash.feature.library.allsongs.AndroidAllSongsViewModel
import com.stash.feature.library.mixbuilder.AndroidMixBuilderViewModel
import com.stash.feature.library.mixbuilder.MixBuilderViewModel
import com.stash.feature.library.share.AndroidShareMixViewModel
import com.stash.feature.library.share.AndroidSharedMixViewModel
import com.stash.feature.library.share.AndroidSharedTrackViewModel
import com.stash.feature.library.share.ShareMixViewModel
import com.stash.feature.library.share.SharedMixViewModel
import com.stash.feature.library.share.SharedTrackViewModel

@Composable
actual fun libraryViewModel(): LibraryViewModel = hiltViewModel<AndroidLibraryViewModel>()

@Composable
actual fun albumDetailViewModel(): AlbumDetailViewModel = hiltViewModel<AndroidAlbumDetailViewModel>()

@Composable
actual fun artistDetailViewModel(): ArtistDetailViewModel = hiltViewModel<AndroidArtistDetailViewModel>()

@Composable
actual fun likedSongsDetailViewModel(): LikedSongsDetailViewModel = hiltViewModel<AndroidLikedSongsDetailViewModel>()

@Composable
actual fun playlistDetailViewModel(): PlaylistDetailViewModel = hiltViewModel<AndroidPlaylistDetailViewModel>()

@Composable
actual fun allSongsViewModel(): AllSongsViewModel = hiltViewModel<AndroidAllSongsViewModel>()

@Composable
actual fun mixBuilderViewModel(): MixBuilderViewModel = hiltViewModel<AndroidMixBuilderViewModel>()

@Composable
actual fun sharedMixViewModel(): SharedMixViewModel = hiltViewModel<AndroidSharedMixViewModel>()

@Composable
actual fun sharedTrackViewModel(): SharedTrackViewModel = hiltViewModel<AndroidSharedTrackViewModel>()

@Composable
actual fun shareMixViewModel(playlistId: Long): ShareMixViewModel =
    hiltViewModel<AndroidShareMixViewModel>(key = "share-$playlistId")

@Composable
actual fun rememberMultipleAudioPicker(onAudioPicked: (List<String>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            onAudioPicked(uris.map { it.toString() })
        }
    }
    return remember(launcher) {
        { launcher.launch(arrayOf("audio/*")) }
    }
}

@Composable
actual fun rememberImagePicker(onImagePicked: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            onImagePicked(uri.toString())
        }
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

@Composable
actual fun rememberFolderPicker(onFolderPicked: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            onFolderPicked(uri.toString())
        }
    }
    return remember(launcher) {
        { launcher.launch(null) }
    }
}

@Composable
actual fun rememberSongFileSharer(): (List<Track>) -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { tracks: List<Track> ->
            val uris = tracks.mapNotNull { it.filePath?.let(Uri::parse) }
            if (uris.isEmpty()) {
                false
            } else {
                val intent = if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
                intent.type = "audio/*"
                intent.clipData = ClipData.newRawUri(null, uris[0]).also { clip ->
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }.isSuccess
            }
        }
    }
}

@Composable
actual fun rememberAvailableBytes(): Long {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            android.os.StatFs(context.filesDir.path).availableBytes
        }.getOrDefault(Long.MAX_VALUE)
    }
}
