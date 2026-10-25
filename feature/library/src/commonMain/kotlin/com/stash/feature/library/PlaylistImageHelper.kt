package com.stash.feature.library

/**
 * Helper for saving center-cropped, scaled custom cover art for playlists.
 */
interface PlaylistImageHelper {
    /**
     * Saves the image at [imageUri] as a 512x512 JPEG and returns a file:// URI
     * with a cache-busting timestamp, or null on failure.
     */
    fun savePlaylistCoverImage(playlistId: Long, imageUri: String): String?

    /** Deletes the custom cover image file for a playlist if present. */
    fun deletePlaylistCoverFile(playlistId: Long)
}
