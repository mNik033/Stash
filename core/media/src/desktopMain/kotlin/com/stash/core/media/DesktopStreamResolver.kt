package com.stash.core.media

import com.stash.core.common.Log
import com.stash.core.data.prefs.QualityPreference
import com.stash.core.model.QualityTier
import com.stash.core.model.Track
import com.stash.core.model.YouTubeVideoId
import com.stash.data.download.jiosaavn.JioSaavnResolver
import com.stash.data.download.jiosaavn.toJioSaavnKbps
import com.stash.data.download.lossless.LosslessSourcePreferences
import com.stash.data.download.lossless.LosslessSourceRegistry
import com.stash.data.download.lossless.TrackQuery
import com.stash.data.download.lossless.tidal.TidalAac
import com.stash.data.download.lossless.tidal.TidalAacResolver
import com.stash.data.download.preview.AudioUrlTailProbe
import com.stash.data.download.ytdlp.YtDlpRunner
import com.stash.data.ytmusic.InnerTubeClient
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.SearchResultSection
import com.stash.data.ytmusic.model.TopResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedDesktopStream(
    val uri: String,
    val fileFormat: String,
    val qualityKbps: Int = 0,
    val sampleRateHz: Int? = null,
    val bitsPerSample: Int? = null,
    val streamOrigin: String,
)

