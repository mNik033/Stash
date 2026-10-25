package com.stash.core.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.blocklist.BlocklistKey
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.TrackBlocklistEntity
import com.stash.core.data.sync.TrackMatcher
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [canonicalWordsForSave]: a song the user saves by hand (a like, a listen, a share) is stored under
 * the loose words, unless their key is blocked; then under the strict words those paths wrote before
 * schema 55, so the block on another version doesn't hide the saved song and sweep it away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SavedSongWordsTest {

    private lateinit var db: StashDatabase
    private lateinit var guard: BlocklistGuard

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        guard = BlocklistGuard(
            database = db,
            blocklistDao = db.trackBlocklistDao(),
            trackDao = db.trackDao(),
            playlistDao = db.playlistDao(),
            downloadQueueDao = db.downloadQueueDao(),
            fileDeleter = mockk(relaxed = true),
            matcher = TrackMatcher(),
            canonicalRecompute = mockk(relaxed = true),
        )
    }

    @After fun tearDown() = db.close()

    /** What the block action stores: the loose key of the song the user blocked. */
    private suspend fun block(title: String, artist: String) = db.trackBlocklistDao().insert(
        TrackBlocklistEntity(
            canonicalKey = BlocklistKey.of(artist, title, TrackMatcher()),
            artist = artist, title = title, blockedAt = 1L, blockedFrom = "test",
        ),
    )

    @Test fun `with nothing blocked the loose words are stored`() = runTest {
        assertEquals("don't stop" to "a, b", guard.canonicalWordsForSave("Don't Stop (feat. X)", "B & A"))
    }

    @Test fun `another version of a blocked song keeps strict words, so it isn't hidden`() = runTest {
        block("Song", "A & B")

        val (title, artist) = guard.canonicalWordsForSave("Song (Live)", "A & B")

        assertEquals("song live" to "a b", title to artist)
        assertFalse("its stored key is not the blocked one", guard.isKeyBlocked(title, artist))
    }

    @Test fun `a song not blocked by its loose key is stored loose even next to a block`() = runTest {
        block("Song", "Artist")
        assertEquals("other" to "artist", guard.canonicalWordsForSave("Other (Live)", "Artist"))
    }

    @Test fun `the blocked song itself stays blocked, and the block stays`() = runTest {
        block("Song", "Artist")

        val (title, artist) = guard.canonicalWordsForSave("Song", "Artist")

        assertEquals("strict and loose words agree", "song" to "artist", title to artist)
        assertTrue("so the save stays hidden, as before schema 55", guard.isKeyBlocked(title, artist))
        assertNotNull("never an unblock", db.trackBlocklistDao().findByKey("artist|song"))
    }
}
