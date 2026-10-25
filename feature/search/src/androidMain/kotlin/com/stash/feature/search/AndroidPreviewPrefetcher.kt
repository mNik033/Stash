package com.stash.feature.search

import androidx.compose.foundation.lazy.LazyListState
import com.stash.core.common.Log
import com.stash.data.download.preview.PreviewUrlCache
import com.stash.data.download.preview.PreviewUrlExtractor
import com.stash.data.ytmusic.model.TrackSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Warms [PreviewUrlExtractor] for a list of `videoId`s so that a later
 * `extractStreamUrl` call from the UI resolves instantly.
 */
class AndroidPreviewPrefetcher(
    private val extractor: PreviewUrlExtractor,
    private val previewUrlCache: MutableMap<String, String>,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : PreviewPrefetcher {

    @Inject
    constructor(extractor: PreviewUrlExtractor, cache: PreviewUrlCache) :
        this(extractor, cache.asMutableMap)

    private val jobs = mutableListOf<Job>()

    override fun prefetch(videoIds: List<String>) {
        videoIds
            .filter { it !in previewUrlCache }
            .forEach { id ->
                val job = scope.launch {
                    try {
                        previewUrlCache[id] = extractor.extractStreamUrl(id, allowYtDlp = false)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        Log.w(TAG, "prefetch fail $id: ${t.message}")
                    }
                }
                jobs.add(job)
            }
    }

    override fun prefetchVisible(listState: LazyListState, items: List<TrackSummary>) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || items.isEmpty()) return
        val first = (visible.first().index - 3).coerceAtLeast(0)
        val last = (visible.last().index + 3).coerceAtMost(items.lastIndex)
        if (first > last) return
        prefetch(items.subList(first, last + 1).map { it.videoId })
    }

    override fun cancelAll() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    companion object {
        private const val TAG = "PreviewPrefetcher"
    }
}

fun PreviewPrefetcher(
    extractor: PreviewUrlExtractor,
    previewUrlCache: MutableMap<String, String>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
): AndroidPreviewPrefetcher = AndroidPreviewPrefetcher(extractor, previewUrlCache, scope)

fun PreviewPrefetcher(
    extractor: PreviewUrlExtractor,
    cache: PreviewUrlCache,
): AndroidPreviewPrefetcher = AndroidPreviewPrefetcher(extractor, cache)
