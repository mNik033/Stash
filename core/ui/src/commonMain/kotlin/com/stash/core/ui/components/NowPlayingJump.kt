package com.stash.core.ui.components

/*
 * The decisions behind the "Now playing" jump chip ([NowPlayingJumpChip]), kept free of
 * Compose so the JVM tests can drive them with plain numbers. The chip feeds them the
 * list's layout info (LazyListItemInfo's index / offset / size, in px) every frame through
 * a derivedStateOf, so they must stay cheap: O(visible rows), no allocation beyond the
 * caller's own list.
 */

/** Which way the chip points: the playing row is above the visible window, or below it. */
enum class NowPlayingJumpDirection { UP, DOWN }

/**
 * One laid-out row of a lazy list: its item [index], its top [offset] from the start of the
 * list's viewport and its [size] along the scroll axis (LazyListItemInfo's fields).
 */
data class ListRowSpan(val index: Int, val offset: Int, val size: Int)

/**
 * The lazy-list item index of the playing row, or -1 when there is none to jump to.
 *
 * Every track list that shows the chip puts its track rows LAST: a header (and on some
 * screens a search bar, a download button, a sift row…) first, then one item per track, then
 * [trailingItemCount] footers (none today). Counting back from the end means a screen never
 * has to keep a second copy of its header logic in sync with the chip: whatever it puts above
 * the tracks, `totalItemCount - trailing - trackCount` is where the tracks start.
 *
 * @param trackIndex       The playing track's position in the screen's track list (-1 if absent).
 * @param trackCount       How many track rows the list has.
 * @param totalItemCount   The lazy list's total item count, from its layout info.
 * @return -1 when the track isn't in the list, or the layout hasn't caught up with a list that
 *   just changed (fewer items laid out than there are tracks).
 */
fun nowPlayingItemIndex(
    trackIndex: Int,
    trackCount: Int,
    totalItemCount: Int,
    trailingItemCount: Int = 0,
): Int {
    if (trackIndex !in 0 until trackCount) return -1
    val leading = totalItemCount - trailingItemCount - trackCount
    if (leading < 0) return -1
    return leading + trackIndex
}

/**
 * Where the playing row sits relative to what the listener can actually see, or null when the
 * chip should hide (the row is on screen, there is no playing row, or nothing is laid out yet).
 *
 * The window is [windowStart, windowEnd) in the rows' own offset space; the caller subtracts
 * whatever floats over the list's bottom edge (the glass mini player and tab bar), because a
 * row drawn under the mini player is laid out but not seen. A row counts as visible once its
 * vertical centre is inside the window: a sliver peeking in at an edge still earns the chip,
 * a row that's mostly on screen doesn't.
 *
 * @param targetIndex  The playing row's item index ([nowPlayingItemIndex]); negative = none.
 * @param visibleRows  The laid-out rows in ascending index order (LazyListLayoutInfo.visibleItemsInfo).
 */
fun nowPlayingJumpDirection(
    targetIndex: Int,
    visibleRows: List<ListRowSpan>,
    windowStart: Int,
    windowEnd: Int,
): NowPlayingJumpDirection? {
    if (targetIndex < 0 || visibleRows.isEmpty()) return null
    val row = visibleRows.firstOrNull { it.index == targetIndex }
    if (row != null) {
        val center = row.offset + row.size / 2
        return when {
            center < windowStart -> NowPlayingJumpDirection.UP
            center >= windowEnd -> NowPlayingJumpDirection.DOWN
            else -> null
        }
    }
    // Not laid out at all: it's past one end of the composed rows.
    return if (targetIndex < visibleRows.first().index) NowPlayingJumpDirection.UP else NowPlayingJumpDirection.DOWN
}

/**
 * The item to scroll to so the playing row lands with [contextRows] rows above it rather than
 * jammed against the top edge, under the chip: the listener sees where they are in the list,
 * not just the one row. Clamped at the top of the list.
 */
fun nowPlayingScrollIndex(targetIndex: Int, contextRows: Int = 2): Int =
    (targetIndex - contextRows).coerceAtLeast(0)
