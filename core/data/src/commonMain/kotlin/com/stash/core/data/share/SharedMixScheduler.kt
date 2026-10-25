package com.stash.core.data.share

interface SharedMixScheduler {
    fun enqueuePublish(delaySeconds: Long = 0)
}
