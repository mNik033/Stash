package com.stash.core.data.social

import com.stash.core.data.db.dao.TrackDao
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopLikeCoordinator @Inject constructor(
    private val trackDao: TrackDao,
) : LikeCoordinator {
    override val mirrorFailures: SharedFlow<String> = MutableSharedFlow()
    override suspend fun setLiked(trackId: Long, liked: Boolean) {
        if (trackId <= 0L || trackDao.getById(trackId) == null) return
        if (liked) {
            trackDao.markStashLiked(trackId, System.currentTimeMillis())
        } else {
            trackDao.clearStashLiked(trackId)
        }
    }
}
