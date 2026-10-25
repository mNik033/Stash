package com.stash.feature.nowplaying.ui

import com.stash.data.lyrics.parser.LrcLine
import com.stash.data.lyrics.parser.TtmlLine
import kotlin.math.PI
import kotlin.math.sin

/*
 * Instrumental breaks: the solos, bridges and intros with nothing to sing. Pure, so the rules are
 * unit-tested (LyricsBreaksTest); the ♪ row that draws them is [InstrumentalBreakNotes].
 *
 * LRC marks a break with a timestamp-only line, which [com.stash.data.lyrics.parser.LrcParser]
 * keeps as a blank [LrcLine]. Word-synced TTML has no such mark: its breaks are the gaps between
 * lines, which the syllable renderer already fills with its interlude dots.
 */

/**
 * A gap shorter than this is a breath, not a break: no ♪ row, and the line before it stays live
 * through it. The word-synced view's interlude dots use the same 3 s (Spicy Lyrics' threshold).
 */
internal const val INSTRUMENTAL_MIN_GAP_MS = 3_000L

/** What TalkBack reads for the ♪ row. */
internal const val INSTRUMENTAL_BREAK_DESCRIPTION = "Instrumental break"

/**
 * One instrumental break: the music from [startMs] until the next sung line at [endMs]. [endMs]
 * is null for an outro (a break with no sung line after it), which runs to the end of the song.
 */
internal data class InstrumentalBreak(val startMs: Long, val endMs: Long?) {

    /** How long the break lasts; null for an outro, whose end isn't in the lyrics. */
    val durationMs: Long? get() = endMs?.let { it - startMs }

    /**
     * Long enough to show as a ♪ row: at least [INSTRUMENTAL_MIN_GAP_MS]. An outro always is: it
     * has no end to measure, and the song closing on music is exactly what the row says.
     */
    val isLong: Boolean get() = durationMs?.let { it >= INSTRUMENTAL_MIN_GAP_MS } ?: true

    /** How far through the break [positionMs] is, 0..1; null for an outro (nothing to count down to). */
    fun progress(positionMs: Long): Float? {
        val length = durationMs ?: return null
        if (length <= 0L) return 1f
        return ((positionMs - startMs).toFloat() / length).coerceIn(0f, 1f)
    }
}

/**
 * The break the line at [index] opens, or null when that line is sung (or out of range). A blank
 * line lasts until the next sung line; blank lines in between are part of the same break.
 */
internal fun instrumentalBreakAt(lines: List<LrcLine>, index: Int): InstrumentalBreak? {
    val line = lines.getOrNull(index) ?: return null
    if (line.text.isNotBlank()) return null
    var next: Long? = null
    for (i in index + 1 until lines.size) {
        if (lines[i].text.isNotBlank()) { next = lines[i].timestampMs; break }
    }
    return InstrumentalBreak(line.timestampMs, next)
}

/**
 * The line-synced lyrics as the lyrics view and the live bar show them. One list for both, so
 * they never disagree on the live line ([currentLineIndex] runs on it):
 *  - a long intro opens with a break at 0, so the ♪ row (not the first line, lit before anyone
 *    sings it) is live until the singing starts, as in the word-synced view;
 *  - a long break keeps its blank line, the ♪ row; a run of blanks folds into the first;
 *  - a short gap's blank is dropped, so the line before it stays live through the breath rather
 *    than going dark for a second or two.
 * Lyrics without breaks come back unchanged.
 */
internal fun linesWithBreaks(lines: List<LrcLine>): List<LrcLine> {
    val firstSung = lines.firstOrNull { it.text.isNotBlank() } ?: return lines
    val out = ArrayList<LrcLine>(lines.size + 1)
    if (firstSung.timestampMs >= INSTRUMENTAL_MIN_GAP_MS) out += LrcLine(0L, "")
    lines.forEachIndexed { i, line ->
        when {
            line.text.isNotBlank() -> out += line
            // Already in a break (a blank before this one, or the intro's): one row per break.
            out.lastOrNull()?.text?.isBlank() == true -> Unit
            instrumentalBreakAt(lines, i)?.isLong == true -> out += line
        }
    }
    return out
}

/**
 * The break word-synced lyrics are in at [posMs], or null while a line is being sung (or the
 * lyrics are over). The same breaks the syllable renderer draws its interlude dots for: a gap of
 * at least [INSTRUMENTAL_MIN_GAP_MS] between lines, or an intro that long. Overlapping lines
 * (duets, adlibs) count as sung until the last of them ends. After the last line it's null: the
 * view shows no dots there either, and the bar keeps the final line.
 */
internal fun ttmlBreakAt(lines: List<TtmlLine>, posMs: Long): InstrumentalBreak? {
    var sungUntil = -1L
    var next: TtmlLine? = null
    for (line in lines) {
        if (line.lead.startMs <= posMs) {
            sungUntil = maxOf(sungUntil, line.lead.endMs)
        } else if (next == null || line.lead.startMs < next.lead.startMs) {
            next = line
        }
    }
    val nextStart = next?.lead?.startMs ?: return null
    if (sungUntil > posMs) return null
    val start = sungUntil.coerceAtLeast(0L)
    return InstrumentalBreak(start, nextStart).takeIf { it.isLong }
}

/** Notes in the ♪ row. */
internal const val BREAK_NOTES = 3

/** A note not reached yet in a live break, and the low point of a reached note's breath. */
internal const val BREAK_NOTE_DIM = 0.35f
internal const val BREAK_NOTE_BREATH_LOW = 0.6f

/**
 * How lit note [index] of a live break's ♪ row is. The notes come up one by one as the break
 * runs (each third of it, like Apple Music's dots), so a glance says how soon the singing is back;
 * an outro, with no end to count to, has them all up. Reached notes breathe gently, each a step
 * behind the one before, as a slow wave: [breathPhase] runs 0..1 per breath, null holds them
 * still (Remove animations).
 */
internal fun breakNoteAlpha(index: Int, progress: Float?, breathPhase: Float?): Float {
    val reached = progress == null || progress >= index.toFloat() / BREAK_NOTES
    if (!reached) return BREAK_NOTE_DIM
    if (breathPhase == null) return 1f
    val wave = 0.5f + 0.5f * sin(2.0 * PI * (breathPhase - index / (2.0 * BREAK_NOTES))).toFloat()
    return BREAK_NOTE_BREATH_LOW + (1f - BREAK_NOTE_BREATH_LOW) * wave
}
