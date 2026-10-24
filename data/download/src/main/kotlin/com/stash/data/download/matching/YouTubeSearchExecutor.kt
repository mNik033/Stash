package com.stash.data.download.matching

import com.stash.core.common.Log
import com.stash.data.download.ytdlp.YtDlpRunner
import com.stash.data.download.ytdlp.YtDlpSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes YouTube searches via [YtDlpRunner] and parses the JSON output
 * into [YtDlpSearchResult] DTOs.
 *
 * Uses the `ytsearchN:` prefix to request up to N results from YouTube,
 * combined with `--dump-json --no-download` to retrieve metadata without
 * downloading any media.
 *
 * Thread-safe: all IO is dispatched to [Dispatchers.IO].
 */
@Singleton
class YouTubeSearchExecutor @Inject constructor(
    private val ytDlpRunner: YtDlpRunner,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true // yt-dlp returns null for some fields (e.g. uploader_id)
    }

    companion object {
        private const val TAG = "YouTubeSearchExecutor"
    }

    /**
     * Search YouTube for tracks matching [query].
     *
     * @param query      Free-text search string (e.g. "Artist - Title official audio").
     * @param maxResults Maximum number of results to return (1..20).
     * @return List of parsed search results, possibly empty on error.
     */
    suspend fun search(query: String, maxResults: Int = 5): List<YtDlpSearchResult> {
        require(query.isNotBlank()) { "Search query must not be blank" }
        val clampedMax = maxResults.coerceIn(1, 20)

        return withContext(Dispatchers.IO) {
            try {
                val args = listOf(
                    "ytsearch$clampedMax:$query",
                    "--dump-json",
                    "--no-download",
                    "--flat-playlist", // Required: without this, yt-dlp fetches full metadata per result (~15s vs ~3s)
                )

                val response = ytDlpRunner.execute(args)
                val output = response.out

                if (output.isBlank()) {
                    Log.w(TAG, "yt-dlp returned empty output for query: $query")
                    return@withContext emptyList()
                }

                // yt-dlp emits one JSON object per line when using --dump-json
                output.trim().lines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        try {
                            json.decodeFromString<YtDlpSearchResult>(line)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse yt-dlp JSON line", e)
                            null
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "YouTube search failed for query: $query", e)
                emptyList()
            }
        }
    }
}
