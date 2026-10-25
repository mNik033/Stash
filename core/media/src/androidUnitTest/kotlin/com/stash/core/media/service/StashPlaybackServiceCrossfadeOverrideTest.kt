package com.stash.core.media.service

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.prefs.CrossfadePreference
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StashPlaybackServiceCrossfadeOverrideTest {
    private val crossfadePreference = mockk<CrossfadePreference>(relaxed = true)

    private fun newService() = Robolectric.buildService(StashPlaybackService::class.java).get()
        .also { it.crossfadePreference = crossfadePreference }

    @Test fun `a session suspends crossfade in memory and restores it, never writing the preference`() {
        val service = newService()
        service.onCrossfadePreference(true)
        assertThat(service.crossfadeActive).isTrue()
        service.setCrossfadeSuspended(true)
        assertThat(service.crossfadeActive).isFalse()
        service.onCrossfadePreference(true) // a preference re-emit mid-session doesn't undo the suspension
        assertThat(service.crossfadeActive).isFalse()
        service.setCrossfadeSuspended(false)
        assertThat(service.crossfadeActive).isTrue()
        coVerify(exactly = 0) { crossfadePreference.setEnabled(any()) }
    }

    @Test fun `an end-of-track sleep timer holds crossfade off until it disarms, never writing the preference`() {
        val service = newService()
        service.onCrossfadePreference(true)
        service.setCrossfadeHeldForSleep(true)
        assertThat(service.crossfadeActive).isFalse()
        service.onCrossfadePreference(true) // a preference re-emit while armed doesn't undo the hold
        assertThat(service.crossfadeActive).isFalse()
        service.setCrossfadeHeldForSleep(false)
        assertThat(service.crossfadeActive).isTrue()
        coVerify(exactly = 0) { crossfadePreference.setEnabled(any()) }
    }

    @Test fun `the sleep hold and a session suspension each keep crossfade off on their own`() {
        val service = newService()
        service.onCrossfadePreference(true)
        service.setCrossfadeSuspended(true)
        service.setCrossfadeHeldForSleep(true)
        service.setCrossfadeHeldForSleep(false)
        assertThat(service.crossfadeActive).isFalse() // still suspended
        service.setCrossfadeHeldForSleep(true)
        service.setCrossfadeSuspended(false)
        assertThat(service.crossfadeActive).isFalse() // still held for sleep
        service.setCrossfadeHeldForSleep(false)
        assertThat(service.crossfadeActive).isTrue()
    }

    @Test fun `ending a session with crossfade off leaves it off`() {
        val service = newService()
        service.onCrossfadePreference(false)
        service.setCrossfadeSuspended(true)
        service.setCrossfadeSuspended(false)
        assertThat(service.crossfadeActive).isFalse()
    }
}