@Singleton
class DesktopStreamResolver @Inject constructor(
    private val innerTubeClient: InnerTubeClient,
    private val ytMusicApiClient: YTMusicApiClient,
    private val tailProbe: AudioUrlTailProbe,
    private val ytDlpRunner: YtDlpRunner,
    private val losslessSourceRegistry: LosslessSourceRegistry,
    private val losslessPrefs: LosslessSourcePreferences,
    private val qualityPreference: QualityPreference,
    private val tidalAacResolver: TidalAacResolver,
    private val jioSaavnResolver: JioSaavnResolver,
) {
    companion object {
        private const val TAG = "DesktopStreamResolver"
        private const val CACHE_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours
        private const val MAX_CACHE_ENTRIES = 256
    }

    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, Pair<ResolvedDesktopStream, Long>>()
    private val canonicalIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun invalidate(track: Track) {
        val rawId = track.youtubeId
        if (!rawId.isNullOrBlank()) {
            streamCache.remove(rawId)
        }
        val artist = track.artist.trim().lowercase()
        val title = track.title.trim().lowercase()
        val matchKey = "$artist::$title"
        canonicalIdCache.remove(matchKey)?.let { streamCache.remove(it) }
    }

    suspend fun resolveStreamUrl(videoId: String, forceFresh: Boolean = false): String? {
        return resolveYouTubeStream(videoId, QualityTier.MAX, forceFresh)?.uri
    }

    suspend fun resolveYouTubeStream(
        videoId: String,
        qualityTier: QualityTier = QualityTier.MAX,
        forceFresh: Boolean = false,
    ): ResolvedDesktopStream? = withContext(Dispatchers.IO) {
        require(YouTubeVideoId.isValid(videoId)) { "Invalid YouTube video id: $videoId" }

        val now = System.currentTimeMillis()
        val cacheKey = "${videoId}_${qualityTier.name}"
        if (!forceFresh) {
            val cached = streamCache[cacheKey] ?: streamCache[videoId]
            if (cached != null && now < cached.second) {
                Log.d(TAG, "Stream cache hit for $videoId (${cached.first.fileFormat} ${cached.first.qualityKbps}k)")
                return@withContext cached.first
            }
        }

        var chosenMeta: Triple<String, Int, Int?>? = null
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

            val sortedFormats = when (qualityTier) {
                QualityTier.MAX, QualityTier.BEST -> {
                    audioFormats.sortedWith(
                        compareByDescending<JsonObject> {
                            (it["mimeType"]?.jsonPrimitive?.content ?: "").contains("opus")
                        }.thenByDescending {
                            it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                        }
                    )
                }
                QualityTier.HIGH -> {
                    audioFormats.sortedByDescending {
                        val br = (it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) / 1000
                        if (br <= 160) br else -br
                    }
                }
                QualityTier.NORMAL -> {
                    audioFormats.sortedByDescending {
                        val br = (it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) / 1000
                        if (br <= 128) br else -br
                    }
                }
                QualityTier.LOW -> {
                    audioFormats.sortedByDescending {
                        val br = (it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) / 1000
                        if (br <= 64) br else -br
                    }
                }
            }

            for (fmt in sortedFormats) {
                val rawUrl = fmt["url"]?.jsonPrimitive?.content ?: continue
                val streamUrl = if (streamPot != null && !rawUrl.contains("pot=")) {
                    if ('?' in rawUrl) "$rawUrl&pot=$streamPot" else "$rawUrl?pot=$streamPot"
                } else {
                    rawUrl
                }

                val contentLength = fmt["contentLength"]?.jsonPrimitive?.content?.toLongOrNull()
                if (tailProbe.servesFullFile(streamUrl, contentLength)) {
                    val mime = fmt["mimeType"]?.jsonPrimitive?.content.orEmpty()
                    val codec = when {
                        mime.contains("opus", ignoreCase = true) -> "opus"
                        mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "m4a"
                        else -> "opus"
                    }
                    val bitrate = ((fmt["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) / 1000L).toInt()
                    val sampleRate = fmt["audioSampleRate"]?.jsonPrimitive?.content?.toIntOrNull()
                    chosenMeta = Triple(codec, bitrate, sampleRate)
                    return@playerForAudio streamUrl
                }
                Log.i(TAG, "tail probe rejected URL for $videoId; testing next format/variant")
            }

            null
        }

        val resolved = if (stream != null) {
            val (codec, bitrate, sampleRate) = chosenMeta ?: Triple("opus", 160, 48000)
            ResolvedDesktopStream(
                uri = stream.url,
                fileFormat = codec,
                qualityKbps = bitrate,
                sampleRateHz = sampleRate,
                bitsPerSample = 16,
                streamOrigin = "youtube",
            )
        } else {
            resolveViaYtDlpFallback(videoId, qualityTier)
        }

        if (resolved != null) {
            if (streamCache.size >= MAX_CACHE_ENTRIES) {
                streamCache.entries.removeIf { it.value.second < now }
                if (streamCache.size >= MAX_CACHE_ENTRIES) {
                    streamCache.keys.firstOrNull()?.let { streamCache.remove(it) }
                }
            }
            streamCache[cacheKey] = Pair(resolved, now + CACHE_TTL_MS)
            streamCache[videoId] = Pair(resolved, now + CACHE_TTL_MS)
            Log.i(TAG, "Resolved YouTube stream for $videoId (${resolved.fileFormat} ${resolved.qualityKbps}k) -> ${resolved.uri.take(60)}...")
        } else {
            Log.w(TAG, "Failed to resolve playable YouTube stream for $videoId")
        }
        resolved
    }

    private suspend fun resolveViaYtDlpFallback(videoId: String, qualityTier: QualityTier): ResolvedDesktopStream? {
        val watchUrl = YouTubeVideoId.watchUrl(videoId) ?: return null
        val formatSelector = when (qualityTier) {
            QualityTier.MAX, QualityTier.BEST -> "251/140/250/bestaudio/best"
            QualityTier.HIGH -> "251/140/250/bestaudio[abr<=160]/best"
            QualityTier.NORMAL -> "250/140/bestaudio[abr<=128]/best"
            QualityTier.LOW -> "249/bestaudio[abr<=64]/best"
        }
        val resp = runCatching {
            ytDlpRunner.execute(
                listOf(
                    "-f", formatSelector,
                    "--no-playlist",
                    "--no-download",
                    "--print", "%(acodec)s\t%(abr)s\t%(asr)s\t%(url)s",
                    "--",
                    watchUrl,
                )
            )
        }.getOrNull() ?: return null
        if (resp.exitCode != 0) return null

        val line = resp.out.lineSequence().map { it.trim() }.firstOrNull { it.contains("http") } ?: return null
        val parts = line.split('\t')
        val (codecRaw, abrRaw, asrRaw, url) = when (parts.size) {
            4 -> Tuple4(parts[0], parts[1], parts[2], parts[3])
            1 -> Tuple4("opus", "160", "48000", parts[0])
            else -> return null
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null

        val codec = when {
            codecRaw.contains("opus", ignoreCase = true) -> "opus"
            codecRaw.contains("mp4a", ignoreCase = true) || codecRaw.contains("aac", ignoreCase = true) -> "m4a"
            codecRaw.contains("mp3", ignoreCase = true) -> "mp3"
            else -> "opus"
        }
        val abr = abrRaw.toDoubleOrNull()?.toInt() ?: 160
        val asr = asrRaw.toIntOrNull() ?: 48000

        return ResolvedDesktopStream(
            uri = url,
            fileFormat = codec,
            qualityKbps = abr,
            sampleRateHz = asr,
            bitsPerSample = 16,
            streamOrigin = "youtube",
        )
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    suspend fun resolveForTrack(track: Track): ResolvedDesktopStream? = withContext(Dispatchers.IO) {
        // 1. If downloaded file exists on disk, play locally
        val filePath = track.filePath
        if (!filePath.isNullOrBlank()) {
            val file = File(filePath)
            if (file.exists() && file.length() > 0L) {
                val ext = file.extension.lowercase().ifBlank { track.fileFormat }
                return@withContext ResolvedDesktopStream(
                    uri = filePath,
                    fileFormat = ext,
                    qualityKbps = track.qualityKbps,
                    sampleRateHz = track.sampleRateHz,
                    bitsPerSample = track.bitsPerSample,
                    streamOrigin = track.streamOrigin ?: "local",
                )
            }
        }

        val qualityTier = qualityPreference.qualityTier.first()
        val losslessEnabled = losslessPrefs.enabledNow()

        val trackQuery = TrackQuery(
            artist = track.artist,
            title = track.title,
            album = track.album.takeIf { it.isNotBlank() },
            isrc = track.isrc,
            durationMs = track.durationMs.takeIf { it > 0 },
            explicit = track.explicit,
            spotifyUri = track.spotifyUri,
            trackId = track.id.takeIf { it > 0 },
        )

        // 2. Lossless Waterfall (Qobuz / Tidal FLAC / Relay via LosslessSourceRegistry)
        if ((qualityTier == QualityTier.MAX || qualityTier == QualityTier.BEST) && losslessEnabled) {
            val losslessResult = runCatching {
                losslessSourceRegistry.resolve(trackQuery, bypassRateLimit = true)
            }.getOrNull()

            if (losslessResult != null && !losslessResult.downloadUrl.isNullOrBlank()) {
                Log.i(TAG, "Resolved lossless stream for '${track.title}' via ${losslessResult.sourceId}")
                return@withContext ResolvedDesktopStream(
                    uri = losslessResult.downloadUrl,
                    fileFormat = losslessResult.format.codec.lowercase(),
                    qualityKbps = losslessResult.format.bitrateKbps,
                    sampleRateHz = losslessResult.format.sampleRateHz.takeIf { it > 0 },
                    bitsPerSample = losslessResult.format.bitsPerSample.takeIf { it > 0 },
                    streamOrigin = losslessResult.sourceId,
                )
            }
        }

        // 3. High-Bitrate Lossy AAC Fallback (Tidal 320k AAC & JioSaavn 320k AAC)
        if (qualityTier == QualityTier.MAX || qualityTier == QualityTier.BEST) {
            val tidalAacAllowed = losslessPrefs.tidalAacFallbackNow() || !losslessEnabled
            if (tidalAacAllowed) {
                val tidalResult = runCatching {
                    tidalAacResolver.resolve(trackQuery, foreground = true)
                }.getOrNull()

                if (tidalResult is TidalAac.Found) {
                    Log.i(TAG, "Resolved Tidal AAC stream for '${track.title}'")
                    return@withContext ResolvedDesktopStream(
                        uri = tidalResult.source.downloadUrl,
                        fileFormat = "m4a",
                        qualityKbps = 320,
                        sampleRateHz = 44100,
                        bitsPerSample = 16,
                        streamOrigin = "tidal",
                    )
                }
            }

            // JioSaavn AAC fallback
            val jioSaavnResult = runCatching {
                jioSaavnResolver.resolve(trackQuery, kbps = qualityTier.toJioSaavnKbps(), bypassRateLimit = true)
            }.getOrNull()

            if (jioSaavnResult != null && !jioSaavnResult.downloadUrl.isNullOrBlank()) {
                Log.i(TAG, "Resolved JioSaavn AAC stream for '${track.title}'")
                return@withContext ResolvedDesktopStream(
                    uri = jioSaavnResult.downloadUrl,
                    fileFormat = "m4a",
                    qualityKbps = jioSaavnResult.format.bitrateKbps,
                    sampleRateHz = jioSaavnResult.format.sampleRateHz.takeIf { it > 0 } ?: 44100,
                    bitsPerSample = 16,
                    streamOrigin = "jiosaavn",
                )
            }
        }

        // 4. YouTube InnerTube stream resolution (filtered by QualityTier)
        val ytId = track.youtubeId?.takeIf { YouTubeVideoId.isValid(it) } ?: findYoutubeIdForTrack(track)
        if (ytId != null) {
            val ytStream = resolveYouTubeStream(ytId, qualityTier)
            if (ytStream != null) {
                return@withContext ytStream
            }
        }

        null
    }

    private suspend fun findYoutubeIdForTrack(track: Track): String? {
        val artist = track.artist.trim()
        val title = track.title.trim()
        if (artist.isBlank() && title.isBlank()) return null

        val key = "${artist.lowercase()}::${title.lowercase()}"
        val cachedId = canonicalIdCache[key]
        if (!cachedId.isNullOrBlank()) return cachedId

        val canonicalId = runCatching {
            ytMusicApiClient.searchCanonicalVideoId(artist, title)
        }.getOrNull()

        if (!canonicalId.isNullOrBlank()) {
            canonicalIdCache[key] = canonicalId
            return canonicalId
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
            canonicalIdCache[key] = songId
            return songId
        }

        return null
    }
}
