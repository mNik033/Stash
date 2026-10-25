package com.stash.core.data.share

/** The optional display name on shared mixes (spec §5), remembered between shares. */
interface SharePreference {
    suspend fun displayName(): String?
    suspend fun setDisplayName(name: String?)
}
