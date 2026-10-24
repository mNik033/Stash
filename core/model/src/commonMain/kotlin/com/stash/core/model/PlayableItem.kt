package com.stash.core.model

/**
 * Platform-neutral representation of a playable audio stream or local file,
 * decoupling playback state and queue routing from Android Media3 [androidx.media3.common.MediaItem].
 */
data class PlayableItem(
    val mediaId: String,
    val uri: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val isLocal: Boolean = false,
    val artworkUri: String? = null,
)
