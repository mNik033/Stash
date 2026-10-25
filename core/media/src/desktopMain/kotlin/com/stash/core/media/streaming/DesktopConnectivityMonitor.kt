package com.stash.core.media.streaming

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopConnectivityMonitor @Inject constructor() : ConnectivityMonitor {
    override fun isConnected(): Boolean = true
    override fun isCellular(): Boolean = false
}
