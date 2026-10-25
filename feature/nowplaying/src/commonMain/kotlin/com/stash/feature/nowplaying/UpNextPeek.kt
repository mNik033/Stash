package com.stash.feature.nowplaying

import com.stash.core.common.primaryArtist
import com.stash.core.model.RepeatMode
import com.stash.core.model.Track
import com.stash.core.model.share.SharedTrack

/**
 * What the one-line "Up next" peek under Now Playing's transport says plays when this song ends.
 * Null hides the line: nothing is coming (the end of a queue without repeat, an empty room queue).
 */
internal sealed interface UpNextPeek {
    /** [title] by [artist] (the lead act, see primaryArtist; blank when unknown). */
    data class Song(val title: String, val artist: String) : UpNextPeek

    /** Repeat-one, or repeat-all over a one-song queue: this song plays again. */
    data object SameSongAgain : UpNextPeek
}

/**
 * The peek for the queue the sheet shows ([queue], [currentIndex] the playing row). That list is
 * already in play order, so shuffle needs nothing here: with shuffle on it is Media3's shuffle walk
 * (#468), and the row after the current one is what plays next. A station's discoveries are
 * appended to it ahead of the end, so radio needs nothing either.
 *
 * Repeat-one plays this song again when it ends, so that is what the line says, though Next (and
 * a swipe) still go to the following row. Repeat-all wraps from the last row to the first.
 */
internal fun upNextPeek(queue: List<Track>, currentIndex: Int, repeatMode: RepeatMode): UpNextPeek? {
    if (currentIndex !in queue.indices) return null
    if (repeatMode == RepeatMode.ONE) return UpNextPeek.SameSongAgain
    val nextIndex = when {
        currentIndex + 1 < queue.size -> currentIndex + 1
        repeatMode == RepeatMode.ALL -> 0
        else -> return null
    }
    if (nextIndex == currentIndex) return UpNextPeek.SameSongAgain
    val next = queue[nextIndex]
    return UpNextPeek.Song(next.title, next.artist.primaryArtist())
}

/**
 * The peek in a Listen Together room: the room's own Up next (the player holds only the room's
 * current song, and the user's queue is set aside). An empty room queue hides the line, even though
 * the room may start a station when the song ends: nothing is known to be coming yet.
 */
internal fun roomUpNextPeek(roomQueue: List<SharedTrack>): UpNextPeek? =
    roomQueue.firstOrNull()?.let { UpNextPeek.Song(it.title, it.artist.primaryArtist()) }

/** The line's text after "Up next · ": "Title — Artist", the title alone without an artist. */
internal fun upNextDetail(peek: UpNextPeek): String = when (peek) {
    is UpNextPeek.Song -> if (peek.artist.isBlank()) peek.title else "${peek.title} — ${peek.artist}"
    UpNextPeek.SameSongAgain -> "Repeating this song"
}

/**
 * What TalkBack reads for the line, saying where a tap goes: "Up next: Title by Artist. Opens the
 * queue." In a room the tap opens the session, whose sheet holds the room's Up next.
 */
internal fun upNextDescription(peek: UpNextPeek, inRoom: Boolean): String {
    val what = when (peek) {
        is UpNextPeek.Song -> if (peek.artist.isBlank()) peek.title else "${peek.title} by ${peek.artist}"
        UpNextPeek.SameSongAgain -> "repeating this song"
    }
    return "Up next: $what. Opens the ${if (inRoom) "session" else "queue"}."
}
