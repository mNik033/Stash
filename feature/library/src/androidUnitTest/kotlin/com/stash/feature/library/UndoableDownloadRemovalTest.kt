package com.stash.feature.library

import com.google.common.truth.Truth.assertThat
import com.stash.core.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The Library's undoable "Remove download": staged songs leave the lists at once, nothing is
 * deleted until the Undo is gone, an Undo really keeps the file, and nothing deletes twice.
 *
 * The delete runs in backgroundScope, so time moves with advanceTimeBy + runCurrent
 * (advanceUntilIdle stops once only background work is left).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UndoableDownloadRemovalTest {

    private val nude = Track(id = 1L, title = "Nude", artist = "Radiohead", isDownloaded = true)
    private val reckoner = Track(id = 2L, title = "Reckoner", artist = "Radiohead", isDownloaded = true)

    /** Removal whose deletes are recorded in [removed] and answer [succeeds]; 1 s settle. */
    private fun TestScope.removal(removed: MutableList<Long>, succeeds: Boolean = true) =
        UndoableDownloadRemoval(
            scope = backgroundScope,
            remove = { track ->
                removed += track.id
                succeeds
            },
            settleMillis = 1_000L,
        )

    @Test
    fun `staging hides the song and deletes nothing, however long the Undo shows`() = runTest {
        val removed = mutableListOf<Long>()
        val r = removal(removed)

        r.stage(nude)
        advanceTimeBy(60_000L)
        runCurrent()

        assertThat(r.pending.value).isEqualTo(nude)
        assertThat(r.hiddenIds.value).containsExactly(1L)
        assertThat(removed).isEmpty()
    }

    @Test
    fun `undo keeps the file and brings the song back`() = runTest {
        val removed = mutableListOf<Long>()
        val r = removal(removed)

        r.stage(nude)
        assertThat(r.undo(1L)).isTrue()
        r.commit() // the screen leaving afterwards finds nothing staged
        runCurrent()

        assertThat(removed).isEmpty()
        assertThat(r.pending.value).isNull()
        assertThat(r.hiddenIds.value).isEmpty()
    }

    @Test
    fun `commit deletes once and keeps the row hidden until the lists catch up`() = runTest {
        val removed = mutableListOf<Long>()
        val r = removal(removed)

        r.stage(nude)
        r.commit(1L)
        runCurrent()

        assertThat(removed).containsExactly(1L)
        assertThat(r.pending.value).isNull()
        assertThat(r.hiddenIds.value).containsExactly(1L)

        advanceTimeBy(999L)
        runCurrent()
        assertThat(r.hiddenIds.value).containsExactly(1L)

        advanceTimeBy(2L)
        runCurrent()
        assertThat(r.hiddenIds.value).isEmpty()
        assertThat(removed).containsExactly(1L)
    }

    @Test
    fun `a commit naming another song is ignored`() = runTest {
        val removed = mutableListOf<Long>()
        val r = removal(removed)

        r.stage(nude)
        r.commit(2L) // an older snackbar's dismissal
        runCurrent()

        assertThat(removed).isEmpty()
        assertThat(r.pending.value).isEqualTo(nude)
    }

    @Test
    fun `staging a second song commits the first`() = runTest {
        val removed = mutableListOf<Long>()
        val r = removal(removed)

        r.stage(nude)
        r.stage(reckoner)
        runCurrent()

        assertThat(removed).containsExactly(1L)
        assertThat(r.pending.value).isEqualTo(reckoner)
        // Nude is still settling; Reckoner is staged.
        assertThat(r.hiddenIds.value).containsExactly(1L, 2L)
    }

    @Test
    fun `a song already staged or on its way out is not staged again`() = runTest {
        val removed = mutableListOf<Long>()
        val r = removal(removed)

        r.stage(nude)
        r.stage(nude) // double tap
        r.commit()
        runCurrent()
        r.stage(nude) // its delete is still landing
        runCurrent()

        assertThat(removed).containsExactly(1L)
        assertThat(r.pending.value).isNull()
    }

    @Test
    fun `undo after the commit is too late and changes nothing`() = runTest {
        val removed = mutableListOf<Long>()
        val r = removal(removed)

        r.stage(nude)
        r.commit()
        assertThat(r.undo(1L)).isFalse()
        runCurrent()

        assertThat(removed).containsExactly(1L)
        assertThat(r.hiddenIds.value).containsExactly(1L)
    }

    @Test
    fun `undo for a song that isn't staged does nothing`() = runTest {
        val removed = mutableListOf<Long>()
        val r = removal(removed)

        r.stage(nude)
        assertThat(r.undo(2L)).isFalse()

        assertThat(r.pending.value).isEqualTo(nude)
        assertThat(r.hiddenIds.value).containsExactly(1L)
    }

    @Test
    fun `a failed delete shows the song again at once`() = runTest {
        val removed = mutableListOf<Long>()
        val r = removal(removed, succeeds = false)

        r.stage(nude)
        r.commit()
        runCurrent()

        assertThat(removed).containsExactly(1L)
        assertThat(r.hiddenIds.value).isEmpty()
    }

    @Test
    fun `commit with nothing staged does nothing`() = runTest {
        val removed = mutableListOf<Long>()
        val r = removal(removed)

        r.commit()
        runCurrent()

        assertThat(removed).isEmpty()
        assertThat(r.hiddenIds.value).isEmpty()
    }
}
