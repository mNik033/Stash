package com.stash.feature.library

/**
 * Persists the Library screen's sort order + source filter across app restarts.
 */
interface LibraryPreferencesStore {
    suspend fun getSortOrder(): SortOrder
    suspend fun getSourceFilter(): SourceFilter
    suspend fun setSortOrder(order: SortOrder)
    suspend fun setSourceFilter(filter: SourceFilter)
}
