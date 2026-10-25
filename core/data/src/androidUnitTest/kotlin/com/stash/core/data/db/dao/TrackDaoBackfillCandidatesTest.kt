package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A row no source can fill (Last.fm has no art, the file is unreadable) stays
 * a backfill candidate forever. With a bare LIMIT, SQLite returned rowid
 * order, so once a batch's worth of those existed every run retried the same
 * oldest rows and newer tracks never got art or a duration. Candidates now
 * come newest-first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TrackDaoBackfillCandidatesTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: TrackDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.trackDao()
    }

    @After fun tearDown() = db.close()

    @Test fun `art, duration and stream-only candidates come newest first`() = runTest {
        (1L..5L).forEach { id ->
            dao.insert(
                TrackEntity(
                    id = id, title = "T$id", artist = "A$id", durationMs = 0L,
                    isDownloaded = id <= 3L, filePath = if (id <= 3L) "/$id.opus" else null,
                    isStreamable = id > 3L,
                ),
            )
        }

        assertEquals(listOf(3L, 2L), dao.findArtBackfillCandidates(limit = 2).map { it.id })
        assertEquals(listOf(3L, 2L), dao.findDurationBackfillCandidates(limit = 2).map { it.id })
        assertEquals(listOf(5L), dao.findStreamableMetadataBackfillCandidates(limit = 1).map { it.id })
    }
}
