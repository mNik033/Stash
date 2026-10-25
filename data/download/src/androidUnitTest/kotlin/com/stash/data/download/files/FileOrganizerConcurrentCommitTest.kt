package com.stash.data.download.files

import android.content.Context
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.prefs.LibraryLayout
import com.stash.core.data.prefs.StoragePreference
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Two downloads whose names slug alike, committed at once to internal
 * storage, used to both find the name free (neither row records it until the
 * worker writes it, seconds later) and the second replaced the first one's
 * file. A commit now claims the name it picked, so the other names its file
 * apart; a later commit sees the claim until the row can be expected to
 * record the file. One track's own commits still replace each other.
 */
class FileOrganizerConcurrentCommitTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val storagePreference = mockk<StoragePreference>()
    private val trackDao = mockk<TrackDao>(relaxed = true)

    @Before
    fun setUp() {
        every { context.filesDir } returns tmp.newFolder("files")
        every { context.cacheDir } returns tmp.newFolder("cache")
        every { storagePreference.externalTreeUri } returns flowOf(null)
        every { storagePreference.libraryLayout } returns flowOf(LibraryLayout.SINGLE_FOLDER)
        // No row records any file: the workers haven't written theirs yet.
        coEvery { trackDao.countOtherTracksWithFilePath(any(), any()) } returns 0
    }

    private fun temp(audio: String): File = tmp.newFile().apply { writeText(audio) }

    @Test
    fun `two concurrent commits to one name each keep their own file`() = runBlocking {
        val organizer = FileOrganizer(context, storagePreference, trackDao)
        // Hold each name check until the other commit is checking too (or half
        // a second has passed): without a claim both pick the free name at once.
        val bothPicking = CountDownLatch(2)
        coEvery { trackDao.countOtherTracksWithFilePath(any(), any()) } answers {
            bothPicking.countDown()
            bothPicking.await(500, TimeUnit.MILLISECONDS)
            0
        }

        val (studio, live) = listOf(1L to "studio take", 2L to "live take").map { (trackId, audio) ->
            async(Dispatchers.Default) {
                organizer.commitDownload(temp(audio), "Artist", "Album $trackId", "Intro", "opus", trackId)
            }
        }.awaitAll()

        assertNotEquals(studio.filePath, live.filePath)
        assertEquals("studio take", File(studio.filePath).readText())
        assertEquals("live take", File(live.filePath).readText())
    }

    @Test
    fun `a commit right after another track's names its file apart until the row records it`() = runBlocking {
        val organizer = FileOrganizer(context, storagePreference, trackDao)

        val studio = organizer.commitDownload(temp("studio take"), "Artist", "Album A", "Intro", "opus", 1L)
        val live = organizer.commitDownload(temp("live take"), "Artist", "Live", "Intro", "opus", 2L)

        assertNotEquals(studio.filePath, live.filePath)
        assertEquals("studio take", File(studio.filePath).readText())
    }

    @Test
    fun `a track's own re-commit still replaces its file`() = runBlocking {
        val organizer = FileOrganizer(context, storagePreference, trackDao)

        val first = organizer.commitDownload(temp("opus take"), "Artist", "Album A", "Intro", "opus", 1L)
        val again = organizer.commitDownload(temp("better take"), "Artist", "Album A", "Intro", "opus", 1L)

        assertEquals(first.filePath, again.filePath)
        assertEquals("better take", File(again.filePath).readText())
    }
}
