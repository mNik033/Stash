package com.stash.data.download.files

import android.content.Context
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.prefs.LibraryLayout
import com.stash.core.data.prefs.StoragePreference
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The wrong-match swap (#531) asks [FileOrganizer.plannedPath] where a file
 * would go before writing it, and passes a name suffix when another track
 * already uses that spot. Internal storage only: the SAF branch needs a
 * ContentResolver.
 */
class FileOrganizerPlannedPathTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val storagePreference = mockk<StoragePreference>()
    private val trackDao = mockk<TrackDao>(relaxed = true)
    private lateinit var musicDir: File

    @Before
    fun setUp() {
        val filesDir = tmp.newFolder("files")
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns tmp.newFolder("cache")
        every { storagePreference.externalTreeUri } returns flowOf(null)
        coEvery { trackDao.getFirstPlaylistNameForTrack(any()) } returns "Gothic"
        musicDir = File(filesDir, "music")
    }

    private fun organizer(layout: LibraryLayout): FileOrganizer {
        every { storagePreference.libraryLayout } returns flowOf(layout)
        return FileOrganizer(context, storagePreference, trackDao)
    }

    private fun temp(name: String) = tmp.newFile(name).apply { writeText("audio") }

    @Test
    fun `plannedPath is where commitDownload writes`() = runTest {
        for (layout in LibraryLayout.entries) {
            val organizer = organizer(layout)
            for (suffix in listOf(null, "Synthesis")) {
                val planned = organizer.plannedPaths("Evanescence", "Synthesis", "Lacrymosa", "opus", 7L, listOf(suffix)).single()
                val committed = organizer.commitDownload(
                    temp("swap_${layout}_$suffix.opus"), "Evanescence", "Synthesis", "Lacrymosa", "opus", 7L, suffix,
                )
                assertEquals("$layout, suffix $suffix", planned, committed.filePath)
            }
        }
    }

    /**
     * Only a NEW file (an import) steps around a file already at its name; a
     * re-download or upgrade still replaces the track's own file there.
     */
    @Test
    fun `a re-download still replaces the file at its name`() = runTest {
        val organizer = organizer(LibraryLayout.ARTIST_ALBUM)
        val own = File(musicDir, "evanescence/synthesis/lacrymosa.opus")
        own.parentFile!!.mkdirs()
        own.writeText("old")
        // Only this track records the file.
        coEvery { trackDao.countOtherTracksWithFilePath(any(), any()) } returns 0

        val committed = organizer.commitDownload(
            tmp.newFile("redownload.opus").apply { writeText("new") },
            "Evanescence", "Synthesis", "Lacrymosa", "opus", 7L,
        )

        assertEquals(own.absolutePath, committed.filePath)
        assertEquals("new", own.readText())
    }

    /**
     * Names aren't unique per track: under Artist/Album the two halves of a
     * Bach prelude-and-fugue slug to the same 60 characters. A commit that
     * would land on ANOTHER track's file takes a distinct name instead of
     * writing over that track's audio.
     */
    @Test
    fun `a download never writes over another track's file at its name`() = runTest {
        val organizer = organizer(LibraryLayout.ARTIST_ALBUM)
        val album = "The Well-Tempered Clavier"
        val prelude = "The Well-Tempered Clavier, Book 1: Prelude and Fugue No. 1 in C Major, BWV 846: I. Prelude"
        val fugue = "The Well-Tempered Clavier, Book 1: Prelude and Fugue No. 1 in C Major, BWV 846: II. Fugue"
        val first = organizer.commitDownload(temp("prelude.opus"), "Bach", album, prelude, "opus", 1L)
        // Track 1 records the shared name, so track 2 must step past it.
        coEvery { trackDao.countOtherTracksWithFilePath(any(), eq(2L)) } answers {
            if (firstArg<String>() == first.filePath) 1 else 0
        }

        val second = organizer.commitDownload(
            tmp.newFile("fugue.opus").apply { writeText("fugue") }, "Bach", album, fugue, "opus", 2L,
        )

        assertNotEquals(first.filePath, second.filePath)
        assertEquals("audio", File(first.filePath).readText())
        assertEquals("fugue", File(second.filePath).readText())
        assertEquals(File(first.filePath).parent, File(second.filePath).parent)
    }

    @Test
    fun `a clash with no album and no track id still gets a free name`() = runTest {
        val organizer = organizer(LibraryLayout.SINGLE_FOLDER)
        val taken = File(musicDir, "artist-intro.opus").absolutePath
        coEvery { trackDao.countOtherTracksWithFilePath(any(), any()) } answers {
            if (firstArg<String>() == taken) 1 else 0
        }

        val committed = organizer.commitDownload(temp("intro.opus"), "Artist", null, "Intro", "opus", null)

        assertEquals(File(musicDir, "artist-intro-2.opus").absolutePath, committed.filePath)
    }

    @Test
    fun `names that slug to nothing get a placeholder`() = runTest {
        val organizer = organizer(LibraryLayout.ARTIST_ALBUM)

        val committed = organizer.commitDownload(temp("q.opus"), "!!!", "???", "?", "opus", 7L)

        assertEquals(File(musicDir, "unknown-artist/unknown-album/untitled.opus").absolutePath, committed.filePath)
    }

    @Test
    fun `a name suffix gives a distinct file in every layout`() = runTest {
        val expectedCanonical = mapOf(
            LibraryLayout.SINGLE_FOLDER to File(musicDir, "evanescence-lacrymosa.opus"),
            LibraryLayout.PLAYLIST to File(musicDir, "gothic/evanescence-lacrymosa.opus"),
            LibraryLayout.ARTIST_ALBUM to File(musicDir, "evanescence/singles/lacrymosa.opus"),
        )
        for ((layout, canonical) in expectedCanonical) {
            val organizer = organizer(layout)
            // Blank album: the Artist/Album layout files it under singles/.
            // One call answers every candidate name (on SAF: one folder listing).
            val (plain, suffixed) = organizer.plannedPaths("Evanescence", null, "Lacrymosa", "opus", 7L, listOf(null, "7"))
            assertEquals("$layout canonical", canonical.absolutePath, plain)
            assertNotEquals("$layout suffixed", plain, suffixed)
            assertEquals("$layout suffixed stays in the same folder", File(plain!!).parent, File(suffixed!!).parent)
        }
    }
}
