package com.stash.data.download.matching

import com.stash.core.data.sync.TrackMatcher
import com.stash.data.ytmusic.InnerTubeClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * [AlbumMatchExecutor]'s process-lifetime album cache must hold YouTube's answers,
 * never a failed request: a NotFound cached after one timeout sent every other track
 * of that album to fuzzy search until the app restarted.
 */
class AlbumMatchExecutorCacheTest {

    private suspend fun find(executor: AlbumMatchExecutor) = executor.findTrackInAlbum(
        targetTitle = "Track One",
        targetArtist = "Some Artist",
        targetAlbum = "Some Album",
        targetDurationMs = 200_000L,
    )

    @Test
    fun `a failed album search is not cached, so the album's next track searches again`() = runTest {
        val innerTube = mock<InnerTubeClient>()
        // InnerTubeClient answers null for a timeout, 429 or 5xx.
        whenever(innerTube.search(any(), anyOrNull())).thenReturn(null)
        val executor = AlbumMatchExecutor(innerTube, TrackMatcher())

        assertNull(find(executor))
        assertNull(find(executor))

        // One query per lookup ("Some Album" has no parenthetical to strip).
        verify(innerTube, times(2)).search(any(), anyOrNull())
    }

    @Test
    fun `an answered search with no album is cached`() = runTest {
        val innerTube = mock<InnerTubeClient>()
        whenever(innerTube.search(any(), anyOrNull())).thenReturn(JsonObject(emptyMap()))
        val executor = AlbumMatchExecutor(innerTube, TrackMatcher())

        assertNull(find(executor))
        assertNull(find(executor))

        verify(innerTube, times(1)).search(any(), anyOrNull())
    }
}
