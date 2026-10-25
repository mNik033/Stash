package com.stash.core.data.library

import com.stash.core.common.TrackCanonical
import com.stash.core.common.strictCanonical
import com.stash.core.data.blocklist.BlocklistGuard

/**
 * The canonical (title, artist) words a song the user saves by hand is stored under: a like (Now
 * Playing's heart, the notification's, a community post's), a listen, a shared song, a playlist add
 * from a stream, a followed mix, a Listen Together or hand-off song. Unlike Search downloads and
 * add-to-playlist from Search, these paths don't refuse a blocked song: the user just asked for it.
 *
 * The one loose rule ([TrackCanonical]), unless its key is blocked: then the strict words
 * ([strictCanonical]) these paths stored until schema 55. The blocklist is keyed by the loose words,
 * which don't tell versions apart, and the library lists hide, and the integrity sweep deletes, a
 * row whose stored key is blocked: liking "Song (Live)" after blocking "Song" would otherwise make
 * the liked song vanish at once and lose its file, likes and playlists at the next launch. The
 * one-time re-key makes the same exception ([CanonicalRecompute]).
 *
 * Never an unblock: the block stays as it is. A save whose strict words still form the blocked key
 * ("Song" by "Artist" reads the same either way), or whose video or Spotify id is blocked, stays
 * hidden, as before schema 55.
 *
 * @return the words to store, title first.
 */
suspend fun BlocklistGuard.canonicalWordsForSave(title: String, artist: String): Pair<String, String> {
    val looseTitle = TrackCanonical.title(title)
    val looseArtist = TrackCanonical.artist(artist)
    if (!isKeyBlocked(looseTitle, looseArtist)) return looseTitle to looseArtist
    return strictCanonical(title) to strictCanonical(artist)
}
