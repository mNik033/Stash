package com.stash.data.download.files

import android.content.ContentResolver
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.prefs.LibraryLayout
import com.stash.core.data.prefs.StoragePreference
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A download committed into a SAF tree (an SD card) over a document already at
 * its name: the track's own file is replaced only once the new one is written
 * in full, and another track's file is never replaced at all.
 */
class FileOrganizerSafCommitTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val resolver = mockk<ContentResolver>(relaxed = true)
    private val storagePreference = mockk<StoragePreference>()
    private val trackDao = mockk<TrackDao>(relaxed = true)
    private lateinit var tree: FakeSafTree

    private val canonical = "evanescence/synthesis/lacrymosa.opus"

    @Before
    fun setUp() {
        every { context.filesDir } returns tmp.newFolder("files")
        every { context.cacheDir } returns tmp.newFolder("cache")
        every { context.contentResolver } returns resolver
        every { storagePreference.libraryLayout } returns flowOf(LibraryLayout.ARTIST_ALBUM)
        coEvery { trackDao.countOtherTracksWithFilePath(any(), any()) } returns 0
        mockkStatic(DocumentFile::class)
        tree = FakeSafTree(resolver, storagePreference)
        tree.seed(canonical, OLD)
    }

    @After
    fun tearDown() {
        unmockkStatic(DocumentFile::class)
    }

    private fun organizer() = FileOrganizer(context, storagePreference, trackDao)

    private suspend fun commit(): FileOrganizer.CommittedTrack {
        val temp = tmp.newFile("dl_7.opus").apply { writeBytes(NEW) }
        return organizer().commitDownload(temp, "Evanescence", "Synthesis", "Lacrymosa", "opus", 7L)
    }

    @Test
    fun `a re-download replaces the track's own document at its name`() = runBlocking {
        val committed = commit()

        // Path-based providers: the replacement takes the old document's URI.
        assertThat(committed.filePath).isEqualTo(tree.uriOf(canonical))
        assertThat(tree.written[tree.uriOf(canonical)]).isEqualTo(NEW)
        assertThat(tree.exists("evanescence/synthesis/lacrymosa.stash-part.opus")).isFalse()
    }

    /**
     * The old commit deleted the document first: a full card left the track
     * with nothing but a partial file at its name.
     */
    @Test
    fun `a failed write leaves the old document as it was`() = runBlocking {
        tree.failWritesTo = { it != tree.uriOf(canonical) }

        try {
            commit()
            fail("a failed write must not commit")
        } catch (expected: java.io.IOException) {
            // The card is full.
        }

        assertThat(tree.exists(canonical)).isTrue()
        assertThat(tree.written[tree.uriOf(canonical)]).isEqualTo(OLD)
        assertThat(tree.exists("evanescence/synthesis/lacrymosa.stash-part.opus")).isFalse()
    }

    @Test
    fun `another track's document at the name is never replaced`() = runBlocking {
        coEvery { trackDao.countOtherTracksWithFilePath(tree.uriOf(canonical), 7L) } returns 1

        val committed = commit()

        assertThat(tree.deleted).doesNotContain(tree.uriOf(canonical))
        assertThat(tree.written[tree.uriOf(canonical)]).isEqualTo(OLD)
        assertThat(committed.filePath).isEqualTo(tree.uriOf("evanescence/synthesis/lacrymosa-synthesis.opus"))
        assertThat(tree.written[committed.filePath]).isEqualTo(NEW)
    }

    private companion object {
        val OLD = "old audio".toByteArray()
        val NEW = "new audio".toByteArray()
    }
}
