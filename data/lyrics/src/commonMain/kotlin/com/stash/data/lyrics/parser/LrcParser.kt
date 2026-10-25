package com.stash.data.lyrics.parser

data class LrcLine(val timestampMs: Long, val text: String)

object LrcParser {

    /**
     * Parses an LRC body into a sorted, timestamp-tagged line list.
     *
     * Recognises:
     *   - `[mm:ss.xx]text`, `[mm:ss.xxx]text`, `[mm:ss.x]text`, `[mm:ss]text` and the
     *     colon-fraction `[mm:ss:xx]text` some sources (KuGou-style exports) write;
     *     minutes may run to three digits
     *   - multiple timestamps per line: `[00:10.00][00:30.00]text` -> two LrcLine entries
     *   - timestamp-only lines (`[01:06.80]`): kept as an EMPTY line. LRCLIB marks
     *     instrumental breaks this way; dropping them left the last sung line
     *     highlighted through a whole solo or outro. A blank stamped at the same
     *     moment as a sung line is dropped, so it can never hide that line.
     *   - `[offset:+/-N]` (milliseconds; positive = lyrics shown EARLIER, per the
     *     LRC convention), applied to every timestamp, clamped at 0.
     *
     * Strips well-known metadata tags: `[ti:...] [ar:...] [al:...] [length:...] [by:...] [offset:...]`.
     * Skips malformed lines (logs nothing — they're expected from real LRCLIB bodies).
     * A body with no sung line at all parses to nothing, as before.
     */
    fun parse(body: String): List<LrcLine> {
        if (body.isBlank()) return emptyList()
        val out = mutableListOf<LrcLine>()
        var offsetMs = 0L
        for (rawLine in body.lineSequence()) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) continue
            OFFSET_TAG.matchEntire(line.trim())?.let { offsetMs = it.groupValues[1].toLong() }
            if (META_TAG.matches(line)) continue

            // Collect every leading [mm:ss.xx] timestamp; the text is whatever follows the last one.
            val timestamps = mutableListOf<Long>()
            var idx = 0
            while (idx < line.length && line[idx] == '[') {
                val close = line.indexOf(']', idx)
                if (close == -1) break
                val token = line.substring(idx + 1, close)
                val ms = parseTimestampMs(token) ?: break
                timestamps += ms
                idx = close + 1
            }
            if (timestamps.isEmpty()) continue
            val text = line.substring(idx).trimEnd()
            val kept = if (text.isBlank()) "" else text
            timestamps.forEach { ms -> out += LrcLine(ms, kept) }
        }
        if (out.none { it.text.isNotEmpty() }) return emptyList()
        val sungAt = out.filter { it.text.isNotEmpty() }.mapTo(HashSet()) { it.timestampMs }
        return out
            .filter { it.text.isNotEmpty() || it.timestampMs !in sungAt }
            .map { if (offsetMs == 0L) it else it.copy(timestampMs = (it.timestampMs - offsetMs).coerceAtLeast(0L)) }
            .sortedBy { it.timestampMs }
    }

    private val META_TAG = Regex("""^\[(ti|ar|al|length|by|offset|au|re|ve):.*]\s*$""", RegexOption.IGNORE_CASE)
    private val OFFSET_TAG = Regex("""^\[offset:\s*([+-]?\d{1,9})\s*]$""", RegexOption.IGNORE_CASE)
    private val TIMESTAMP = Regex("""^(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?$""")

    private fun parseTimestampMs(token: String): Long? {
        val match = TIMESTAMP.matchEntire(token) ?: return null
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val frac = match.groupValues[3]
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            3 -> frac.toLong()
            else -> return null
        }
        return (minutes * 60_000) + (seconds * 1_000) + fracMs
    }
}
