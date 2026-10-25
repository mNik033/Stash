package com.stash.core.data.sync

interface FlacUpgradeEnqueuer {
    suspend fun startBatch(trackIds: List<Long>)
}
