package com.stash.core.media

import com.stash.core.model.PlayableItem

/**
 * Outcome of a streaming-routing decision when the player is asked to play
 * a track. Surfaced as a return value rather than emitting through an
 * internal SharedFlow so the caller (a ViewModel / screen) can decide how
 * to render each reason — typically a snackbar for the refusal variants
 * and silent no-op for [NotAvailable] rows (which should already be
 * greyed out in the UI).
 */
expect sealed class StreamRoutingResult {
    /** The track is playable — contains platform-agnostic [playableItem]. */
    class Item : StreamRoutingResult {
        val playableItem: PlayableItem
    }

    /**
     * No local file on disk, not marked streamable in the library, OR
     * the resolver couldn't find a stream URL. Library row should be
     * rendered greyed-out; tap is silently ignored.
     */
    data object NotAvailable : StreamRoutingResult

    /**
     * Streaming is enabled but the device is on cellular and the user
     * has not opted-in to streaming on metered networks. Caller surfaces
     * "Streaming on cellular is off in Settings".
     */
    data object CellularRefused : StreamRoutingResult

    /**
     * Streaming is enabled but the device has no validated internet
     * connection (airplane mode, captive portal not yet accepted, etc.).
     * Caller surfaces "You're offline — can't stream this track".
     */
    data object NoConnectivity : StreamRoutingResult

    /**
     * Rapid duplicate tap while a prior resolve for the same videoId is
     * still in flight. Caller should NOT emit any user-visible feedback.
     */
    data object Deduped : StreamRoutingResult
}
