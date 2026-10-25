package com.stash.core.media.actions

import com.stash.core.media.preview.PreviewState
import com.stash.core.model.Playlist
import com.stash.core.model.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared preview + download actions for any screen that renders track rows.
 *
 * Consolidates preview/download wiring across Search, Artist Profile, and Album Discovery screens.
 */
interface TrackActionsDelegate {
    val previewState: StateFlow<PreviewState>
    val userMessages: SharedFlow<String>
    val downloadingIds: StateFlow<Set<String>>
    val downloadedIds: StateFlow<Set<String>>
    val previewLoadingId: StateFlow<String?>
    val waitingForLosslessIds: StateFlow<Set<String>>
    val inListenTogether: Boolean
    val userPlaylists: Flow<List<Playlist>>

    fun bindToScope(scope: CoroutineScope)
    fun previewTrack(track: TrackItem)
    fun stopPreview()
    fun downloadTrack(item: TrackItem)
    suspend fun refreshDownloadedIds(videoIds: Collection<String>)
    fun playNext(item: TrackItem)
    fun startRadio(item: TrackItem)
    fun addToQueue(item: TrackItem)
    fun addToPlaylist(item: TrackItem, playlistId: Long)
    fun createPlaylistAndAdd(item: TrackItem, name: String)
    fun onOwnerCleared()
}
