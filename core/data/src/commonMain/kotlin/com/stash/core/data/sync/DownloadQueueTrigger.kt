package com.stash.core.data.sync

/**
 * Platform-agnostic trigger for queueing and draining download items.
 */
interface DownloadQueueTrigger {
    fun triggerDrain()
    suspend fun enqueue(queueId: Long) {
        triggerDrain()
    }
}
