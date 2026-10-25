package com.stash.core.media.service

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
 * Mid-fade the session's player is the OUTGOING master; the incoming spare is not wired to the
 * session. Whatever the user does to the outgoing in the fade window has to stop the fade, or the
 * spare plays on and is promoted at the swap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) // ExoPlayer's static init reads Build fields
class CrossfadeEngineInterruptTest {
    /**
     * Runs the fade to its end. It runs in backgroundScope, and advanceUntilIdle() stops as soon as
     * only background work is left, so it never advanced the fade's ramp.
     */
    private fun TestScope.finishFade() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    private val audioManager = mockk<AudioManager>(relaxed = true)
    private val context = mockk<Context> {
        every { getSystemService(Context.AUDIO_SERVICE) } returns audioManager
    }
    private val next = MediaItem.Builder().setMediaId("b").build()

    /** Every listener the engine puts on the master, in registration order. */
    private val masterListeners = mutableListOf<Player.Listener>()
    private val master = mockk<ExoPlayer>(relaxed = true) {
        every { addListener(capture(masterListeners)) } just runs
        every { mediaItemCount } returns 2
        every { getMediaItemAt(0) } returns MediaItem.Builder().setMediaId("a").build()
        every { getMediaItemAt(1) } returns next
        every { currentMediaItemIndex } returns 0
        every { nextMediaItemIndex } returns 1
    }
    private val spare = mockk<ExoPlayer>(relaxed = true) {
        every { mediaItemCount } returns 1
        every { currentMediaItem } returns next
        every { isPlaying } returns true
    }

    private fun engine(scope: CoroutineScope): CrossfadeEngine {
        val players = ArrayDeque(listOf(master, spare))
        return CrossfadeEngine(context, { players.removeFirst() }, scope).also { it.initialize() }
    }

    private fun positionInfo() = mockk<Player.PositionInfo>(relaxed = true)

    @Test fun `a user pause mid-fade stops the fade and the incoming is never promoted`() = runTest {
        var swapped: ExoPlayer? = null
        val engine = engine(backgroundScope)
        engine.performTransition(fadeMs = 2_000L) { swapped = it }
        runCurrent()
        advanceTimeBy(500)

        masterListeners.forEach {
            it.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        }
        finishFade()

        assertThat(swapped).isNull()
        assertThat(engine.isTransitioning()).isFalse()
        verify { spare.stop() }
        verify(exactly = 0) { master.play() } // the pause holds
    }

    @Test fun `the outgoing's own end-of-item hold does not stop the fade`() = runTest {
        var swapped: ExoPlayer? = null
        engine(backgroundScope).performTransition(fadeMs = 2_000L) { swapped = it }
        runCurrent()
        advanceTimeBy(500)

        masterListeners.forEach {
            it.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM)
        }
        finishFade()

        assertThat(swapped).isSameInstanceAs(spare)
    }

    @Test fun `a seek inside the outgoing song mid-fade stops the fade`() = runTest {
        every { master.playWhenReady } returns true
        var swapped: ExoPlayer? = null
        val engine = engine(backgroundScope)
        engine.performTransition(fadeMs = 2_000L) { swapped = it }
        runCurrent()
        advanceTimeBy(500)

        masterListeners.forEach {
            it.onPositionDiscontinuity(positionInfo(), positionInfo(), Player.DISCONTINUITY_REASON_SEEK)
        }
        finishFade()

        assertThat(swapped).isNull()
        assertThat(engine.isTransitioning()).isFalse()
        verify { spare.stop() }
    }

    @Test fun `a permanent focus loss mid-fade ends the fade and focus is not taken back`() = runTest {
        every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        var swapped: ExoPlayer? = null
        val engine = engine(backgroundScope)
        // Playback starts: the master takes focus.
        masterListeners.forEach { it.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
        engine.performTransition(fadeMs = 2_000L) { swapped = it }
        runCurrent()
        advanceTimeBy(500)

        // Another app starts playing.
        engine.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        finishFade()

        assertThat(swapped).isNull()
        assertThat(engine.isTransitioning()).isFalse()
        verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
    }

    @Test fun `a seek after the outgoing reached its end lets it play on`() = runTest {
        every { master.playWhenReady } returns false // held by pauseAtEndOfMediaItems
        engine(backgroundScope).performTransition(fadeMs = 2_000L) {}
        runCurrent()
        advanceTimeBy(500)

        masterListeners.forEach {
            it.onPositionDiscontinuity(positionInfo(), positionInfo(), Player.DISCONTINUITY_REASON_SEEK)
        }

        verify { master.play() }
    }
}
