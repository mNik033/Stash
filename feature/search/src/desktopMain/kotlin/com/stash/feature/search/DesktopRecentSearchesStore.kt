package com.stash.feature.search

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class DesktopRecentSearchesStore @Inject constructor() : RecentSearchesStore {
    private val _recent = MutableStateFlow<List<RecentSearch>>(emptyList())
    override val recent: Flow<List<RecentSearch>> = _recent.asStateFlow()

    override suspend fun record(entry: RecentSearch) {
        val text = entry.text.trim()
        if (text.isEmpty()) return
        val sanitized = entry.copy(
            text = text,
            subtitle = entry.subtitle?.trim()?.takeIf { it.isNotEmpty() },
            thumbnailUrl = entry.thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() },
            artistId = entry.artistId?.trim()?.takeIf { it.isNotEmpty() },
            videoId = entry.videoId?.trim()?.takeIf { it.isNotEmpty() },
        )
        _recent.update { current ->
            val deduped = current.filterNot { sanitized.supersedes(it) }
            (listOf(sanitized) + deduped).take(RecentSearchesStore.MAX)
        }
    }

    override suspend fun remove(entry: RecentSearch) {
        _recent.update { current ->
            current.filterNot { it.sameEntryAs(entry) }
        }
    }

    override suspend fun clear() {
        _recent.value = emptyList()
    }
}
