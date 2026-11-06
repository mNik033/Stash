package com.stash.core.data.social

import kotlinx.coroutines.flow.SharedFlow

/**
 * Single entry point for the heart button.
 */
interface LikeCoordinator {
    /** A service no longer takes this phone's sign-in (once per service per session). The app says so in a toast. */
    val mirrorFailures: SharedFlow<Destination>

    /**
     * Toggle the heart for [trackId]. [trackId] must be a real Room PK.
     */
    suspend fun setLiked(trackId: Long, liked: Boolean)

    /** Mirror only without toggling local state. */
    suspend fun mirrorOnly(trackId: Long, liked: Boolean)

    companion object {
        const val SAFETY_NET_DELAY_MS = 10 * 60 * 1_000L
        const val IN_PROCESS_MAX_GROUPS = 20
        const val MAX_BACKOFF_MS = 5 * 60 * 1_000L
    }
}
