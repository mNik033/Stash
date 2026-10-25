package com.stash.core.data.social

import kotlinx.coroutines.flow.SharedFlow

/**
 * Single entry point for the heart button.
 */
interface LikeCoordinator {
    /** One subtle "couldn't sync — will retry" per session; UI collects this. */
    val mirrorFailures: SharedFlow<String>

    /**
     * Toggle the heart for [trackId]. [trackId] must be a real Room PK.
     */
    suspend fun setLiked(trackId: Long, liked: Boolean)
}
