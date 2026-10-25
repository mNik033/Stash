package com.stash.core.media

import com.google.common.truth.Truth.assertThat
import com.stash.core.model.Track
import org.junit.Test

/**
 * Tests for [QueueDisplay] — the pure logic that decides what queue the UI
 * displays.
 *
 * Background (the bug this pins): `PlayerState.queue` used to be built from
 * the ExoPlayer timeline, but the background fill silently drops stream
 * tracks it can't resolve in the fast lane (antra is disallowed there
 * entirely), so during antra streaming the timeline holds only downloaded
 * tracks — and "Up Next" showed downloaded tracks while playback actually
 * followed the logical queue via just-in-time prefetch insertion.
 */
class QueueDisplayTest {

  private fun track(id: Long, fileFormat: String = "opus") =
    Track(id = id, title = "T$id", artist = "A$id", fileFormat = fileFormat)

  private val t1 = track(1)
  private val t2 = track(2)
  private val t3 = track(3)
  private val t4 = track(4)
  private val t5 = track(5)

  // ---- compute ----

  @Test
  fun `current track in logical queue - returns logical queue with id-matched index`() {
    // Timeline only holds the resolved subset [t1, t3]; logical has all 4.
    val result = QueueDisplay.compute(
      timelineQueue = listOf(t1, t3),
      timelineIndex = 0,
      logicalQueue = listOf(t1, t2, t3, t4),
      currentTrackId = 1L,
    )
    assertThat(result.queue.map { it.id }).containsExactly(1L, 2L, 3L, 4L).inOrder()
    assertThat(result.currentIndex).isEqualTo(0)
    assertThat(result.isLogical).isTrue()
  }

  @Test
  fun `current index is id-matched, not timeline index`() {
    // Playing t3 = timeline index 1, but logical position 2.
    val result = QueueDisplay.compute(
      timelineQueue = listOf(t1, t3),
      timelineIndex = 1,
      logicalQueue = listOf(t1, t2, t3, t4),
      currentTrackId = 3L,
    )
    assertThat(result.currentIndex).isEqualTo(2)
  }

  @Test
  fun `logical rows are overlaid with resolved timeline metadata`() {
    // The timeline's t3 carries stream-resolved format info (flac); the
    // logical (DB) row still says opus. Display should prefer the
    // resolved row so FLAC badges survive the logical-queue switch.
    val resolvedT3 = track(3, fileFormat = "flac")
    val result = QueueDisplay.compute(
      timelineQueue = listOf(t1, resolvedT3),
      timelineIndex = 0,
      logicalQueue = listOf(t1, t2, t3, t4),
      currentTrackId = 1L,
    )
    assertThat(result.queue[2].fileFormat).isEqualTo("flac")
    assertThat(result.queue[1].fileFormat).isEqualTo("opus") // unresolved stays DB row
  }

  @Test
  fun `current track not in logical queue - falls back to timeline`() {
    val result = QueueDisplay.compute(
      timelineQueue = listOf(t1, t3),
      timelineIndex = 1,
      logicalQueue = listOf(t2, t4),
      currentTrackId = 3L,
    )
    assertThat(result.queue.map { it.id }).containsExactly(1L, 3L).inOrder()
    assertThat(result.currentIndex).isEqualTo(1)
    assertThat(result.isLogical).isFalse()
  }

  @Test
  fun `empty logical queue - falls back to timeline`() {
    val result = QueueDisplay.compute(
      timelineQueue = listOf(t1, t2),
      timelineIndex = 0,
      logicalQueue = emptyList(),
      currentTrackId = 1L,
    )
    assertThat(result.isLogical).isFalse()
  }

  @Test
  fun `null or non-positive current id - falls back to timeline`() {
    val logical = listOf(t1, t2)
    for (id in listOf<Long?>(null, 0L, -1L)) {
      val result = QueueDisplay.compute(
        timelineQueue = listOf(t1),
        timelineIndex = 0,
        logicalQueue = logical,
        currentTrackId = id,
      )
      assertThat(result.isLogical).isFalse()
    }
  }

