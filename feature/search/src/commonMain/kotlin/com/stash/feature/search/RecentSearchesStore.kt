package com.stash.feature.search

import kotlinx.coroutines.flow.Flow

/**
 * Most-recent-first list of committed searches, capped at [MAX].
 */
interface RecentSearchesStore {
    val recent: Flow<List<RecentSearch>>

    /** Record [entry] as the most-recent search. No-op for blank text. */
    suspend fun record(entry: RecentSearch)

    /**
     * Remove [entry] alone. By identity ([RecentSearch.sameEntryAs]), not by
     * its text.
     */
    suspend fun remove(entry: RecentSearch)

    /** Clear all recent searches. */
    suspend fun clear()

    companion object {
        /** Max retained queries — industry-standard recents depth. */
        const val MAX = 10
    }
}
