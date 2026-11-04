package com.stash.data.download.lossless

import java.net.URI

/**
 * The fetch plan for a single-representation DASH manifest: one init segment,
 * then [mediaUrls] in playback order. Concatenating them gives a playable fMP4.
 */
data class DashPlan(
    val initUrl: String,
    val mediaUrls: List<String>,
)

/** A manifest we cannot turn into a [DashPlan]; the download fails rather than guessing. */
class DashManifestException(message: String) : Exception(message)

/**
 * Minimal MPD reader for Tidal's audio manifests (one `SegmentTemplate` with a
 * `SegmentTimeline`, no DRM). Pure JVM — a regex pass, not an XML stack — because
 * the shape is fixed and this keeps it unit-testable without Android.
 *
 * Deliberately narrow: anything it does not understand (`r="-1"`, template
 * tokens other than `$Number$`, no timeline) is an error, so a format change
 * upstream surfaces as a failed download instead of a silently truncated file.
 */
object DashManifestParser {

    private val TEMPLATE = Regex("<(?:\\w+:)?SegmentTemplate\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val TIMELINE = Regex(
        "<(?:\\w+:)?SegmentTimeline\\b[^>]*>(.*?)</(?:\\w+:)?SegmentTimeline>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val S_ELEMENT = Regex("<(?:\\w+:)?S\\b([^>]*?)/?>", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("([\\w:]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")
    private val BASE_URL = Regex("<(?:\\w+:)?BaseURL\\b[^>]*>([^<]*)<", RegexOption.IGNORE_CASE)
    private val NUMBER_TOKEN = Regex("\\\$Number(%0(\\d+)d)?\\$")

    /** Guards against a hostile/garbled manifest asking for millions of fetches. */
    const val MAX_SEGMENTS = 10_000

    /**
     * @param mpd      Manifest XML text.
     * @param mpdUrl   Where it came from; relative URLs resolve against it (and
     *                 any `BaseURL`). Tidal's are absolute, so this rarely matters.
     */
    fun parse(mpd: String, mpdUrl: String): DashPlan {
        val templateAttrs = TEMPLATE.find(mpd)?.groupValues?.get(1)
            ?: throw DashManifestException("no SegmentTemplate")
        val attrs = attributes(templateAttrs)
        val init = attrs["initialization"]?.takeIf { it.isNotBlank() }
            ?: throw DashManifestException("no initialization URL")
        val media = attrs["media"]?.takeIf { it.isNotBlank() }
            ?: throw DashManifestException("no media template")
        if (!NUMBER_TOKEN.containsMatchIn(media)) throw DashManifestException("media template lacks \$Number\$")
        val startNumber = attrs["startNumber"]?.let {
            it.toLongOrNull() ?: throw DashManifestException("bad startNumber '$it'")
        } ?: 1L

        val timeline = TIMELINE.find(mpd)?.groupValues?.get(1)
            ?: throw DashManifestException("no SegmentTimeline")
        var count = 0L
        for (s in S_ELEMENT.findAll(timeline)) {
            val r = attributes(s.groupValues[1])["r"]?.let {
                it.toLongOrNull() ?: throw DashManifestException("bad r '$it'")
            } ?: 0L
            // r=-1 means "repeat until the period ends" — needs durations we don't model.
            if (r < 0) throw DashManifestException("open-ended repeat (r=$r) unsupported")
            count += 1 + r
            if (count > MAX_SEGMENTS) throw DashManifestException("too many segments")
        }
        if (count == 0L) throw DashManifestException("empty SegmentTimeline")

        val base = baseFor(mpd, mpdUrl)
        val mediaUrls = (0 until count).map { i -> resolve(base, expand(media, startNumber + i)) }
        return DashPlan(initUrl = resolve(base, init), mediaUrls = mediaUrls)
    }

    /**
     * Converts a single-representation fMP4 DASH [mpd] into an HLS v7 VOD playlist (`.m3u8`)
     * with `#EXT-X-MAP:URI="<initUrl>"`.
     *
     * Used on Desktop (particularly macOS, where Homebrew builds `ffmpeg`/`libmpv` without
     * `libxml2` and therefore omits the `dash` demuxer while always including the `hls` and
     * `mov,mp4` demuxers).
     */
    fun toHlsM3u8(mpd: String, mpdUrl: String): String {
        val templateAttrs = TEMPLATE.find(mpd)?.groupValues?.get(1)
            ?: throw DashManifestException("no SegmentTemplate")
        val attrs = attributes(templateAttrs)
        val init = attrs["initialization"]?.takeIf { it.isNotBlank() }
            ?: throw DashManifestException("no initialization URL")
        val media = attrs["media"]?.takeIf { it.isNotBlank() }
            ?: throw DashManifestException("no media template")
        if (!NUMBER_TOKEN.containsMatchIn(media)) throw DashManifestException("media template lacks \$Number\$")
        val startNumber = attrs["startNumber"]?.let {
            it.toLongOrNull() ?: throw DashManifestException("bad startNumber '$it'")
        } ?: 1L
        val timescale = attrs["timescale"]?.toLongOrNull()?.takeIf { it > 0L } ?: 44100L

        val timeline = TIMELINE.find(mpd)?.groupValues?.get(1)
            ?: throw DashManifestException("no SegmentTimeline")
        val base = baseFor(mpd, mpdUrl)
        val initUrl = resolve(base, init)

        val segments = ArrayList<Pair<Double, String>>()
        var curNumber = startNumber
        var maxDurSec = 1.0

        for (s in S_ELEMENT.findAll(timeline)) {
            val sAttrs = attributes(s.groupValues[1])
            val r = sAttrs["r"]?.let {
                it.toLongOrNull() ?: throw DashManifestException("bad r '$it'")
            } ?: 0L
            if (r < 0) throw DashManifestException("open-ended repeat (r=$r) unsupported")
            val d = sAttrs["d"]?.toLongOrNull()?.takeIf { it > 0L } ?: (timescale * 4L)
            val durSec = d.toDouble() / timescale.toDouble()
            if (durSec > maxDurSec) maxDurSec = durSec

            for (i in 0..r) {
                if (segments.size >= MAX_SEGMENTS) throw DashManifestException("too many segments")
                val segUrl = resolve(base, expand(media, curNumber))
                segments.add(durSec to segUrl)
                curNumber++
            }
        }
        if (segments.isEmpty()) throw DashManifestException("empty SegmentTimeline")

        val targetDuration = kotlin.math.ceil(maxDurSec).toInt().coerceAtLeast(1)
        return buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:7")
            appendLine("#EXT-X-TARGETDURATION:$targetDuration")
            appendLine("#EXT-X-MEDIA-SEQUENCE:$startNumber")
            appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
            appendLine("#EXT-X-MAP:URI=\"$initUrl\"")
            for ((durSec, segUrl) in segments) {
                appendLine("#EXTINF:${String.format(java.util.Locale.US, "%.6f", durSec)},")
                appendLine(segUrl)
            }
            appendLine("#EXT-X-ENDLIST")
        }
    }

    private fun attributes(raw: String): Map<String, String> =
        ATTR.findAll(raw).associate { m ->
            val value = m.groups[3]?.value ?: m.groups[4]?.value ?: ""
            m.groupValues[1].substringAfter(':') to decodeEntities(value)
        }

    private fun expand(template: String, number: Long): String {
        val out = NUMBER_TOKEN.replace(template) { m ->
            val width = m.groups[2]?.value?.toIntOrNull()
            if (width != null) number.toString().padStart(width, '0') else number.toString()
        }
        // Any other identifier ($RepresentationID$, $Time$...) means a manifest shape we don't handle.
        if (Regex("\\$\\w+\\$").containsMatchIn(out)) throw DashManifestException("unsupported template token")
        return out.replace("$$", "$")
    }

    /** Lazy: Tidal's URLs are absolute, so a base that java.net.URI rejects must not fail them. */
    private fun baseFor(mpd: String, mpdUrl: String): Lazy<URI> = lazy {
        try {
            val docUri = URI(mpdUrl)
            val baseText = BASE_URL.find(mpd)?.groupValues?.get(1)?.trim()?.let(::decodeEntities)
            if (baseText.isNullOrEmpty()) docUri else docUri.resolve(baseText)
        } catch (e: IllegalArgumentException) {
            throw DashManifestException("unresolvable base URL")
        } catch (e: java.net.URISyntaxException) {
            throw DashManifestException("unresolvable base URL")
        }
    }

    // Absolute URLs pass through verbatim: CDN signatures can hold characters
    // java.net.URI is strict about, and re-encoding would break the signature.
    private fun resolve(base: Lazy<URI>, ref: String): String {
        val r = ref.trim()
        if (r.startsWith("https://", ignoreCase = true) || r.startsWith("http://", ignoreCase = true)) return r
        return try {
            base.value.resolve(r).toString()
        } catch (e: IllegalArgumentException) {
            throw DashManifestException("unresolvable segment URL")
        }
    }

    /** XML attribute text is entity-encoded: Tidal's signed URLs arrive with `&amp;`. */
    internal fun decodeEntities(s: String): String =
        s.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#38;", "&")
            .replace("&amp;", "&") // last, so "&amp;lt;" stays "&lt;"
}
