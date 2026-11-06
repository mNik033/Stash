package com.stash.core.media.streaming

import kotlinx.coroutines.flow.Flow

/**
 * Monitors network reachability and connection type for streaming and sync routing decisions.
 */
interface ConnectivityMonitor {
    fun isConnected(): Boolean
    fun isCellular(): Boolean
    fun isMetered(): Boolean
    fun meteredChanges(): Flow<Boolean>
}
