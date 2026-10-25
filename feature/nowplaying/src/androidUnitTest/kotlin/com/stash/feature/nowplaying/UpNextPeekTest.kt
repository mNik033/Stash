package com.stash.feature.nowplaying

import com.stash.core.model.RepeatMode
import com.stash.core.model.Track
import com.stash.core.model.share.SharedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The "Up next" peek under Now Playing's transport: which song it names for the queue's ends and
 * the repeat modes, a Listen Together room's own queue, and what it shows and reads.
 */
class UpNextPeekTest {

    private fun track(id: Long, title: String, artist: String = "Artist $id") =
        Track(id = id, title = title, artist = artist)

    private val queue = listOf(track(1, "One"), track(2, "Two", "Metro Boomin, Travis Scott"), track(3, "Three"))

    @Test fun `names the row after the playing one, by its lead act`() {
        assertEquals(UpNextPeek.Song("Two", "Metro Boomin"), upNextPeek(queue, currentIndex = 0, repeatMode = RepeatMode.OFF))
    }

    @Test fun `nothing at the end of the queue without repeat`() {
        assertNull(upNextPeek(queue, currentIndex = 2, repeatMode = RepeatMode.OFF))
    }

    @Test fun `repeat-all wraps to the first row`() {
        assertEquals(UpNextPeek.Song("One", "Artist 1"), upNextPeek(queue, currentIndex = 2, repeatMode = RepeatMode.ALL))
    }

    @Test fun `repeat-one, or repeat-all over one song, plays this song again`() {
        assertEquals(UpNextPeek.SameSongAgain, upNextPeek(queue, currentIndex = 0, repeatMode = RepeatMode.ONE))
        assertEquals(UpNextPeek.SameSongAgain, upNextPeek(listOf(track(1, "One")), currentIndex = 0, repeatMode = RepeatMode.ALL))
    }

    @Test fun `no peek without a playing row`() {
        assertNull(upNextPeek(emptyList(), currentIndex = 0, repeatMode = RepeatMode.ALL))
        assertNull(upNextPeek(queue, currentIndex = 5, repeatMode = RepeatMode.OFF))
    }

    @Test fun `a room peeks at its own queue, and hides when it is empty`() {
        val room = listOf(SharedTrack(title = "Room song", artist = "Simon & Garfunkel"), SharedTrack(title = "Later", artist = "B"))
        assertEquals(UpNextPeek.Song("Room song", "Simon & Garfunkel"), roomUpNextPeek(room))
        assertNull(roomUpNextPeek(emptyList()))
    }

    @Test fun `the line shows title and artist, or the title alone`() {
        assertEquals("Two — Metro Boomin", upNextDetail(UpNextPeek.Song("Two", "Metro Boomin")))
        assertEquals("Two", upNextDetail(UpNextPeek.Song("Two", "")))
        assertEquals("Repeating this song", upNextDetail(UpNextPeek.SameSongAgain))
    }

    @Test fun `TalkBack hears the song and where a tap goes`() {
        assertEquals(
            "Up next: Two by Metro Boomin. Opens the queue.",
            upNextDescription(UpNextPeek.Song("Two", "Metro Boomin"), inRoom = false),
        )
        assertEquals("Up next: Two. Opens the session.", upNextDescription(UpNextPeek.Song("Two", ""), inRoom = true))
        assertEquals("Up next: repeating this song. Opens the queue.", upNextDescription(UpNextPeek.SameSongAgain, inRoom = false))
    }
}
