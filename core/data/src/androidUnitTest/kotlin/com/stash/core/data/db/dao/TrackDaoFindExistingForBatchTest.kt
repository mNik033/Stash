package com.stash.core.data.db.dao

import androidx.room.Room
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
 * Covers the sentinel-value contract on [TrackDao.findExistingForBatch]:
 * callers must never pass an empty list (SQLite `IN ()` is a syntax error),
 * and a sentinel placeholder for a dimension with no real values must not
 * spuriously match.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TrackDaoFindExistingForBatchTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: TrackDao

    private val sentinel = "\u0000__stash_never_match__"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.trackDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `empty list for a dimension is safe and matches via the others`() = runTest {
        // Room 2.7 expands an empty IN () safely (no SQLiteException) — the
        // sentinel in the DAO wrapper is belt-and-suspenders for older Room,
        // not a live requirement. Documents actual behavior.
        val id = dao.insert(TrackEntity(title = "T", artist = "A", canonicalTitle = "t", canonicalArtist = "a"))

        val result = dao.findExistingForBatch(
            spotifyUris = emptyList(),
            youtubeIds = emptyList(),
            canonicalIdentities = listOf("t" to "a"),
        )

        assertEquals(1, result.size)
        assertEquals(id, result.single().id)
    }

    @Test fun `sentinel placeholder matches nothing by itself, only real canonical key matches`() = runTest {
        val id = dao.insert(
            TrackEntity(title = "Runaway", artist = "Kanye West", canonicalTitle = "runaway", canonicalArtist = "kanye west")
        )

        val result = dao.findExistingForBatch(
            spotifyUris = listOf(sentinel),
            youtubeIds = listOf(sentinel),
            canonicalIdentities = listOf("runaway" to "kanye west"),
        )

        assertEquals(1, result.size)
        assertEquals(id, result.single().id)
    }

    @Test fun `sentinel never accidentally matches a real row`() = runTest {
        dao.insert(TrackEntity(title = "T", artist = "A", spotifyUri = null, canonicalTitle = "t", canonicalArtist = "a"))

        val result = dao.findExistingForBatch(
            spotifyUris = listOf(sentinel),
            youtubeIds = listOf(sentinel),
            canonicalIdentities = listOf("no-match" to "key"),
        )

        assertTrue(result.isEmpty())
    }

    @Test fun `a blank canonical identity is never looked up, so no local import is loaded`() = runTest {
        // "(Intro)" or "[Untitled]" key as a blank title; a blank title used to bind as
        // `canonical_title IN ('')`, which loads every never-keyed local import ("", "").
        val boundArgs = mutableListOf<Any?>()
        val watchedDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StashDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, args -> if (sql.trimStart().startsWith("SELECT", ignoreCase = true)) boundArgs.addAll(args) }, Runnable::run)
            .build()
        try {
            val watchedDao = watchedDb.trackDao()
            repeat(3) { watchedDao.insert(TrackEntity(title = "Import $it", artist = "Me", isDownloaded = true, filePath = "/m/$it.mp3")) }
            watchedDao.insert(
                TrackEntity(
                    title = "[Untitled]", artist = "Artist", canonicalTitle = "", canonicalArtist = "artist",
                    isDownloaded = true, filePath = "/m/untitled.opus",
                ),
            )
            val byUri = watchedDao.insert(
                TrackEntity(title = "(Intro)", artist = "Artist", spotifyUri = "spotify:track:intro", canonicalTitle = "", canonicalArtist = "artist"),
            )
            boundArgs.clear()

            val result = watchedDao.findExistingForBatch(
                spotifyUris = listOf("spotify:track:intro"),
                youtubeIds = listOf(sentinel),
                canonicalIdentities = listOf("" to "artist", "" to "", "song" to ""),
            )

            assertEquals("only the id match comes back", listOf(byUri), result.map { it.id })
            assertTrue("no blank title was bound: $boundArgs", "" !in boundArgs)
            assertTrue(
                "a downloaded twin is never looked up by a blank identity either",
                watchedDao.findDownloadedByCanonicalIdentities(listOf("" to "artist", "" to "")).isEmpty(),
            )
            assertTrue("$boundArgs", "" !in boundArgs)
        } finally {
            watchedDb.close()
        }
    }

    @Test fun `oversized key sets stay under the SQLite bind limit and lose no matches`() = runTest {
        // Issue #337: Android <= 11 ships SQLite with a 999 bind-variable cap.
        // A ~500-track playlist produces >999 IN() variables in one query and
        // sync dies with "too many SQL variables". Robolectric's SQLite is too
        // new to enforce the cap, so assert on observed bind counts instead.
        var maxSelectBinds = 0
        val watchedDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryCallback(
                { sqlQuery, bindArgs ->
                    if (sqlQuery.trimStart().startsWith("SELECT", ignoreCase = true)) {
                        maxSelectBinds = maxOf(maxSelectBinds, bindArgs.size)
                    }
                },
                Runnable::run,
            )
            .build()
        try {
            val watchedDao = watchedDb.trackDao()
            // Rows 0..899 are queried by spotify_uri and youtube_id; rows
            // 900..999 ONLY by canonical key — they can only be found by a
            // later chunk, so a dropped or unmerged chunk fails the test.
            watchedDao.insertAll(
                (0 until 1000).map { i ->
                    TrackEntity(
                        title = "T$i", artist = "A$i",
                        spotifyUri = "spotify:track:$i", youtubeId = "yt$i",
                        canonicalTitle = "t$i", canonicalArtist = "a$i",
                    )
                }
            )

            val result = watchedDao.findExistingForBatch(
                spotifyUris = (0 until 900).map { "spotify:track:$it" },
                youtubeIds = (0 until 900).map { "yt$it" },
                canonicalIdentities = (900 until 1000).map { "t$it" to "a$it" },
            )

            assertEquals(1000, result.size)
            assertEquals(1000, result.map { it.id }.distinct().size)
            assertTrue(
                "a single SELECT bound $maxSelectBinds variables (device limit is 999)",
                maxSelectBinds <= 999,
            )
        } finally {
            watchedDb.close()
        }
    }
}