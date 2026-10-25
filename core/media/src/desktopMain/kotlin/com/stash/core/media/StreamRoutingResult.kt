package com.stash.core.media

import com.stash.core.model.PlayableItem

actual sealed class StreamRoutingResult {
    /** The track is playable — contains platform-agnostic [playableItem]. */
    actual data class Item(
        actual val playableItem: PlayableItem,
    ) : StreamRoutingResult()

    /**
     * No local file on disk, not marked streamable in the library, OR
     * the resolver couldn't find a stream URL. Library row should be
     * rendered greyed-out; tap is silently ignored.
     */
    actual data object NotAvailable : StreamRoutingResult()

    /**
     * Streaming is enabled but the device is on cellular and the user
     * has not opted-in to streaming on metered networks. Caller surfaces
     * "Streaming on cellular is off in Settings".
     */
    actual data object CellularRefused : StreamRoutingResult()

    /**
     * Streaming is enabled but the device has no validated internet
     * connection (airplane mode, captive portal not yet accepted, etc.).
     * Caller surfaces "You're offline — can't stream this track".
     */
    actual data object NoConnectivity : StreamRoutingResult()

    /**
     * Rapid duplicate tap while a prior resolve for the same videoId is
     * still in flight. Caller should NOT emit any user-visible feedback.
     */
    actual data object Deduped : StreamRoutingResult()
}
