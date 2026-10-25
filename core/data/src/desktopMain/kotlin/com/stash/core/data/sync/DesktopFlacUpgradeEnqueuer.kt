package com.stash.core.data.sync

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopFlacUpgradeEnqueuer @Inject constructor() : FlacUpgradeEnqueuer {
    override suspend fun startBatch(trackIds: List<Long>) {}
}
