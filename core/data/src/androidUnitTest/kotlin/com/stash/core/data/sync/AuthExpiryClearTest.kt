package com.stash.core.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A fresh sign-in (or a disconnect) settles that source's expired flag at once
 * via [SyncStateManager.clearAuthExpiry], leaving the other source's flag for
 * the next sync's probe to decide.
 */
class AuthExpiryClearTest {

    @Test fun `clearing Spotify keeps YouTube's flag`() {
        val manager = SyncStateManager()
        manager.onAuthExpiryProbed(AuthExpiryState(spotifyExpired = true, youtubeExpired = true))

        manager.clearAuthExpiry(spotify = true)

        assertEquals(AuthExpiryState(spotifyExpired = false, youtubeExpired = true), manager.authExpiry.value)
    }

    @Test fun `clearing YouTube keeps Spotify's flag`() {
        val manager = SyncStateManager()
        manager.onAuthExpiryProbed(AuthExpiryState(spotifyExpired = true, youtubeExpired = true))

        manager.clearAuthExpiry(youtube = true)

        assertEquals(AuthExpiryState(spotifyExpired = true, youtubeExpired = false), manager.authExpiry.value)
    }

    @Test fun `clearing a source that was never flagged changes nothing`() {
        val manager = SyncStateManager()
        manager.onAuthExpiryProbed(AuthExpiryState(spotifyExpired = false, youtubeExpired = true))

        manager.clearAuthExpiry(spotify = true)

        assertEquals(AuthExpiryState(spotifyExpired = false, youtubeExpired = true), manager.authExpiry.value)
    }

    @Test fun `clearing both leaves nothing expired`() {
        val state = AuthExpiryState(spotifyExpired = true, youtubeExpired = true).cleared(spotify = true, youtube = true)

        assertFalse(state.anyExpired)
    }

    @Test fun `a later probe still wins over a clear`() {
        // The clear is a shortcut, not a latch: the next sync's probe reports the truth.
        val manager = SyncStateManager()
        manager.clearAuthExpiry(spotify = true)

        manager.onAuthExpiryProbed(AuthExpiryState(spotifyExpired = true, youtubeExpired = false))

        assertEquals(AuthExpiryState(spotifyExpired = true, youtubeExpired = false), manager.authExpiry.value)
    }
}
