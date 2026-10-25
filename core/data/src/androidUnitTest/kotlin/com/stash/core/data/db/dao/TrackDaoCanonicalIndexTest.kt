package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every lookup of a song by its canonical words is served by the (canonical_title, canonical_artist)
 * index (schema 55), proven with SQLite's own `EXPLAIN QUERY PLAN` on the SQL the DAO actually runs
 * (captured through Room's query callback, binds included). The lookups used to wrap the columns in
 * `LOWER()` or `||`, which made each one a scan of the whole table: on a ~19k-row library, once per
 * playlist per sync, inside the diff's write transaction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TrackDaoCanonicalIndexTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: TrackDao

    /** The statements the DAO ran, with their binds, since the last [captured] call. */
    private val statements = mutableListOf<Pair<String, List<Any?>>>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StashDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, args -> synchronized(statements) { statements += sql to args.toList() } }, Runnable::run)
            .build()
        dao = db.trackDao()
    }

    @After fun tearDown() = db.close()

    private suspend fun seed() {
        dao.insertAll(
            (1..50).map { i ->
                TrackEntity(
                    title = "Song $i", artist = "Artist ${i % 7}", spotifyUri = "spotify:track:$i", youtubeId = "video$i",
                    canonicalTitle = "song $i", canonicalArtist = "artist ${i % 7}",
                    isDownloaded = i % 2 == 0, filePath = if (i % 2 == 0) "/m/$i.opus" else null,
                )
            },
        )
        synchronized(statements) { statements.clear() }
    }

    /** The single SELECT on `tracks` the DAO call ran. */
    private suspend fun captured(call: suspend () -> Unit): Pair<String, List<Any?>> {
        synchronized(statements) { statements.clear() }
        call()
        val selects = synchronized(statements) {
            statements.filter { (sql, _) -> sql.trimStart().startsWith("SELECT", ignoreCase = true) && "FROM tracks" in sql }
        }
        assertEquals("one SELECT on tracks, got $selects", 1, selects.size)
        return selects.single()
    }

    /** SQLite's plan for [statement], one `detail` line per step. */
    private fun plan(statement: Pair<String, List<Any?>>): List<String> =
        db.openHelper.readableDatabase
            .query(SimpleSQLiteQuery("EXPLAIN QUERY PLAN ${statement.first}", statement.second.toTypedArray()))
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("detail"))) } }

    private fun assertUsesCanonicalIndex(statement: Pair<String, List<Any?>>) {
        val plan = plan(statement)
        assertTrue(
            "searches the canonical index: $plan\nfor ${statement.first}",
            plan.any { it.startsWith("SEARCH") && INDEX in it },
        )
        assertTrue(
            "never scans tracks: $plan\nfor ${statement.first}",
            plan.none { it.startsWith("SCAN") && "tracks" in it },
        )
    }

    @Test fun `the migration's index is the one the entity declares`() {
        val names = db.openHelper.readableDatabase.query("PRAGMA index_list(`tracks`)")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) } }
        assertTrue(names.toString(), INDEX in names)
    }

    @Test fun `findByCanonicalIdentity searches the index`() = runTest {
        seed()
        assertUsesCanonicalIndex(captured { dao.findByCanonicalIdentity("song 3", "artist 3") })
    }

    @Test fun `findAllByCanonicalIdentity searches the index, oldest first, without a sort`() = runTest {
        seed()
        val statement = captured { dao.findAllByCanonicalIdentity("song 3", "artist 3") }
        assertUsesCanonicalIndex(statement)
        assertTrue("ORDER BY id comes from the index order", plan(statement).none { "TEMP B-TREE" in it })
    }

    @Test fun `findAllDownloadedByCanonical searches the index`() = runTest {
        seed()
        assertUsesCanonicalIndex(captured { dao.findAllDownloadedByCanonical("song 4", "artist 4") })
        assertEquals(listOf("Song 4"), dao.findAllDownloadedByCanonical("song 4", "artist 4").map { it.title })
    }

    @Test fun `findDownloadedByCanonicalIdentities searches the index and returns only the wanted pairs`() = runTest {
        seed()
        dao.insert(
            TrackEntity(
                title = "Song 4", artist = "Someone Else", canonicalTitle = "song 4", canonicalArtist = "someone else",
                isDownloaded = true, filePath = "/m/else.opus",
            ),
        )
        assertUsesCanonicalIndex(captured { dao.findDownloadedByCanonicalIdentities(listOf("song 4" to "artist 4", "song 6" to "artist 6")) })
        assertEquals(
            "same title, other artist is not returned",
            setOf("song 4" to "artist 4", "song 6" to "artist 6"),
            dao.findDownloadedByCanonicalIdentities(listOf("song 4" to "artist 4", "song 6" to "artist 6"))
                .map { it.canonicalTitle to it.canonicalArtist }.toSet(),
        )
    }

    @Test fun `findExistingForBatch answers its OR with three index searches`() = runTest {
        seed()
        val statement = captured { dao.findExistingForBatch(listOf("spotify:track:1"), listOf("video2"), listOf("song 3" to "artist 3")) }
        assertUsesCanonicalIndex(statement)
        val plan = plan(statement)
        assertTrue("spotify_uri by its index: $plan", plan.any { it.startsWith("SEARCH") && "index_tracks_spotify_uri" in it })
        assertTrue("youtube_id by its index: $plan", plan.any { it.startsWith("SEARCH") && "index_tracks_youtube_id" in it })
    }

    @Test fun `findExistingForBatch returns exactly the asked-for identities`() = runTest {
        seed()
        dao.insert(TrackEntity(title = "Song 3", artist = "Other", canonicalTitle = "song 3", canonicalArtist = "other"))
        val rows = dao.findExistingForBatch(listOf("spotify:track:1"), listOf("video2"), listOf("song 3" to "artist 3"))
        assertEquals(setOf("Song 1", "Song 2", "Song 3"), rows.map { it.title }.toSet())
        assertTrue("a row sharing only the title is not returned", rows.none { it.artist == "Other" })
    }

    @Test fun `findByAnyIdentity searches indexes for every branch`() = runTest {
        seed()
        val statement = captured {
            dao.findByAnyIdentity(
                spotifyUri = "spotify:track:1", spotifyUriIsNull = 0,
                youtubeId = "video2", youtubeIdIsNull = 0,
                canonicalTitle = "song 3", canonicalArtist = "artist 3",
            )
        }
        assertUsesCanonicalIndex(statement)
    }

    private companion object {
        const val INDEX = "index_tracks_canonical_title_canonical_artist"
    }
}
