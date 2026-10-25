package com.stash.core.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The "Now playing" jump chip's decisions: which item is the playing row, whether the listener
 * can see it (the glass chrome at the bottom doesn't count), and which way to point.
 *
 * Rows are 60px tall in a 600px viewport whose bottom 120px sits under the mini player and tab
 * bar, so the visible window is [0, 480).
 */
class NowPlayingJumpTest {

    private val rowHeight = 60
    private val windowEnd = 480

    /** Rows [first]..[last] laid out back to back, [first] starting at [firstOffset]. */
    private fun rows(first: Int, last: Int, firstOffset: Int = 0) =
        (first..last).map { ListRowSpan(index = it, offset = firstOffset + (it - first) * rowHeight, size = rowHeight) }

    private fun direction(target: Int, laidOut: List<ListRowSpan>) =
        nowPlayingJumpDirection(target, laidOut, windowStart = 0, windowEnd = windowEnd)

    // ── nowPlayingItemIndex ──────────────────────────────────────────────

    @Test
    fun `tracks after one header start at item one`() {
        // Library Songs: the recently-downloaded rail, then 50 tracks.
        assertThat(nowPlayingItemIndex(trackIndex = 0, trackCount = 50, totalItemCount = 51)).isEqualTo(1)
        assertThat(nowPlayingItemIndex(trackIndex = 49, trackCount = 50, totalItemCount = 51)).isEqualTo(50)
    }

    @Test
    fun `a varying header is counted back from the end`() {
        // Playlist detail with header + download button + search bar above 10 tracks.
        assertThat(nowPlayingItemIndex(trackIndex = 3, trackCount = 10, totalItemCount = 13)).isEqualTo(6)
    }

    @Test
    fun `trailing footers are skipped`() {
        assertThat(
            nowPlayingItemIndex(trackIndex = 0, trackCount = 10, totalItemCount = 13, trailingItemCount = 2),
        ).isEqualTo(1)
    }

    @Test
    fun `a track that isn't in the list has no row`() {
        assertThat(nowPlayingItemIndex(trackIndex = -1, trackCount = 10, totalItemCount = 11)).isEqualTo(-1)
        assertThat(nowPlayingItemIndex(trackIndex = 10, trackCount = 10, totalItemCount = 11)).isEqualTo(-1)
    }

    @Test
    fun `a layout that hasn't caught up with a longer list has no row yet`() {
        // The list just grew from 5 to 20 tracks; the last layout still holds 6 items.
        assertThat(nowPlayingItemIndex(trackIndex = 2, trackCount = 20, totalItemCount = 6)).isEqualTo(-1)
    }

    // ── nowPlayingJumpDirection ─────────────────────────────────────────

    @Test
    fun `a row in the window hides the chip`() {
        assertThat(direction(target = 3, laidOut = rows(0, 9))).isNull()
    }

    @Test
    fun `nothing playing hides the chip`() {
        assertThat(direction(target = -1, laidOut = rows(0, 9))).isNull()
    }

    @Test
    fun `nothing laid out hides the chip`() {
        assertThat(direction(target = 3, laidOut = emptyList())).isNull()
    }

    @Test
    fun `a row scrolled far down points down`() {
        assertThat(direction(target = 200, laidOut = rows(0, 9))).isEqualTo(NowPlayingJumpDirection.DOWN)
    }

    @Test
    fun `a row scrolled far up points up`() {
        assertThat(direction(target = 2, laidOut = rows(40, 49))).isEqualTo(NowPlayingJumpDirection.UP)
    }

    @Test
    fun `a row laid out under the mini player is not visible`() {
        // Row 8 spans 480..540: laid out, but entirely under the glass chrome.
        assertThat(direction(target = 8, laidOut = rows(0, 9))).isEqualTo(NowPlayingJumpDirection.DOWN)
    }

    @Test
    fun `a row mostly above the top edge points up`() {
        // Scrolled 40px into row 10: its centre (30px down the row) is above the window.
        assertThat(direction(target = 10, laidOut = rows(10, 19, firstOffset = -40))).isEqualTo(NowPlayingJumpDirection.UP)
    }

    @Test
    fun `a row mostly on screen at the top edge counts as visible`() {
        // Scrolled 20px into row 10: its centre is 10px inside the window.
        assertThat(direction(target = 10, laidOut = rows(10, 19, firstOffset = -20))).isNull()
    }

    @Test
    fun `a row half hidden at the bottom edge points down`() {
        // Row 7 spans 450..510: its centre (480) is the first pixel under the chrome.
        assertThat(direction(target = 7, laidOut = rows(0, 9, firstOffset = 30))).isEqualTo(NowPlayingJumpDirection.DOWN)
    }

    @Test
    fun `a top inset moves the window's top edge`() {
        // A 50px overlay at the top: row 0's centre (30) is under it.
        assertThat(nowPlayingJumpDirection(0, rows(0, 9), windowStart = 50, windowEnd = windowEnd))
            .isEqualTo(NowPlayingJumpDirection.UP)
    }

    // ── nowPlayingScrollIndex ───────────────────────────────────────────

    @Test
    fun `the jump leaves two rows of context above the playing row`() {
        assertThat(nowPlayingScrollIndex(120)).isEqualTo(118)
    }

    @Test
    fun `the jump never scrolls past the top`() {
        assertThat(nowPlayingScrollIndex(1)).isEqualTo(0)
        assertThat(nowPlayingScrollIndex(0)).isEqualTo(0)
    }
}
