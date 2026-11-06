package com.stash.data.download.lossless.relay

import com.stash.core.common.platform.AppPaths

/**
 * Whether there is a network that reaches the internet, and a way to wait for one, for [LosslessConfigFetcher]'s refresh
 * loop: offline it waits for the network instead of retrying on a timer. A seam so the loop is tested on virtual time.
 */
internal interface ConfigNetworkSignal {
    /** True when the default network has internet and Android has validated it (not a captive portal). */
    fun isOnline(): Boolean

    /**
     * Waits until [isOnline] would be true and returns true; at once when it already is. Returns false at once when the
     * network can't be watched: nothing was waited for, so the caller's failures still count.
     */
    suspend fun awaitOnline(): Boolean
}

internal object AlwaysOnlineConfigNetworkSignal : ConfigNetworkSignal {
    override fun isOnline(): Boolean = true
    override suspend fun awaitOnline(): Boolean = true
}

internal expect fun createDefaultConfigNetworkSignal(appPaths: AppPaths): ConfigNetworkSignal
