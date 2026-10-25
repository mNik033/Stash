package com.stash.data.download.files

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.stash.core.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LibrarySizeHolderTest {

    /**
     * The first walk's result, past the 0 B placeholder. The walk runs on a real IO thread as soon
     * as the test subscribes, so a fast fake can finish before the collector reads its first value,
     * and the StateFlow (conflated) then hands it the walk's result directly: asserting the
     * placeholder first made these tests fail now and then.
     */
    private suspend fun ReceiveTurbine<LibrarySizeBreakdown>.awaitFirstWalk(): LibrarySizeBreakdown {
        val first = awaitItem()
        return if (first == LibrarySizeBreakdown(0L, 0L, 0)) awaitItem() else first
    }

    @Test
    fun `refresh recomputes and publishes size without a track count change`() = runTest {
        val trackCount = MutableStateFlow(7)
        val initialSize = LibrarySizeBreakdown(100L, 80L, 2)
        val refreshedSize = LibrarySizeBreakdown(140L, 120L, 3)
        val fileOrganizer = mockk<FileOrganizer>()
        val musicRepository = mockk<MusicRepository>()
        every { musicRepository.getTrackCount() } returns trackCount
        coEvery { fileOrganizer.computeMusicLibrarySize() } returnsMany
            listOf(initialSize, refreshedSize)
        val holder = LibrarySizeHolder(fileOrganizer, musicRepository)

        holder.size.test {
            assertThat(awaitFirstWalk()).isEqualTo(initialSize)

            holder.refresh()

            assertThat(awaitItem()).isEqualTo(refreshedSize)
            coVerify(exactly = 2) { fileOrganizer.computeMusicLibrarySize() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A sync changes the track count hundreds of times; each change used to
     * start another full walk of the library (thousands of binder queries
     * on an SD card) right after the last one ended.
     */
    @Test
    fun `track count changes right after a walk wait for the gap`() = runTest {
        val trackCount = MutableStateFlow(7)
        val size = LibrarySizeBreakdown(100L, 80L, 2)
        val fileOrganizer = mockk<FileOrganizer>()
        val musicRepository = mockk<MusicRepository>()
        every { musicRepository.getTrackCount() } returns trackCount
        coEvery { fileOrganizer.computeMusicLibrarySize() } returns size
        val holder = LibrarySizeHolder(fileOrganizer, musicRepository)

        holder.size.test {
            assertThat(awaitFirstWalk()).isEqualTo(size)

            (8..20).forEach { trackCount.value = it }
            Thread.sleep(500)

            coVerify(exactly = 1) { fileOrganizer.computeMusicLibrarySize() }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
