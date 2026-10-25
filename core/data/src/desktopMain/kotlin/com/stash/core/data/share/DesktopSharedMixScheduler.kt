package com.stash.core.data.share

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopSharedMixScheduler @Inject constructor() : SharedMixScheduler {
    override fun enqueuePublish(delaySeconds: Long) {
        // Desktop no-op
    }
}
