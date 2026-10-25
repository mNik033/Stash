package com.stash.core.media.preview

/**
 * State of the search preview player.
 *
 * [Idle] is the initial state and is re-entered whenever playback ends,
 * is stopped, or an error occurs.
 *
 * [Playing] carries the [videoId] of the track currently being previewed so
 * that UI layers can highlight the correct row without needing a separate
 * "current item" field.
 */
sealed interface PreviewState {
    data object Idle : PreviewState
    data class Playing(val videoId: String) : PreviewState
}