  // ---- moveTimelineTarget ----
  // Scenario for all cases: logical [1,2,3,4,5]; timeline holds the
  // resolved subset [1,3,5] (2 and 4 were dropped by the fast-lane fill).

  @Test
  fun `move resolved track toward front - lands after resolved predecessors`() {
    // Move t5 to logical position 1 → newLogical [1,5,2,3,4].
    // Timeline without the moved item: [1,3]. Only t1 is logically before
    // position 1 → insert at timeline index 1 → timeline becomes [1,5,3].
    val target = QueueDisplay.moveTimelineTarget(
      newLogical = listOf(t1, t5, t2, t3, t4),
      toIndex = 1,
      timelineIdsWithoutMoved = listOf(1L, 3L),
    )
    assertThat(target).isEqualTo(1)
  }

  @Test
  fun `move resolved track toward back`() {
    // Move t1 to logical position 3 → newLogical [2,3,4,1,5].
    // Timeline without moved: [3,5]. Logically before position 3: {2,3,4}
    // → only t3 of those is in the timeline → insert at 1 → [3,1,5].
    val target = QueueDisplay.moveTimelineTarget(
      newLogical = listOf(t2, t3, t4, t1, t5),
      toIndex = 3,
      timelineIdsWithoutMoved = listOf(3L, 5L),
    )
    assertThat(target).isEqualTo(1)
  }

  @Test
  fun `move to logical front - timeline index zero`() {
    val target = QueueDisplay.moveTimelineTarget(
      newLogical = listOf(t5, t1, t2, t3, t4),
      toIndex = 0,
      timelineIdsWithoutMoved = listOf(1L, 3L),
    )
    assertThat(target).isEqualTo(0)
  }

  @Test
  fun `move to logical end - timeline end`() {
    // Move t3 to the end → newLogical [1,2,4,5,3].
    // Timeline without moved: [1,5]; both are logically before position 4
    // → insert at 2 (timeline end) → [1,5,3].
    val target = QueueDisplay.moveTimelineTarget(
      newLogical = listOf(t1, t2, t4, t5, t3),
      toIndex = 4,
      timelineIdsWithoutMoved = listOf(1L, 5L),
    )
    assertThat(target).isEqualTo(2)
  }

  // ---- the same song queued twice ----

  @Test
  fun `while the second copy plays, the current index is that copy`() {
    // [A, B, C, A] with the second A playing: the first match highlighted row 0 and listed
    // B and C, already played, as "Up next".
    val result = QueueDisplay.compute(
      timelineQueue = listOf(t1, t2, t3, t1),
      timelineIndex = 3,
      logicalQueue = listOf(t1, t2, t3, t1),
      currentTrackId = 1L,
    )
    assertThat(result.currentIndex).isEqualTo(3)
  }

  @Test
  fun `the first copy playing stays on the first copy`() {
    val result = QueueDisplay.compute(
      timelineQueue = listOf(t1, t2, t3, t1),
      timelineIndex = 0,
      logicalQueue = listOf(t1, t2, t3, t1),
      currentTrackId = 1L,
    )
    assertThat(result.currentIndex).isEqualTo(0)
  }

  @Test
  fun `copyOrdinal and indexOfCopy find a row again by its copy`() {
    val queue = listOf(t1, t2, t1, t3, t1)
    assertThat(QueueDisplay.copyOrdinal(queue, 0)).isEqualTo(0)
    assertThat(QueueDisplay.copyOrdinal(queue, 4)).isEqualTo(2)
    assertThat(QueueDisplay.indexOfCopy(queue, 1L, 1)).isEqualTo(2)
    assertThat(QueueDisplay.indexOfCopy(queue, 1L, 3)).isEqualTo(-1)
  }

  @Test
  fun `move counts only the copies of a repeated song that sit before the target`() {
    // [A, B, C, B]: move A to logical position 2 -> newLogical [B, C, A, B]. Timeline without
    // the moved item: [B, C, B]. Only the FIRST B and C precede position 2 -> insert at 2.
    // An id set counted both Bs and answered 3.
    val target = QueueDisplay.moveTimelineTarget(
      newLogical = listOf(t2, t3, t1, t2),
      toIndex = 2,
      timelineIdsWithoutMoved = listOf(2L, 3L, 2L),
    )
    assertThat(target).isEqualTo(2)
  }

