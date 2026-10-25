package com.stash.core.media.service

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The role-swap hands the outgoing master's queue to the incoming player. What it hands over has
 * to be the SAME queue: the same items around the same current one, in the same play order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) // ExoPlayer's static init reads Build fields
class CrossfadeEngineQueueTransferTest {
    /**
     * Runs the fade to its end. It runs in backgroundScope, and advanceUntilIdle() stops as soon as
     * only background work is left, so it never advanced the fade's ramp.
     */
    private fun TestScope.finishFade() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    private val context = mockk<Context> {
        every { getSystemService(Context.AUDIO_SERVICE) } returns mockk<AudioManager>(relaxed = true)
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).build()

    /** The outgoing master: [queue] in timeline order, playing [current]. */
    private fun master(queue: List<MediaItem>, current: Int, shuffle: ShuffleOrder? = null) =
        mockk<ExoPlayer>(relaxed = true) {
            every { mediaItemCount } returns queue.size
            every { getMediaItemAt(any()) } answers { queue[firstArg()] }
            every { currentMediaItemIndex } returns current
            every { nextMediaItemIndex } returns current + 1
            if (shuffle != null) every { shuffleOrder } returns shuffle
        }

    /** The spare, primed on [incoming], whose count grows as the transfer adds items. */
    private fun spare(incoming: MediaItem) = mockk<ExoPlayer>(relaxed = true) {
        var count = 1
        every { mediaItemCount } answers { count }
        every { currentMediaItem } returns incoming
        every { isPlaying } returns true
        every { addMediaItems(any(), any()) } answers { count += secondArg<List<MediaItem>>().size }
        every { addMediaItems(any<List<MediaItem>>()) } answers { count += firstArg<List<MediaItem>>().size }
    }

    private fun engine(scope: CoroutineScope, master: ExoPlayer, spare: ExoPlayer): CrossfadeEngine {
        val players = ArrayDeque(listOf(master, spare))
        return CrossfadeEngine(context, { players.removeFirst() }, scope).also { it.initialize() }
    }

    @Test fun `the swap keeps the outgoing's shuffle order`() = runTest {
        val queue = listOf(item("a"), item("b"), item("c"), item("d"))
        val order = ShuffleOrder.DefaultShuffleOrder(intArrayOf(2, 0, 3, 1), 7L)
        val out = master(queue, current = 0, shuffle = order)
        val inc = spare(queue[1])
        var swapped: ExoPlayer? = null
        engine(backgroundScope, out, inc).performTransition(fadeMs = 1_000L) { swapped = it }
        finishFade()

        assertThat(swapped).isSameInstanceAs(inc)
        verify { inc.shuffleOrder = order }
    }

    @Test fun `fading into the second copy of a song keeps the queue around that copy`() = runTest {
        // [A, B, C, A, D], fading C into the second A.
        val a = item("a")
        val queue = listOf(a, item("b"), item("c"), a, item("d"))
        val out = master(queue, current = 2)
        val inc = spare(a)
        engine(backgroundScope, out, inc).performTransition(fadeMs = 1_000L) {}
        finishFade()

        verify { inc.addMediaItems(0, queue.subList(0, 3)) }
        verify { inc.addMediaItems(listOf(queue[4])) }
    }

    @Test fun `the upcoming song removed mid-fade hands back to the outgoing, no swap`() = runTest {
        val queue = mutableListOf(item("a"), item("b"), item("c"))
        val out = master(queue, current = 0)
        every { out.playWhenReady } returns true
        val inc = spare(queue[1])
        var swapped: ExoPlayer? = null
        val engine = engine(backgroundScope, out, inc)
        engine.performTransition(fadeMs = 2_000L) { swapped = it }
        advanceTimeBy(500)

        queue.removeAt(1) // swiped away from the queue sheet: C is next now
        every { out.mediaItemCount } returns queue.size
        finishFade()

        assertThat(swapped).isNull()
        assertThat(engine.isTransitioning()).isFalse()
        verify(exactly = 0) { inc.addMediaItems(any(), any()) }
        verify(exactly = 0) { inc.addMediaItems(any<List<MediaItem>>()) }
        verify { inc.stop() }
    }

    @Test fun `a Play next inserted mid-fade after the outgoing reached its end plays next`() = runTest {
        val queue = mutableListOf(item("a"), item("b"))
        val out = master(queue, current = 0)
        every { out.playWhenReady } returns false // held at its end by pauseAtEndOfMediaItems
        val inc = spare(queue[1])
        var swapped: ExoPlayer? = null
        engine(backgroundScope, out, inc).performTransition(fadeMs = 2_000L) { swapped = it }
        advanceTimeBy(500)

        queue.add(1, item("x"))
        every { out.mediaItemCount } returns queue.size
        finishFade()

        assertThat(swapped).isNull()
        verify { out.play() } // on into X, not stuck paused at A's end
    }
}
