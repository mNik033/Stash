package com.stash.core.media.streaming

/**
 * Monitors network reachability and connection type for streaming and sync routing decisions.
 */
interface ConnectivityMonitor {
    fun isConnected(): Boolean
    fun isCellular(): Boolean
}
