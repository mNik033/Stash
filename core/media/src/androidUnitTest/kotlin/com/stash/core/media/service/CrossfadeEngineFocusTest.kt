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
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A call (a transient focus loss) pauses the music and its end resumes it,
 * but only what the call itself paused. A pause that lands during the call
 * (the sleep timer, a headset button) is a no-op on the already-paused player,
 * so ExoPlayer reports nothing, and the call's end used to undo it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) // ExoPlayer's static init reads Build fields
class CrossfadeEngineFocusTest {

    private val audioManager = mockk<AudioManager>(relaxed = true) {
        every { requestAudioFocus(any<AudioFocusRequest>()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private val context = mockk<Context> {
        every { getSystemService(Context.AUDIO_SERVICE) } returns audioManager
    }
    private val next = MediaItem.Builder().setMediaId("b").build()

    /** The master's real playWhenReady, so the engine reads back what it (or the "listener") set. */
    private var masterPlayWhenReady = true
    private val masterListeners = mutableListOf<Player.Listener>()
    private val master = mockk<ExoPlayer>(relaxed = true) {
        every { addListener(capture(masterListeners)) } just runs
        every { playWhenReady } answers { masterPlayWhenReady }
        every { playWhenReady = any() } answers { masterPlayWhenReady = firstArg() }
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
        every { playWhenReady } returns true
    }

    /** An engine whose master is playing and holds focus. */
    private fun engine(scope: CoroutineScope): CrossfadeEngine {
        val players = ArrayDeque(listOf(master, spare))
        val engine = CrossfadeEngine(context, { players.removeFirst() }, scope).also { it.initialize() }
        masterListeners.forEach { it.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
        return engine
    }

    @Test fun `the call ending resumes what the call paused`() = runTest {
        val engine = engine(backgroundScope)

        engine.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertThat(masterPlayWhenReady).isFalse()
        engine.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertThat(masterPlayWhenReady).isTrue()
    }

    @Test fun `a pause during the call stays paused when it ends`() = runTest {
        val engine = engine(backgroundScope)

        engine.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        engine.onPlayPauseRequested() // the sleep timer's pause, a no-op on the paused player
        engine.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertThat(masterPlayWhenReady).isFalse()
        verify(exactly = 0) { master.playWhenReady = true }
        // Paused for good, so focus goes back: a later nav prompt can't "resume" it either.
        verify { audioManager.abandonAudioFocusRequest(any()) }
    }

    @Test fun `a song already paused when the call comes stays paused after it`() = runTest {
        val engine = engine(backgroundScope)
        masterPlayWhenReady = false // paused, as ExoPlayer reports a no-op pause: without an event

        engine.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        engine.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        verify(exactly = 0) { master.playWhenReady = true }
    }

    @Test fun `a play or pause outside a call changes nothing`() = runTest {
        val engine = engine(backgroundScope)

        engine.onPlayPauseRequested()

        assertThat(masterPlayWhenReady).isTrue()
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any()) }
    }

    @Test fun `a pause during a call mid-fade keeps the outgoing song, paused`() = runTest {
        var swapped: ExoPlayer? = null
        val engine = engine(backgroundScope)
        engine.performTransition(fadeMs = 2_000L) { swapped = it }
        runCurrent()
        advanceTimeBy(500)

        engine.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        engine.onPlayPauseRequested()
        engine.focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        advanceTimeBy(10_000)
        runCurrent()

        assertThat(swapped).isNull()
        assertThat(engine.isTransitioning()).isFalse()
        verify { spare.stop() }
        verify(exactly = 0) { master.playWhenReady = true }
        // Started once by the fade itself; the call's end didn't start it again.
        verify(exactly = 1) { spare.playWhenReady = true }
    }
}
