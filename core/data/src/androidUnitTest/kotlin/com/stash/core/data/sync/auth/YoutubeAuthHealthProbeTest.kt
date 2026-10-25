package com.stash.core.data.sync.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YoutubeAuthHealthProbeTest {

    private val probe = YoutubeAuthHealthProbe()

    /**
     * Regression guard: an authenticated account with zero YouTube Music
     * playlists browses as Empty, and treating that as "expired" used to
     * short-circuit the whole sync (Spotify included). Every answer was already
     * mapped to false, so the probe no longer walks the user's whole library
     * just to discard the result.
     */
    @Test fun `never reports expired`() = runTest {
        assertFalse(probe.isExpired())
    }

    @Test fun `probes the YouTube source`() {
        assertEquals(AuthSource.YOUTUBE, probe.source)
    }
}
