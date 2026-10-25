package com.stash.feature.search

import androidx.compose.foundation.lazy.LazyListState
import com.stash.data.ytmusic.model.TrackSummary

/**
 * Interface for pre-warming URLs for search result and artist profile tracks.
 */
interface PreviewPrefetcher {
    fun prefetch(videoIds: List<String>)
    fun prefetchVisible(listState: LazyListState, items: List<TrackSummary>)
    fun cancelAll()
}
