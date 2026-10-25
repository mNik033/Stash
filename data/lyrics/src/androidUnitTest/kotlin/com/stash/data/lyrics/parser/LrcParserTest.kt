package com.stash.data.lyrics.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test fun `parses single line with hundredths`() {
        val body = "[00:12.34]hello world"
        val lines = LrcParser.parse(body)
        assertEquals(1, lines.size)
        assertEquals(12_340L, lines[0].timestampMs)
        assertEquals("hello world", lines[0].text)
    }

    @Test fun `parses single line with milliseconds`() {
        val body = "[00:12.345]hello world"
        val lines = LrcParser.parse(body)
        assertEquals(1, lines.size)
        assertEquals(12_345L, lines[0].timestampMs)
    }

    @Test fun `sorts lines by timestamp`() {
        val body = "[01:00.00]b\n[00:00.00]a\n[00:30.00]middle"
        val lines = LrcParser.parse(body)
        assertEquals(listOf("a", "middle", "b"), lines.map { it.text })
    }

    @Test fun `expands multi-timestamp lines`() {
        val body = "[00:10.00][00:30.00]chorus"
        val lines = LrcParser.parse(body)
        assertEquals(2, lines.size)
        assertEquals(10_000L, lines[0].timestampMs)
        assertEquals(30_000L, lines[1].timestampMs)
        assertTrue(lines.all { it.text == "chorus" })
    }

    @Test fun `strips standard metadata tags`() {
        val body = "[ti:Title]\n[ar:Artist]\n[al:Album]\n[length:03:45]\n[by:Author]\n[offset:+0]\n[00:10.00]line"
        val lines = LrcParser.parse(body)
        assertEquals(1, lines.size)
        assertEquals("line", lines[0].text)
    }

    @Test fun `skips malformed lines without throwing`() {
        val body = "garbage\n[00:10.00]ok\nalso garbage\n[bad timestamp]nope\n[00:20.00]ok2"
        val lines = LrcParser.parse(body)
        assertEquals(listOf("ok", "ok2"), lines.map { it.text })
    }

    @Test fun `returns empty list for completely malformed input`() {
        assertTrue(LrcParser.parse("not lyrics at all").isEmpty())
        assertTrue(LrcParser.parse("").isEmpty())
    }

    /**
     * LRCLIB marks an instrumental break with a bare timestamp. Dropped, the
     * last sung line stayed highlighted for the whole solo.
     */
    @Test fun `keeps a timestamp-only line as an empty break`() {
        val body = "[01:02.10]last verse line\n[01:06.80]\n[01:37.00]next line"
        val lines = LrcParser.parse(body)
        assertEquals(listOf("last verse line", "", "next line"), lines.map { it.text })
        assertEquals(66_800L, lines[1].timestampMs)
    }

    @Test fun `a blank stamped with a sung line cannot hide it`() {
        val lines = LrcParser.parse("[00:10.00]\n[00:10.00]sung\n[00:20.00]")
        assertEquals(listOf(10_000L to "sung", 20_000L to ""), lines.map { it.timestampMs to it.text })
    }

    @Test fun `a body of breaks only parses to nothing`() {
        assertTrue(LrcParser.parse("[00:10.00]\n[00:20.00]   ").isEmpty())
    }

    @Test fun `a positive offset shows lyrics earlier, a negative one later`() {
        val earlier = LrcParser.parse("[offset:+500]\n[00:10.00]a\n[00:00.20]b")
        assertEquals(listOf(0L, 9_500L), earlier.map { it.timestampMs })

        val later = LrcParser.parse("[00:10.00]a\n[offset:-250]")
        assertEquals(10_250L, later.single().timestampMs)
    }

    @Test fun `accepts whole-second, colon-fraction, tenths and three-digit-minute stamps`() {
        val body = "[00:05]a\n[00:06:50]b\n[00:07.5]c\n[100:00.00]d"
        val lines = LrcParser.parse(body)
        assertEquals(
            listOf(5_000L to "a", 6_500L to "b", 7_500L to "c", 6_000_000L to "d"),
            lines.map { it.timestampMs to it.text },
        )
    }

    @Test fun `trims trailing whitespace but preserves internal spacing`() {
        val body = "[00:10.00]  hello  world   "
        val lines = LrcParser.parse(body)
        assertEquals("  hello  world", lines[0].text)
    }
}
