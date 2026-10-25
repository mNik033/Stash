package com.stash.core.data.mix

import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.db.dao.DiscoveryQueueDao
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.TrackSkipEventDao
import com.stash.core.data.db.dao.TrackTagDao
import com.stash.core.data.db.entity.StashMixRecipeEntity
import com.stash.core.data.db.entity.TrackEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * A Mix Builder era chip ("80s") means music *released* in that decade. The
 * library slice used to compare it with the year each track was added to
 * Stash, so on any library built recently an era mix kept no library tracks.
 * Moods (below) were not applied to the library slice at all.
 */
class MixGeneratorEraTest {

    private val trackDao: TrackDao = mockk()
    private val trackTagDao: TrackTagDao = mockk(relaxed = true)
    private val listeningEventDao: ListeningEventDao = mockk(relaxed = true)
    private lateinit var generator: MixGenerator

    @Before fun setUp() {
        generator = MixGenerator(
            trackDao,
            trackTagDao,
            listeningEventDao,
            mockk<DiscoveryQueueDao>(relaxed = true),
            mockk<BlocklistGuard>(relaxed = true),
            mockk<TrackSkipEventDao>(relaxed = true),
        )
        coEvery { listeningEventDao.getPlayCountsSinceWithLatest(any()) } returns emptyList()
        coEvery { listeningEventDao.getTrackIdsPlayedSince(any()) } returns emptyList()
        // Every track was added this year, as in any library built recently.
        coEvery { trackDao.getAllDownloaded() } returns (1L..10L).map { stubTrack(it) }
    }

    @Test fun `an era keeps the library tracks tagged with that decade`() = runTest {
        coEvery { trackTagDao.getTrackIdsByTags(listOf("80s")) } returns listOf(3L, 4L)

        val result = generator.generate(eraRecipe(1980, 1989))

        assertEquals(setOf(3L, 4L), result.map { it.id }.toSet())
    }

    @Test fun `an era spanning decades matches each decade tag`() = runTest {
        coEvery { trackTagDao.getTrackIdsByTags(listOf("90s", "2000s")) } returns listOf(7L)

        val result = generator.generate(eraRecipe(1995, 2004))

        assertEquals(listOf(7L), result.map { it.id })
    }

    @Test fun `an era nothing in the library is tagged with is dropped instead of emptying the mix`() = runTest {
        coEvery { trackTagDao.getTrackIdsByTags(any()) } returns emptyList()

        val result = generator.generate(eraRecipe(1970, 1979))

        assertEquals(10, result.size)
    }

    /** Moods used to steer only discovery: a "Chill" library slice was just heavy rotation. */
    @Test fun `a mood keeps the library tracks tagged with it`() = runTest {
        coEvery { trackTagDao.getTrackIdsByTags(MoodTagMap.expand("chill")) } returns listOf(5L, 6L)

        val result = generator.generate(eraRecipe(null, null).copy(moodKeysCsv = "chill"))

        assertEquals(setOf(5L, 6L), result.map { it.id }.toSet())
    }

    @Test fun `a mood nothing in the library is tagged with is dropped`() = runTest {
        coEvery { trackTagDao.getTrackIdsByTags(any()) } returns emptyList()

        val result = generator.generate(eraRecipe(null, null).copy(moodKeysCsv = "focus"))

        assertEquals(10, result.size)
    }

    private fun eraRecipe(start: Int?, end: Int?) = StashMixRecipeEntity(
        id = 1L,
        name = "Era",
        eraStartYear = start,
        eraEndYear = end,
        discoveryRatio = 0f,
        targetLength = 10,
        freshnessWindowDays = 0,
        seedStrategy = "TAG_GRAPH",
        isBuiltin = false,
    )

    private fun stubTrack(id: Long) = TrackEntity(
        id = id,
        title = "Track $id",
        artist = "Artist $id",
        canonicalTitle = "track $id",
        canonicalArtist = "artist $id",
        isDownloaded = true,
        dateAdded = Instant.now(),
    )
}
