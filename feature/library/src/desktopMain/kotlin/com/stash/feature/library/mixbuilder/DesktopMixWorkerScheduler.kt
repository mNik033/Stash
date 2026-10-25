package com.stash.feature.library.mixbuilder

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopMixWorkerScheduler @Inject constructor() : MixWorkerScheduler {
    override suspend fun enqueueRefresh(mixId: Long) {
        // Desktop can run discovery/mix refresh via in-process background coroutines when desktop daemon is active
    }

    override suspend fun enqueueDiscovery() {
        // Desktop discovery worker no-op or in-process
    }
}
