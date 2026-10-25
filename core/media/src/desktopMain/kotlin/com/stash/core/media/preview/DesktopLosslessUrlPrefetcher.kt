package com.stash.core.media.preview

import com.stash.core.model.TrackItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopLosslessUrlPrefetcher @Inject constructor() : LosslessUrlPrefetcher {
    override fun warmUp(track: TrackItem) = Unit
}
