package com.stash.core.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.common.TrackCanonical
import com.stash.core.common.strictCanonical
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.dao.CanonicalWordsRow
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.data.db.entity.PlaylistTrackCrossRef
import com.stash.core.data.db.entity.TrackBlocklistEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [CanonicalRecompute], the one-time re-key after schema 55: rows the old strict rule wrote get the
 * loose rule's words, in batches, resumably, idempotently, and nothing else about the library
 * changes: no row is merged or deleted, no playlist membership, like or file moves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class CanonicalRecomputeTest {

    private lateinit var db: StashDatabase
    private lateinit var recompute: CanonicalRecompute

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recompute = CanonicalRecompute(db)
    }

    @After fun tearDown() = db.close()

    /** A row as Search, a stream, a like or a hand-off stored it before schema 55. */
    private fun strictRow(title: String, artist: String, youtubeId: String? = null) = TrackEntity(
        title = title, artist = artist, youtubeId = youtubeId, source = MusicSource.YOUTUBE,
        canonicalTitle = strictCanonical(title), canonicalArtist = strictCanonical(artist),
    )

    /** A row as the sync stored it, always: the loose rule. */
    private fun looseRow(title: String, artist: String, spotifyUri: String? = null) = TrackEntity(
        title = title, artist = artist, spotifyUri = spotifyUri, source = MusicSource.SPOTIFY,
        canonicalTitle = TrackCanonical.title(title), canonicalArtist = TrackCanonical.artist(artist),
    )

    private suspend fun words(id: Long): Pair<String, String> =
        db.trackDao().getById(id)!!.let { it.canonicalTitle to it.canonicalArtist }

    /** Every column of every row except the two canonical words, to prove nothing else moved. */
    private suspend fun everythingButWords(): List<TrackEntity> =
        db.trackDao().getAllForIntegrityScan().sortedBy { it.id }.map { it.copy(canonicalTitle = "", canonicalArtist = "") }

    @Test fun `re-keys the rows the strict rule wrote, and only those`() = runTest {
        val dao = db.trackDao()
        val strict = dao.insert(strictRow("Don't Stop (feat. X)", "A & B", youtubeId = "video000001"))
        val synced = dao.insert(looseRow("Song (Live)", "A & B", spotifyUri = "spotify:track:1"))
        val alreadyAlike = dao.insert(strictRow("Hello", "Adele")) // strict == loose: nothing to do
        val localImport = dao.insert(TrackEntity(title = "Demo", artist = "Me", canonicalTitle = "", canonicalArtist = ""))
        // The YouTube canonicalizer keys the artist from the video's uploader, not the row's artist.
        val canonicalized = dao.insert(
            TrackEntity(title = "Smooth Criminal", artist = "Michael Jackson", canonicalTitle = "smooth criminal", canonicalArtist = "michael jackson - topic"),
        )

        val outcome = recompute.run()

        assertEquals(CanonicalRecompute.Outcome(scanned = 5, rekeyed = 1, keptBlocked = 0, lastId = canonicalized), outcome)
        assertEquals("don't stop" to "a, b", words(strict))
        assertEquals("song" to "a, b", words(synced))
        assertEquals("hello" to "adele", words(alreadyAlike))
        assertEquals("" to "", words(localImport))
        assertEquals("smooth criminal" to "michael jackson - topic", words(canonicalized))
    }

    @Test fun `looseWordsFor recognises exactly the strict form`() {
        fun row(title: String, artist: String, ct: String, ca: String) = CanonicalWordsRow(1, title, artist, ct, ca)
        assertEquals("don't stop" to "a, b", CanonicalRecompute.looseWordsFor(row("Don't Stop (feat. X)", "A & B", "don t stop feat x", "a b")))
        assertNull("loose already", CanonicalRecompute.looseWordsFor(row("Don't Stop (feat. X)", "A & B", "don't stop", "a, b")))
        assertNull("only the title is strict", CanonicalRecompute.looseWordsFor(row("Don't Stop", "A & B", "don t stop", "a, b")))
        assertNull("never keyed", CanonicalRecompute.looseWordsFor(row("!!!", "???", "", "")))
        assertNull("strict equals loose", CanonicalRecompute.looseWordsFor(row("hello", "adele", "hello", "adele")))
        assertNull("a blank loose title", CanonicalRecompute.looseWordsFor(row("(Intro)", "A & B", "intro", "a b")))
        assertNull("a blank loose title", CanonicalRecompute.looseWordsFor(row("Ft. Lauderdale", "A", "ft lauderdale", "a")))
    }

    @Test fun `a row whose loose title would be blank keeps its words`() = runTest {
        val dao = db.trackDao()
        // A blank title would join every local import's blank key, and name no song.
        val intro = dao.insert(strictRow("(Intro)", "A & B", youtubeId = "video000001"))
        val untitled = dao.insert(strictRow("[Untitled]", "A & B", youtubeId = "video000002"))

        val outcome = recompute.run()

        assertEquals(0, outcome.rekeyed)
        assertEquals("intro" to "a b", words(intro))
        assertEquals("untitled" to "a b", words(untitled))
    }

    @Test fun `runs in batches, handing back each batch's last id`() = runTest {
        val dao = db.trackDao()
        val ids = dao.insertAll(
            (1..1201).map { i -> if (i % 3 == 0) strictRow("Song $i (Live)", "A & B") else looseRow("Song $i", "C, D") },
        )
        val batches = mutableListOf<Long>()

        val outcome = recompute.run(batchSize = 500) { batches += it }

        assertEquals(listOf(ids[499], ids[999], ids[1200]), batches)
        assertEquals(1201, outcome.scanned)
        assertEquals(400, outcome.rekeyed)
        val rows = dao.getAllForIntegrityScan()
        assertEquals("no row added or removed", 1201, rows.size)
        assertTrue(rows.all { it.canonicalTitle == TrackCanonical.title(it.title) && it.canonicalArtist == TrackCanonical.artist(it.artist) })
    }

    @Test fun `never merges or deletes a row and leaves memberships, likes and files alone`() = runTest {
        val dao = db.trackDao()
        val playlists = db.playlistDao()
        // The same song twice: synced from Spotify, and saved from Search before schema 55.
        val synced = dao.insert(looseRow("Don't Stop (feat. X)", "A & B", spotifyUri = "spotify:track:1"))
        val saved = dao.insert(
            strictRow("Don't Stop", "B & A", youtubeId = "video000001").copy(
                filePath = "/m/dont-stop.opus", isDownloaded = true, stashLikedAt = 7L, playCount = 3,
            ),
        )
        val spotifyList = playlists.insert(PlaylistEntity(name = "Spotify", source = MusicSource.SPOTIFY, sourceId = "spotify:playlist:1", type = PlaylistType.CUSTOM))
        val myList = playlists.insert(PlaylistEntity(name = "Mine", source = MusicSource.BOTH, sourceId = "custom_1", type = PlaylistType.CUSTOM))
        playlists.insertCrossRef(PlaylistTrackCrossRef(playlistId = spotifyList, trackId = synced, position = 0))
        playlists.insertCrossRef(PlaylistTrackCrossRef(playlistId = myList, trackId = saved, position = 0))
        playlists.insertCrossRef(PlaylistTrackCrossRef(playlistId = myList, trackId = synced, position = 1))
        val membershipsBefore = playlists.getCrossRefsForPlaylist(spotifyList) + playlists.getCrossRefsForPlaylist(myList)
        val rowsBefore = everythingButWords()

        recompute.run()

        assertEquals("both rows now share one key", words(synced), words(saved))
        assertEquals("but both rows stay, with everything else as it was", rowsBefore, everythingButWords())
        assertEquals(membershipsBefore, playlists.getCrossRefsForPlaylist(spotifyList) + playlists.getCrossRefsForPlaylist(myList))
    }

    @Test fun `running it again changes nothing`() = runTest {
        val dao = db.trackDao()
        dao.insert(strictRow("Don't Stop (feat. X)", "A & B"))
        dao.insert(looseRow("Song", "C"))
        recompute.run()
        val afterFirst = dao.getAllForIntegrityScan()

        val second = recompute.run()

        assertEquals(0, second.rekeyed)
        assertEquals(afterFirst, dao.getAllForIntegrityScan())
    }

    @Test fun `a stopped run resumes after its last committed batch`() = runTest {
        val dao = db.trackDao()
        val ids = dao.insertAll((1..5).map { strictRow("Song $it (feat. X)", "A & B") })
        var saved = 0L

        val stopped = runCatching {
            recompute.run(batchSize = 2) { lastId ->
                saved = lastId
                throw CancellationException("stopped after the first batch")
            }
        }

        assertTrue(stopped.exceptionOrNull() is CancellationException)
        assertEquals(ids[1], saved)
        assertEquals("the first batch is committed", "song 1" to "a, b", words(ids[0]))
        assertEquals("song 2" to "a, b", words(ids[1]))
        assertEquals("the rest is untouched", "song 3 feat x" to "a b", words(ids[2]))

        val resumed = recompute.run(afterId = saved, batchSize = 2)

        assertEquals(3, resumed.scanned)
        assertEquals(3, resumed.rekeyed)
        assertTrue(ids.all { words(it) == TrackCanonical.title("Song ${ids.indexOf(it) + 1} (feat. X)") to "a, b" })
    }

    @Test fun `a row whose new key is blocked keeps its words, so it doesn't vanish`() = runTest {
        val dao = db.trackDao()
        // The user blocked the studio "Song"; the blocklist key is loose, so it covers "Song (Live)" too.
        db.trackBlocklistDao().insert(TrackBlocklistEntity(canonicalKey = "artist|song", artist = "Artist", title = "Song", blockedAt = 1L, blockedFrom = "test"))
        val live = dao.insert(strictRow("Song (Live)", "Artist", youtubeId = "video000001").copy(filePath = "/m/live.opus", isDownloaded = true))
        val other = dao.insert(strictRow("Other (Remix)", "Artist"))

        val outcome = recompute.run()

        assertEquals(1, outcome.keptBlocked)
        assertEquals("song live" to "artist", words(live))
        assertEquals("other" to "artist", words(other))
    }

    @Test fun `a row changed after it was read keeps the newer words`() = runTest {
        val dao = db.trackDao()
        val id = dao.insert(strictRow("Don't Stop", "A & B"))
        // Another writer re-keys the row between the recompute's read and its write.
        dao.updateCanonicalMetadata(id, "Don't Stop", "don't stop now", "a, b", null, null, 0L)

        val written = dao.recomputeCanonicalIfUnchanged(id, oldTitle = "don t stop", oldArtist = "a b", newTitle = "don't stop", newArtist = "a, b")

        assertEquals(0, written)
        assertEquals("don't stop now" to "a, b", words(id))
    }
}
