package com.stash.core.data.backcover

import com.stash.core.common.primaryArtist
import com.stash.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * An album's art beyond its front, as image URLs: its [back] cover (null when it has none) and the
 * pages [inside] it (booklet, gatefold panels, inner sleeve), in reading order.
 *
 * Both come from the Cover Art Archive (scans, 1200px) when it has them, else from Discogs (photos
 * and scans by its users, 600px at most): [backFromDiscogs] and [insideFromDiscogs] say which.
 * Discogs' API terms ask for "Data provided by Discogs", linked to [discogsPage] (the release the
 * images come from), next to its images; null when none are shown.
 */
data class AlbumCoverArt(
    val back: String?,
    val inside: List<String>,
    val backFromDiscogs: Boolean = false,
    val insideFromDiscogs: Boolean = false,
    val discogsPage: String? = null,
)

/** The back covers Now Playing flips to and the pages inside them, one set per album. */
interface AlbumBackCovers {
    /** The album's art as stored: null until it is first looked up. */
    fun observe(album: BackCoverAlbum): Flow<AlbumCoverArt?>

    /**
     * Looks the album up unless its answer is stored, and returns it: null when the lookup failed.
     * The back is stored the moment it is found ([observe] sees it), before the walk ends.
     */
    suspend fun resolve(album: BackCoverAlbum): AlbumCoverArt?

    /** No art: the default of a ViewModel under test. */
    object None : AlbumBackCovers {
        override fun observe(album: BackCoverAlbum): Flow<AlbumCoverArt?> = flowOf(null)
        override suspend fun resolve(album: BackCoverAlbum): AlbumCoverArt? = null
    }

    companion object {
        /** After a failed lookup, the album waits this long before another: 2 minutes. */
        const val FAILURE_BACKOFF_MS = 2L * 60 * 1000
    }
}

/**
 * The album a song's back cover belongs to: its primary artist and title, plus the song's ISRC,
 * which finds the album on MusicBrainz more surely than a title search.
 */
data class BackCoverAlbum(val artist: String, val title: String, val isrc: String?) {
    /** Every song on the album shares this ([isrc] is the song's, so it stays out). */
    val key: String get() = backCoverAlbumKey(artist, title)

    companion object {
        /** The playing song's album, or null for a song without one (a single YouTube upload). */
        fun of(track: Track): BackCoverAlbum? {
            val title = track.album.trim().takeIf { it.isNotEmpty() } ?: return null
            val artist = track.albumArtist.ifBlank { track.artist }.primaryArtist().trim()
                .takeIf { it.isNotEmpty() } ?: return null
            return BackCoverAlbum(artist, title, track.isrc?.trim()?.takeIf { it.isNotEmpty() })
        }
    }
}

/** The `album_back_covers` row key for an album: artist and title, trimmed and lowercased. */
fun backCoverAlbumKey(artist: String, title: String): String =
    "${artist.trim().lowercase()}\u001F${title.trim().lowercase()}"

