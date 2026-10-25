package com.stash.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v54 -> v55 adds the index every lookup of a song by its canonical words needs, on
 * `tracks (canonical_title, canonical_artist)`. It changes no row: rows that already share a key
 * (two versions of a song, or a duplicate pair) keep existing side by side, which is why the
 * index is not unique, and the canonical values themselves are recomputed later, in Kotlin, by
 * CanonicalRecomputeWorker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MigrationV54V55Test {

    private val DB_NAME = "migration-v54v55-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StashDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun insertTrack(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Int, title: String, ct: String, ca: String, youtubeId: String?) {
        db.execSQL(
            "INSERT INTO tracks (id, title, artist, album, duration_ms, file_format, quality_kbps, " +
                "file_size_bytes, source, date_added, play_count, is_downloaded, canonical_title, " +
                "canonical_artist, match_confidence, match_dismissed, match_flagged, youtube_id) " +
                "VALUES (?, ?, 'A & B', '', 1000, 'opus', 160, 0, 'YOUTUBE', 0, 0, 0, ?, ?, 0.0, 0, 0, ?)",
            arrayOf<Any?>(id, title, ct, ca, youtubeId),
        )
    }

    @Test fun `54 to 55 adds the canonical index and keeps every row as it was`() {
        helper.createDatabase(DB_NAME, 54).use { db ->
            // A synced row (loose words) and a Search-saved row (the old strict words).
            insertTrack(db, 1, "Don't Stop (feat. X)", "don't stop", "a, b", null)
            insertTrack(db, 2, "Don't Stop (feat. X)", "don t stop feat x", "a b", "video000001")
            // Two rows that already share a key: the index must not reject them.
            insertTrack(db, 3, "Song", "song", "a, b", "video000002")
            insertTrack(db, 4, "Song (Live)", "song", "a, b", "video000003")
            db.execSQL("INSERT INTO playlists (id, name, source, source_id, type, is_active, sync_enabled, track_count, date_added) VALUES (9, 'P', 'BOTH', 'custom_9', 'CUSTOM', 1, 1, 2, 0)")
            db.execSQL("INSERT INTO playlist_tracks (playlist_id, track_id, position, added_at) VALUES (9, 3, 0, 0), (9, 4, 1, 0)")
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 55, true, StashMigrations.MIGRATION_54_55)

        db.query("PRAGMA index_list(`tracks`)").use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == INDEX) {
                    found = true
                    assertEquals("not unique: one key holds every version of a song", 0, c.getInt(c.getColumnIndexOrThrow("unique")))
                }
            }
            assertTrue("$INDEX exists", found)
        }
        db.query("PRAGMA index_info(`$INDEX`)").use { c ->
            val columns = buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            assertEquals(listOf("canonical_title", "canonical_artist"), columns)
        }
        db.query("SELECT id, title, canonical_title, canonical_artist, youtube_id FROM tracks ORDER BY id").use { c ->
            val rows = buildList {
                while (c.moveToNext()) add(listOf(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4)))
            }
            assertEquals(
                "no row is merged, deleted or re-keyed by the migration",
                listOf(
                    listOf(1L, "Don't Stop (feat. X)", "don't stop", "a, b", null),
                    listOf(2L, "Don't Stop (feat. X)", "don t stop feat x", "a b", "video000001"),
                    listOf(3L, "Song", "song", "a, b", "video000002"),
                    listOf(4L, "Song (Live)", "song", "a, b", "video000003"),
                ),
                rows,
            )
        }
        db.query("SELECT track_id FROM playlist_tracks WHERE playlist_id = 9 ORDER BY position").use { c ->
            val ids = buildList { while (c.moveToNext()) add(c.getLong(0)) }
            assertEquals(listOf(3L, 4L), ids)
        }
        db.query("EXPLAIN QUERY PLAN SELECT * FROM tracks WHERE canonical_title = 'song' AND canonical_artist = 'a, b'").use { c ->
            val plan = buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("detail"))) }
            assertTrue("the migrated database searches the index: $plan", plan.any { INDEX in it })
            assertFalse(plan.any { it.startsWith("SCAN") })
        }
    }

    private companion object {
        const val INDEX = "index_tracks_canonical_title_canonical_artist"
    }
}
