package com.stash.core.media

import com.stash.core.model.RadioStartResult
import com.stash.core.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Start radio from the Library: the song menus (Songs, playlists, Liked, albums, an artist's songs,
 * History) and Library's artist page.
 */
interface RadioStarter {

    /** A Library start is in flight: the station is being built. Drives the tuning indicator. */
    val tuning: StateFlow<Boolean>

    /** A song radio seeded from [track]; nothing (null) while another start is [tuning]. */
    suspend fun fromSong(track: Track): String?

    /** An artist radio seeded from [artistName]; nothing (null) for a blank name or while [tuning]. */
    suspend fun fromArtist(artistName: String): String?
}

