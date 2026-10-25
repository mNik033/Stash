package com.stash.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayHistoryTest {

    private fun e(id: Long, copy: Int = 0) = PlayHistory.Entry(id.toString(), copy)

    @Test
    fun `a forward move records the song left`() {
        val h = PlayHistory()
        h.moveTo(e(1), previousGone = false)
        h.moveTo(e(2), previousGone = false)
        h.moveTo(e(3), previousGone = false)
        assertThat(h.peek()).isEqualTo(e(2))
        assertThat(h.size).isEqualTo(2)
    }

    @Test
    fun `a move to the most recent entry is a step back and takes it off`() {
        val h = PlayHistory()
        listOf(1L, 2L, 3L).forEach { h.moveTo(e(it), previousGone = false) }
        h.moveTo(e(2), previousGone = false) // Back, ours or the notification's
        assertThat(h.peek()).isEqualTo(e(1))
        assertThat(h.current).isEqualTo(e(2))
    }

    @Test
    fun `another copy of the same song is another entry`() {
        val h = PlayHistory()
        h.moveTo(e(1, copy = 0), previousGone = false)
        h.moveTo(e(1, copy = 1), previousGone = false)
        assertThat(h.peek()).isEqualTo(e(1, copy = 0))
    }

    @Test
    fun `the same entry again is no move`() {
        val h = PlayHistory()
        h.moveTo(e(1), previousGone = false)
        h.moveTo(e(1), previousGone = false)
        assertThat(h.size).isEqualTo(0)
    }

    @Test
    fun `a queue that took the old song away clears the history`() {
        val h = PlayHistory()
        listOf(1L, 2L).forEach { h.moveTo(e(it), previousGone = false) }
        h.moveTo(e(5), previousGone = true)
        assertThat(h.peek()).isNull()
        assertThat(h.current).isEqualTo(e(5))
    }

    @Test
    fun `the history keeps only the most recent songs`() {
        val h = PlayHistory(limit = 3)
        (1L..6L).forEach { h.moveTo(e(it), previousGone = false) }
        assertThat(h.size).isEqualTo(3)
        assertThat(h.pop()).isEqualTo(e(5))
        assertThat(h.pop()).isEqualTo(e(4))
        assertThat(h.pop()).isEqualTo(e(3))
        assertThat(h.pop()).isNull()
    }

    @Test
    fun `reset forgets everything`() {
        val h = PlayHistory()
        listOf(1L, 2L).forEach { h.moveTo(e(it), previousGone = false) }
        h.reset()
        assertThat(h.current).isNull()
        assertThat(h.peek()).isNull()
        h.moveTo(e(7), previousGone = false)
        assertThat(h.peek()).isNull() // the first song of a new queue records nothing
    }
}
