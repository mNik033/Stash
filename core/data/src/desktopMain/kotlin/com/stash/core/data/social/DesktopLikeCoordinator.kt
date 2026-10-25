package com.stash.core.data.social

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class DesktopLikeCoordinator : LikeCoordinator {
    override val mirrorFailures: SharedFlow<String> = MutableSharedFlow()
    override suspend fun setLiked(trackId: Long, liked: Boolean) {}
}
