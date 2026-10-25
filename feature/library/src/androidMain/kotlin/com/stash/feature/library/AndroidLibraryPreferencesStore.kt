package com.stash.feature.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.libraryPrefsDataStore by preferencesDataStore("library_prefs")

@Singleton
class AndroidLibraryPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : LibraryPreferencesStore {
    private object Keys {
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SOURCE_FILTER = stringPreferencesKey("source_filter")
    }

    override suspend fun getSortOrder(): SortOrder {
        val stored = context.libraryPrefsDataStore.data.first()[Keys.SORT_ORDER]
        return stored?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.RECENT
    }

    override suspend fun getSourceFilter(): SourceFilter {
        val stored = context.libraryPrefsDataStore.data.first()[Keys.SOURCE_FILTER]
        return stored?.let { runCatching { SourceFilter.valueOf(it) }.getOrNull() } ?: SourceFilter.ALL
    }

    override suspend fun setSortOrder(order: SortOrder) {
        context.libraryPrefsDataStore.edit { it[Keys.SORT_ORDER] = order.name }
    }

    override suspend fun setSourceFilter(filter: SourceFilter) {
        context.libraryPrefsDataStore.edit { it[Keys.SOURCE_FILTER] = filter.name }
    }
}