  // ---- playNextSlot ("Play next": first in, first out) ----

  @Test
  fun `Play next lands right after the current song in the timeline and the walk`() {
    // Walk 2 -> 0 -> 3 -> 1, slot 2 playing, nothing queued with Play next yet.
    val slot = QueueDisplay.playNextSlot(4, currentIndex = 2, walk = listOf(2, 0, 3, 1)) { false }
    assertThat(slot.at).isEqualTo(3)
    assertThat(slot.walk).containsExactly(2, 3, 0, 4, 1).inOrder()
  }

  @Test
  fun `a second Play next goes after the first, in the timeline and the walk`() {
    // Timeline [1, 2, 3, P, 4] with P (slot 3) a Play next; walk 2 -> 3 -> 0 -> 4 -> 1.
    val slot = QueueDisplay.playNextSlot(5, currentIndex = 2, walk = listOf(2, 3, 0, 4, 1)) { it == 3 }
    assertThat(slot.at).isEqualTo(4)
    assertThat(slot.walk).containsExactly(2, 3, 4, 0, 5, 1).inOrder()
  }

  @Test
  fun `shuffle off, Play next skips the run of Play next songs only`() {
    // Slots 1 and 2 are Play nexts, slot 3 isn't, slot 4 is again (queued after a jump back).
    val slot = QueueDisplay.playNextSlot(6, currentIndex = 0, walk = null) { it in setOf(1, 2, 4) }
    assertThat(slot.at).isEqualTo(3)
    assertThat(slot.walk).isNull()
  }

  @Test
  fun `the service's walk is used even when it is the identity order`() {
    // On the player itself an identity walk is a real order, not a mask: one song queued, shuffle on.
    val slot = QueueDisplay.playNextSlot(1, currentIndex = 0, walk = listOf(0)) { false }
    assertThat(slot.at).isEqualTo(1)
    assertThat(slot.walk).containsExactly(0, 1).inOrder()
  }

  @Test
  fun `a walk that doesn't cover the timeline is not placed`() {
    val slot = QueueDisplay.playNextSlot(4, currentIndex = 1, walk = listOf(1, 0)) { false }
    assertThat(slot.at).isEqualTo(2)
    assertThat(slot.walk).isNull()
  }

  // ---- walkWithAppended ----

  @Test
  fun `Add to queue lands at the end of the walk, in tap order`() {
    assertThat(QueueDisplay.walkWithAppended(listOf(2, 0, 3, 1), at = 4, count = 2))
      .containsExactly(2, 0, 3, 1, 4, 5).inOrder()
  }

  @Test
  fun `an unshuffled walk is a masking timeline, not a shuffle - nothing is sent`() {
    assertThat(QueueDisplay.walkWithAppended(listOf(0, 1, 2, 3), at = 4, count = 1)).isNull()
  }

  // ---- walkWithInsertedAt (the queue sheet's Undo) ----

  @Test
  fun `Undo puts the song back on the row of the walk it left`() {
    // Walk 2 -> 0 -> 3 -> 1; the song goes back in at slot 1 (shifting slots 1+ up) as row 2.
    assertThat(QueueDisplay.walkWithInsertedAt(listOf(2, 0, 3, 1), at = 1, count = 1, position = 2))
      .containsExactly(3, 0, 1, 4, 2).inOrder()
  }

  @Test
  fun `Undo past the end of the walk appends, and a negative row goes first`() {
    assertThat(QueueDisplay.walkWithInsertedAt(listOf(2, 0, 3, 1), at = 4, count = 1, position = 9))
      .containsExactly(2, 0, 3, 1, 4).inOrder()
    assertThat(QueueDisplay.walkWithInsertedAt(listOf(2, 0, 3, 1), at = 4, count = 1, position = -1))
      .containsExactly(4, 2, 0, 3, 1).inOrder()
  }

  @Test
  fun `Undo on an unshuffled walk sends nothing either`() {
    assertThat(QueueDisplay.walkWithInsertedAt(listOf(0, 1, 2), at = 1, count = 1, position = 1)).isNull()
  }
}
