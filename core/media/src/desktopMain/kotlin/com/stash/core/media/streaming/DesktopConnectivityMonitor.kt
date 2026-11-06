package com.stash.core.media.streaming

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopConnectivityMonitor @Inject constructor() : ConnectivityMonitor {
    override fun isConnected(): Boolean = true
    override fun isCellular(): Boolean = false
    override fun isMetered(): Boolean = false
    override fun meteredChanges(): Flow<Boolean> = flowOf(false)
}
