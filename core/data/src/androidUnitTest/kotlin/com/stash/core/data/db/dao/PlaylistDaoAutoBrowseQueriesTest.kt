package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.data.db.entity.PlaylistTrackCrossRef
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Android Auto's Playlists row runs these on every open, in place of loading every playlist's
 * whole track list: they must answer exactly what that Kotlin did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PlaylistDaoAutoBrowseQueriesTest {
    private lateinit var db: StashDatabase
    private lateinit var dao: PlaylistDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.playlistDao()
    }

    @After fun tearDown() = db.close()

    @Test fun `hasDownloadedTrack sees only a downloaded track that is still in the playlist`() = runTest {
        val withDownload = playlist("a")
        add(withDownload, track("1", downloaded = true))
        val streamOnly = playlist("b")
        add(streamOnly, track("2", downloaded = false))
        val removedDownload = playlist("c")
        add(removedDownload, track("3", downloaded = true), removed = true)

        assertThat(dao.hasDownloadedTrack(withDownload)).isTrue()
        assertThat(dao.hasDownloadedTrack(streamOnly)).isFalse()
        assertThat(dao.hasDownloadedTrack(removedDownload)).isFalse()
    }

    @Test fun `the liked count is distinct playable tracks across the likes playlists`() = runTest {
        val stashLikes = playlist("likes-stash")
        val spotifyLikes = playlist("likes-spotify")
        val both = track("1", downloaded = true)
        add(stashLikes, both)
        add(spotifyLikes, both, position = 0) // liked in two places: one row in the car
        add(spotifyLikes, track("2", downloaded = false, checkedAt = null), position = 1) // never checked: playable
        add(spotifyLikes, track("3", downloaded = false, checkedAt = 1_000L), position = 2) // confirmed unstreamable
        add(spotifyLikes, track("4", downloaded = true), position = 3, removed = true)

        assertThat(dao.countPlayableInAutoAcross(listOf(stashLikes, spotifyLikes))).isEqualTo(2)
    }

    private suspend fun playlist(sourceId: String): Long = dao.insert(
        PlaylistEntity(
            name = sourceId,
            source = MusicSource.SPOTIFY,
            sourceId = sourceId,
            type = PlaylistType.CUSTOM,
            syncEnabled = false,
            isActive = true,
        )
    )

    private suspend fun track(key: String, downloaded: Boolean, checkedAt: Long? = null): Long =
        db.trackDao().insert(
            TrackEntity(
                title = "Song $key",
                artist = "Artist $key",
                canonicalTitle = "song $key",
                canonicalArtist = "artist $key",
                isDownloaded = downloaded,
                isStreamable = false,
                isStreamableCheckedAt = checkedAt,
            )
        )

    private suspend fun add(playlistId: Long, trackId: Long, position: Int = 0, removed: Boolean = false) {
        dao.insertCrossRef(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = position,
                addedAt = Instant.parse("2026-09-16T00:00:00Z"),
                removedAt = if (removed) Instant.parse("2026-09-17T00:00:00Z") else null,
            )
        )
    }
}
