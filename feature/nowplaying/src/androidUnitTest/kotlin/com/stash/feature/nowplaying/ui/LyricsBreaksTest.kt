package com.stash.feature.nowplaying.ui

import com.stash.data.lyrics.parser.LrcLine
import com.stash.data.lyrics.parser.LrcParser
import com.stash.data.lyrics.parser.TtmlGroup
import com.stash.data.lyrics.parser.TtmlLine
import com.stash.data.lyrics.parser.TtmlSyllable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsBreaksTest {

    private fun sung(ms: Long, text: String = "la") = LrcLine(ms, text)
    private fun blank(ms: Long) = LrcLine(ms, "")

    // ── instrumentalBreakAt ──────────────────────────────────────────────────────────────────

    @Test fun `a sung line is no break`() {
        assertNull(instrumentalBreakAt(listOf(sung(0), sung(5_000)), 0))
    }

    @Test fun `out of range is no break`() {
        assertNull(instrumentalBreakAt(listOf(blank(0)), 3))
        assertNull(instrumentalBreakAt(listOf(blank(0)), -1))
    }

    @Test fun `a blank line runs until the next sung line`() {
        val lines = listOf(sung(0), blank(10_000), sung(25_000))
        val gap = instrumentalBreakAt(lines, 1)!!
        assertEquals(InstrumentalBreak(10_000, 25_000), gap)
        assertEquals(15_000L, gap.durationMs)
        assertTrue(gap.isLong)
    }

    @Test fun `blank lines after a break's first are part of it`() {
        val lines = listOf(sung(0), blank(10_000), blank(14_000), sung(25_000))
        assertEquals(25_000L, instrumentalBreakAt(lines, 1)!!.endMs)
    }

    @Test fun `a trailing blank is an outro with no end, and always long`() {
        val gap = instrumentalBreakAt(listOf(sung(0), blank(200_000)), 1)!!
        assertNull(gap.endMs)
        assertNull(gap.durationMs)
        assertTrue(gap.isLong)
        assertNull(gap.progress(250_000))
    }

    @Test fun `a gap under three seconds is a breath`() {
        assertFalse(InstrumentalBreak(10_000, 12_999).isLong)
        assertTrue(InstrumentalBreak(10_000, 13_000).isLong)
    }

    @Test fun `progress runs 0 to 1 across the break and clamps outside it`() {
        val gap = InstrumentalBreak(10_000, 20_000)
        assertEquals(0f, gap.progress(5_000)!!, 0f)
        assertEquals(0f, gap.progress(10_000)!!, 0f)
        assertEquals(0.5f, gap.progress(15_000)!!, 1e-6f)
        assertEquals(1f, gap.progress(30_000)!!, 0f)
    }

    @Test fun `a zero-length break counts as done`() {
        assertEquals(1f, InstrumentalBreak(10_000, 10_000).progress(10_000)!!, 0f)
    }

    // ── linesWithBreaks ──────────────────────────────────────────────────────────────────────

    @Test fun `lyrics without breaks come back unchanged`() {
        val lines = listOf(sung(0, "one"), sung(2_000, "two"))
        assertEquals(lines, linesWithBreaks(lines))
    }

    @Test fun `a long break keeps its blank line`() {
        val lines = listOf(sung(0, "one"), blank(4_000), sung(20_000, "two"))
        assertEquals(lines, linesWithBreaks(lines))
    }

    @Test fun `a short gap's blank is dropped so the line before it stays live`() {
        val lines = listOf(sung(0, "one"), blank(4_000), sung(5_500, "two"))
        val shown = linesWithBreaks(lines)
        assertEquals(listOf(sung(0, "one"), sung(5_500, "two")), shown)
        assertEquals(0, currentLineIndex(shown, 4_500))
    }

    @Test fun `a run of blanks folds into one break`() {
        val lines = listOf(sung(0, "one"), blank(4_000), blank(9_000), blank(12_000), sung(20_000, "two"))
        assertEquals(listOf(sung(0, "one"), blank(4_000), sung(20_000, "two")), linesWithBreaks(lines))
    }

    @Test fun `a long intro opens with a break at the start`() {
        val lines = listOf(sung(12_000, "one"), sung(15_000, "two"))
        val shown = linesWithBreaks(lines)
        assertEquals(listOf(blank(0), sung(12_000, "one"), sung(15_000, "two")), shown)
        // The ♪ row, not the first line, is live until the singing starts.
        assertEquals(0, currentLineIndex(shown, 6_000))
        assertEquals(1, currentLineIndex(shown, 12_000))
    }

    @Test fun `an intro's own blank folds into the opening break`() {
        val lines = listOf(blank(1_000), sung(12_000, "one"))
        assertEquals(listOf(blank(0), sung(12_000, "one")), linesWithBreaks(lines))
    }

    @Test fun `a short intro adds nothing and drops its blank`() {
        assertEquals(listOf(sung(2_000, "one")), linesWithBreaks(listOf(blank(0), sung(2_000, "one"))))
        assertEquals(listOf(sung(2_999, "one")), linesWithBreaks(listOf(sung(2_999, "one"))))
    }

    @Test fun `an outro blank stays`() {
        val lines = listOf(sung(0, "one"), blank(180_000))
        assertEquals(lines, linesWithBreaks(lines))
    }

    @Test fun `nothing sung comes back as is`() {
        val empty = emptyList<LrcLine>()
        assertSame(empty, linesWithBreaks(empty))
    }

    @Test fun `the live line goes to the break once it starts, dimming the sung line before it`() {
        val shown = linesWithBreaks(listOf(sung(0, "one"), blank(4_000), sung(20_000, "two")))
        assertEquals(0, currentLineIndex(shown, 3_999))
        assertEquals(1, currentLineIndex(shown, 4_000))
        assertEquals(2, currentLineIndex(shown, 20_000))
    }

    @Test fun `LRCLIB's timestamp-only break lines reach the view as a break`() {
        val parsed = LrcParser.parse("[00:01.00]one\n[00:05.00]\n[00:30.00]two")
        val shown = linesWithBreaks(parsed)
        assertEquals(3, shown.size)
        assertEquals(InstrumentalBreak(5_000, 30_000), instrumentalBreakAt(shown, 1))
    }

    // ── ttmlBreakAt ──────────────────────────────────────────────────────────────────────────

    private fun line(start: Long, end: Long) = TtmlLine(
        oppositeAligned = false,
        lead = TtmlGroup(listOf(TtmlSyllable("la", false, start, end)), start, end),
        background = emptyList(),
    )

    @Test fun `a word-synced line being sung is no break`() {
        val lines = listOf(line(1_000, 4_000), line(20_000, 22_000))
        assertNull(ttmlBreakAt(lines, 2_000))
    }

    @Test fun `a long gap between word-synced lines is a break`() {
        val lines = listOf(line(1_000, 4_000), line(20_000, 22_000))
        assertEquals(InstrumentalBreak(4_000, 20_000), ttmlBreakAt(lines, 4_000))
        assertEquals(InstrumentalBreak(4_000, 20_000), ttmlBreakAt(lines, 19_999))
        assertNull(ttmlBreakAt(lines, 20_000))
    }

    @Test fun `a short gap between word-synced lines is not`() {
        val lines = listOf(line(1_000, 4_000), line(6_000, 8_000))
        assertNull(ttmlBreakAt(lines, 5_000))
    }

    @Test fun `a long word-synced intro is a break from the start`() {
        val lines = listOf(line(8_000, 10_000))
        assertEquals(InstrumentalBreak(0, 8_000), ttmlBreakAt(lines, 1_000))
        assertNull(ttmlBreakAt(listOf(line(2_000, 4_000)), 1_000))
    }

    @Test fun `after the last word-synced line there is no break`() {
        assertNull(ttmlBreakAt(listOf(line(1_000, 4_000)), 60_000))
    }

    @Test fun `an overlapping line keeps the gap closed until it ends`() {
        // A duet: the second voice runs past the first, so the break starts when it stops.
        val lines = listOf(line(1_000, 4_000), line(2_000, 9_000), line(20_000, 22_000))
        assertNull(ttmlBreakAt(lines, 6_000))
        assertEquals(InstrumentalBreak(9_000, 20_000), ttmlBreakAt(lines, 10_000))
    }

    // ── breakNoteAlpha ───────────────────────────────────────────────────────────────────────

    @Test fun `notes come up one by one as the break runs`() {
        assertEquals(1f, breakNoteAlpha(0, progress = 0f, breathPhase = null), 0f)
        assertEquals(BREAK_NOTE_DIM, breakNoteAlpha(1, progress = 0.2f, breathPhase = null), 0f)
        assertEquals(1f, breakNoteAlpha(1, progress = 0.34f, breathPhase = null), 0f)
        assertEquals(BREAK_NOTE_DIM, breakNoteAlpha(2, progress = 0.5f, breathPhase = null), 0f)
        assertEquals(1f, breakNoteAlpha(2, progress = 0.7f, breathPhase = null), 0f)
    }

    @Test fun `an outro has every note up`() {
        for (note in 0 until BREAK_NOTES) assertEquals(1f, breakNoteAlpha(note, progress = null, breathPhase = null), 0f)
    }

    @Test fun `a reached note breathes between its low and full`() {
        for (step in 0..20) {
            val a = breakNoteAlpha(0, progress = 1f, breathPhase = step / 20f)
            assertTrue("alpha $a at $step", a in BREAK_NOTE_BREATH_LOW - 1e-6f..1f + 1e-6f)
        }
    }

    @Test fun `each note breathes a step behind the one before`() {
        val phase = 0.25f
        assertTrue(breakNoteAlpha(0, 1f, phase) != breakNoteAlpha(1, 1f, phase))
        // Note 1 a sixth of a breath later reaches where note 0 was.
        assertEquals(breakNoteAlpha(0, 1f, phase), breakNoteAlpha(1, 1f, phase + 1f / 6f), 1e-5f)
    }

    @Test fun `an unreached note does not breathe`() {
        assertEquals(BREAK_NOTE_DIM, breakNoteAlpha(2, progress = 0f, breathPhase = 0.25f), 0f)
    }
}
