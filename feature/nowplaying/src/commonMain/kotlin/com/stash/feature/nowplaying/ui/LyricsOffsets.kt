package com.stash.feature.nowplaying.ui

/*
 * Lyrics sync offsets: the song's own (stored on its lyrics row, for an LRC that runs early or
 * late) plus the audio output's (Bluetooth headphones play later than wired ones, and Media3's
 * position may or may not account for that, so nothing is guessed: the listener sets it once per
 * output). Pure, so the combining and clamping are unit-tested (LyricsOffsetsTest). Signed ms;
 * positive delays the lyrics.
 */

/** Cap on a song's own offset (the sync dialog's "further out" field), and on the two combined. */
internal const val LYRICS_OFFSET_CAP_MS = 60_000L

/**
 * Cap on an output's offset. No real output lags by more (Bluetooth runs 0.1-0.5 s), and the
 * offset shifts every song on it, so a typo like "30" must not push them all half a minute.
 */
internal const val OUTPUT_OFFSET_CAP_MS = 10_000L

/**
 * An audio output as lyrics offsets know it: [key] identifies it across sessions (its kind and
 * device name, so "Bluetooth WH-1000XM4" and "Wired headphones" never share an offset), [name] is
 * what the sync dialog shows ("WH-1000XM4", "Phone speaker").
 */
data class LyricsOutput(val key: String, val name: String)

/** The output playing now and the offset the listener set for it (0 when none). */
data class LyricsOutputOffset(val output: LyricsOutput, val offsetMs: Long)

/** The [LyricsOutput] for a device of [kind] (a stable kind name, e.g. "BLUETOOTH") called [name]. */
internal fun lyricsOutputOf(kind: String, name: String): LyricsOutput {
    val shown = name.trim()
    return LyricsOutput(key = "$kind:$shown", name = shown)
}

/** Keeps an output's offset within [OUTPUT_OFFSET_CAP_MS]. */
internal fun clampOutputOffsetMs(offsetMs: Long): Long =
    offsetMs.coerceIn(-OUTPUT_OFFSET_CAP_MS, OUTPUT_OFFSET_CAP_MS)

/**
 * The offset the lyrics run on: the song's own plus the output's, either missing counting as 0
 * (a stream has no row for a song offset; the output is unknown until something plays), the sum
 * held within [LYRICS_OFFSET_CAP_MS] like a song offset alone.
 */
internal fun effectiveLyricsOffsetMs(songOffsetMs: Long?, outputOffsetMs: Long?): Long =
    ((songOffsetMs ?: 0L) + (outputOffsetMs ?: 0L)).coerceIn(-LYRICS_OFFSET_CAP_MS, LYRICS_OFFSET_CAP_MS)

/** Where the lyrics are at [playerPositionMs] under [offsetMs]: a positive offset holds them back. */
internal fun lyricsPositionMs(playerPositionMs: Long, offsetMs: Long): Long =
    (playerPositionMs - offsetMs).coerceAtLeast(0L)

/**
 * Where to seek the player so a tapped line at [lineMs] starts as it's heard under [offsetMs]:
 * the inverse of [lyricsPositionMs]. Seeking to the bare timestamp would light the line before it
 * with a positive offset (or skip into it with a negative one).
 */
internal fun lyricsSeekTargetMs(lineMs: Long, offsetMs: Long): Long =
    (lineMs + offsetMs).coerceAtLeast(0L)
