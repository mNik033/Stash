package com.stash.core.common

import kotlin.jvm.Volatile

/** The last metered answer seen, for [isActiveNetworkMetered] and the network callback that feeds it. */
object MeteredNetwork {
    /** Null until the first network is seen. Process-wide: one device, one active network. */
    @Volatile
    var lastKnown: Boolean? = null
        internal set

    /**
     * [current] (null: no active network) remembered and returned; with none, the last answer seen,
     * or not metered before any. A network callback reports through here too, so both agree.
     */
    fun read(current: Boolean?): Boolean {
        if (current != null) {
            lastKnown = current
            return current
        }
        return lastKnown ?: false
    }
}
