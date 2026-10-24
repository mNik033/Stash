package com.stash.core.common.platform

/**
 * Abstraction over network connectivity and metered state.
 */
interface Connectivity {
    /** True if there is an active, validated internet connection. */
    fun isConnected(): Boolean

    /** True if the active connection is cellular / metered mobile data. */
    fun isCellular(): Boolean
}
