package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.DiscoveryQueueEntity
import com.stash.core.data.db.entity.ListeningEventEntity
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.data.db.entity.PlaylistTrackCrossRef
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.db.entity.TrackSkipEventEntity
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the orphan-sweep predicate + atomic delete (data-loss fix). Only a
 * downloaded track with no active membership and no active discovery row may
 * be swept; everything else must survive. A swept track with listening history
 * keeps its row (and so its plays) and only loses its download.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TrackDaoOrphanSweepTest {

    private lateinit var db: StashDatabase
    private lateinit var trackDao: TrackDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        trackDao = db.trackDao()
    }

    @After fun tearDown() { db.close() }

    private fun track(
        id: Long,
        downloaded: Boolean = true,
        source: MusicSource = MusicSource.SPOTIFY,
    ) = TrackEntity(
        id = id, title = "T$id", artist = "a",
        canonicalTitle = "t$id", canonicalArtist = "a",
        isDownloaded = downloaded, filePath = if (downloaded) "/music/$id.flac" else null,
        source = source,
    )

    @Test fun `sweeps only the unprotected downloaded orphan`() = runTest {
        // 1: true orphan — downloaded, no membership, no discovery row → SWEEP.
        trackDao.insert(track(1))
        // 2: downloaded but has an active playlist membership → survives.
        trackDao.insert(track(2))
        val pl = db.playlistDao().insert(
            PlaylistEntity(
                name = "P", source = MusicSource.SPOTIFY,
                sourceId = "p", type = PlaylistType.CUSTOM,
            ),
        )
        db.playlistDao().insertCrossRef(PlaylistTrackCrossRef(playlistId = pl, trackId = 2, position = 0))
        // 3: downloaded orphan owned by an active (PENDING) discovery row → protected.
        trackDao.insert(track(3))
        val recipeId = db.stashMixRecipeDao().insert(
            com.stash.core.data.db.entity.StashMixRecipeEntity(name = "r"),
        )
        db.discoveryQueueDao().insertIfNew(
            DiscoveryQueueEntity(
                recipeId = recipeId, artist = "a", title = "T3", seedArtist = "s",
                status = DiscoveryQueueEntity.STATUS_PENDING, trackId = 3,
            ),
        )
        // 4: device import — LocalImportCoordinator writes source LOCAL with no
        //    membership at all → never swept (its file may exist nowhere else).
        trackDao.insert(track(4, source = MusicSource.LOCAL))
        // 5: not downloaded → never swept.
        trackDao.insert(track(5, downloaded = false))
        // 6: custom / shared add (source BOTH), no membership → never swept.
        trackDao.insert(track(6, source = MusicSource.BOTH))

        val sweep = trackDao.deleteOrphanedDownloadedTracks()

        assertEquals(listOf(1L), sweep.deleted.map { it.id })
        assertEquals(emptyList<Long>(), sweep.undownloaded.map { it.id })
        // The row is actually gone; the survivors remain, still downloaded.
        val survivors = trackDao.getByIds(listOf(1L, 2L, 3L, 4L, 5L, 6L))
        assertEquals(setOf(2L, 3L, 4L, 5L, 6L), survivors.map { it.id }.toSet())
        assertEquals(setOf(2L, 3L, 4L, 6L), survivors.filter { it.isDownloaded }.map { it.id }.toSet())
    }

    @Test fun `an orphan with listening history keeps its row and plays, losing only its download`() = runTest {
        // 1: played (a pending scrobble hangs off this event) → row kept, un-downloaded.
        trackDao.insert(track(1))
        db.listeningEventDao().insert(ListeningEventEntity(trackId = 1, startedAt = 1_000L, completedAt = 2_000L))
        // 2: only skipped — the skip is mix-affinity signal → row kept too.
        trackDao.insert(track(2))
        db.trackSkipEventDao().insert(TrackSkipEventEntity(trackId = 2, skippedAt = 3_000L, positionMs = 0L))
        // 3: never played → deleted outright.
        trackDao.insert(track(3))

        val sweep = trackDao.deleteOrphanedDownloadedTracks()

        assertEquals(listOf(3L), sweep.deleted.map { it.id })
        assertEquals(setOf(1L, 2L), sweep.undownloaded.map { it.id }.toSet())
        // The caller deletes files from the returned rows, so their paths are the pre-sweep ones.
        assertEquals(setOf("/music/1.flac", "/music/2.flac"), sweep.undownloaded.map { it.filePath }.toSet())
        val kept = trackDao.getByIds(listOf(1L, 2L, 3L))
        assertEquals(setOf(1L, 2L), kept.map { it.id }.toSet())
        kept.forEach {
            assertEquals(false, it.isDownloaded)
            assertNull(it.filePath)
        }
        // The history the CASCADE would have taken is still there.
        assertEquals(1, db.trackSkipEventDao().countSkipsSince(2L, 0L))
        assertEquals(listOf(1L), trackDao.idsWithListeningHistory(listOf(1L)))
        // Un-downloaded, they are no longer orphan candidates: a second pass is a no-op.
        val again = trackDao.deleteOrphanedDownloadedTracks()
        assertEquals(0, again.deleted.size + again.undownloaded.size)
    }

    @Test fun `a removed (tombstoned) membership does not protect an orphan`() = runTest {
        trackDao.insert(track(1))
        val pl = db.playlistDao().insert(
            PlaylistEntity(
                name = "P", source = MusicSource.SPOTIFY,
                sourceId = "p", type = PlaylistType.CUSTOM,
            ),
        )
        // removed_at set = soft-deleted membership; the track is still an orphan.
        db.playlistDao().insertCrossRef(
            PlaylistTrackCrossRef(
                playlistId = pl, trackId = 1, position = 0,
                removedAt = java.time.Instant.parse("2025-01-01T00:00:00Z"),
            ),
        )

        val sweep = trackDao.deleteOrphanedDownloadedTracks()

        assertEquals(listOf(1L), sweep.deleted.map { it.id })
    }
}
