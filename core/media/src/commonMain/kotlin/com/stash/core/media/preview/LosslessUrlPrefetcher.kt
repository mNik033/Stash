package com.stash.core.media.preview

import com.stash.core.model.TrackItem

/**
 * Interface for pre-warming lossless URLs for search, artist profile, and album discovery rows.
 */
interface LosslessUrlPrefetcher {
    fun warmUp(track: TrackItem)
}
