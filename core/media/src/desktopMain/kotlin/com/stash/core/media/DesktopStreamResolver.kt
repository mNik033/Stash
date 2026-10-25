package com.stash.core.media

import com.stash.core.common.Log
import com.stash.core.model.Track
import com.stash.core.model.YouTubeVideoId
import com.stash.data.download.preview.AudioUrlTailProbe
import com.stash.data.ytmusic.InnerTubeClient
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.SearchResultSection
import com.stash.data.ytmusic.model.TopResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopStreamResolver @Inject constructor(
    private val innerTubeClient: InnerTubeClient,
    private val ytMusicApiClient: YTMusicApiClient,
    private val tailProbe: AudioUrlTailProbe,
) {
    companion object {
        private const val TAG = "DesktopStreamResolver"
    }

    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        require(YouTubeVideoId.isValid(videoId)) { "Invalid YouTube video id: $videoId" }

        val stream = innerTubeClient.playerForAudio(videoId) { response, streamPot ->
            val status = response["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.content
            if (status != "OK") {
                Log.w(TAG, "player status for $videoId is $status")
                return@playerForAudio null
            }
            val formats = response["streamingData"]?.jsonObject?.get("adaptiveFormats")?.jsonArray
                ?: return@playerForAudio null

            val audioFormats = formats.filterIsInstance<JsonObject>().filter { f ->
                val mime = f["mimeType"]?.jsonPrimitive?.content ?: ""
                mime.startsWith("audio/") && f["url"] != null
            }
            if (audioFormats.isEmpty()) return@playerForAudio null

            // Prefer Opus itag 251/250, otherwise highest bitrate audio
            val bestFormat = audioFormats.filter {
                (it["mimeType"]?.jsonPrimitive?.content ?: "").contains("opus")
            }.maxByOrNull {
                it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            } ?: audioFormats.maxByOrNull {
                it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            } ?: return@playerForAudio null

            val rawUrl = bestFormat["url"]?.jsonPrimitive?.content ?: return@playerForAudio null
            val streamUrl = if (streamPot != null && !rawUrl.contains("pot=")) {
                if ('?' in rawUrl) "$rawUrl&pot=$streamPot" else "$rawUrl?pot=$streamPot"
            } else {
                rawUrl
            }

            val contentLength = bestFormat["contentLength"]?.jsonPrimitive?.content?.toLongOrNull()
            if (!tailProbe.servesFullFile(streamUrl, contentLength)) {
                Log.i(TAG, "tail probe rejected URL for $videoId; testing next format/variant")
                return@playerForAudio null
            }

            streamUrl
        }

        val resolved = stream?.url ?: "https://www.youtube.com/watch?v=$videoId"
        Log.d(TAG, "Resolved stream for $videoId (variant=${stream?.variant}) -> ${resolved.take(60)}...")
        resolved
    }

    suspend fun resolveForTrack(track: Track): String? {
        // 1. If downloaded file exists on disk, play locally
        val filePath = track.filePath
        if (!filePath.isNullOrBlank()) {
            val file = File(filePath)
            if (file.exists() && file.length() > 0L) {
                return filePath
            }
        }

        // 2. If track already has a valid youtubeId
        val ytId = track.youtubeId?.takeIf { YouTubeVideoId.isValid(it) }
        if (ytId != null) {
            return runCatching { resolveStreamUrl(ytId) }.getOrNull()
        }

        // 3. Fallback: Search YouTube Music by artist + title
        val artist = track.artist.trim()
        val title = track.title.trim()
        if (artist.isNotBlank() || title.isNotBlank()) {
            val canonicalId = runCatching {
                ytMusicApiClient.searchCanonicalVideoId(artist, title)
            }.getOrNull()

            if (!canonicalId.isNullOrBlank()) {
                return runCatching { resolveStreamUrl(canonicalId) }.getOrNull()
            }

            val searchResult = runCatching {
                ytMusicApiClient.searchAll("$artist $title".trim())
            }.getOrNull()

            val songId = searchResult?.sections
                ?.filterIsInstance<SearchResultSection.Songs>()
                ?.firstOrNull()
                ?.tracks?.firstOrNull()
                ?.videoId
                ?: searchResult?.sections
                    ?.filterIsInstance<SearchResultSection.Top>()
                    ?.firstOrNull()
                    ?.item
                    ?.let { it as? TopResultItem.TrackTop }
                    ?.track?.videoId

            if (!songId.isNullOrBlank()) {
                return runCatching { resolveStreamUrl(songId) }.getOrNull()
            }
        }

        return null
    }
}
