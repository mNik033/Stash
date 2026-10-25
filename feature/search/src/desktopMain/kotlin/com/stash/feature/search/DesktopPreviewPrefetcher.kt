package com.stash.feature.search

import androidx.compose.foundation.lazy.LazyListState
import com.stash.data.ytmusic.model.TrackSummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopPreviewPrefetcher @Inject constructor() : PreviewPrefetcher {
    override fun prefetch(videoIds: List<String>) = Unit
    override fun prefetchVisible(listState: LazyListState, items: List<TrackSummary>) = Unit
    override fun cancelAll() = Unit
}
