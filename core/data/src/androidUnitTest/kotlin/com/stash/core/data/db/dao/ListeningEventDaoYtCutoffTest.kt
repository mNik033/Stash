package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.ListeningEventEntity
import com.stash.core.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Turning YouTube history on must send only plays from that moment: every play
 * recorded before it also has `yt_scrobbled = 0`, and used to be sent as well.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ListeningEventDaoYtCutoffTest {

    private lateinit var db: StashDatabase
    private lateinit var events: ListeningEventDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        events = db.listeningEventDao()
    }

    @After fun tearDown() = db.close()

    @Test fun `plays before the opt-in instant are neither pending nor counted`() = runTest {
        db.trackDao().insert(
            TrackEntity(id = 1, title = "Borderline", artist = "Tame Impala", canonicalTitle = "borderline", canonicalArtist = "tame impala"),
        )
        events.insert(ListeningEventEntity(trackId = 1, startedAt = OPT_IN - 86_400_000L))
        events.insert(ListeningEventEntity(trackId = 1, startedAt = OPT_IN - 1))
        val atOptIn = events.insert(ListeningEventEntity(trackId = 1, startedAt = OPT_IN))
        val after = events.insert(ListeningEventEntity(trackId = 1, startedAt = OPT_IN + 60_000L))

        assertEquals(listOf(atOptIn, after), events.pendingYtScrobbles(sinceMs = OPT_IN).map { it.id })
        assertEquals(2, events.pendingYtScrobbleCount(sinceMs = OPT_IN).first())
    }

    private companion object {
        const val OPT_IN = 1_759_838_400_000L
    }
}
