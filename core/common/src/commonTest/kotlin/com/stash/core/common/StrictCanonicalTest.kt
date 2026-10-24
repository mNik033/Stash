package com.stash.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [strictCanonical], the strict word form that songs saved outside the sync were stored in
 * until schema 55. Rows already in users' libraries were written by it (and by the five copies it
 * replaced), and they are recognised for re-keying by exactly this output, so it must never move.
 */
class StrictCanonicalTest {

    /** The five private copies' body, verbatim: the shared function must agree with it everywhere. */
    private fun formerCopy(s: String): String =
        s.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val pinned = linkedMapOf(
        // Brackets and version tags stay as words: strict, not loose.
        "Song (Remastered 2011)" to "song remastered 2011",
        "Song [Live at Wembley]" to "song live at wembley",
        "Song - Radio Edit" to "song radio edit",
        // "feat." credits are kept, dot and all turned to a space.
        "Song (feat. Artist B)" to "song feat artist b",
        "Song ft. Artist B" to "song ft artist b",
        // Punctuation becomes a space, runs collapse, ends trim.
        "  Don't  Stop   Me Now!!  " to "don t stop me now",
        "AC/DC" to "ac dc",
        "Track_Name-v2.0" to "track name v2 0",
        "Hello?! ... World" to "hello world",
        // Two artists, however they are joined.
        "Metro Boomin, Travis Scott" to "metro boomin travis scott",
        "Simon & Garfunkel" to "simon garfunkel",
        "Artist A x Artist B" to "artist a x artist b",
        "Dan + Shay" to "dan shay",
        // Unicode letters and digits of every script are kept and lowercased.
        "Beyoncé" to "beyoncé",
        "Sigur Rós" to "sigur rós",
        "Мумий Тролль" to "мумий тролль",
        "坂本龍一" to "坂本龍一",
        "Ｓｏｎｇ２" to "ｓｏｎｇ２",
        // A decomposed accent (combining mark) is not a letter: it becomes a space, so NFD and
        // NFC spellings of the same name are two identities. Pinned, not endorsed.
        "Beyoncé" to "beyonce",
        // Locale-free lowercasing: dotted capital I lowers to i + a combining dot, which then
        // splits the word. Also pinned as-is.
        "İstanbul" to "i stanbul",
        // Whitespace of every kind folds to one space; a no-break space is punctuation here.
        "a\tb\nc" to "a b c",
        "a b" to "a b",
        // Symbols and emoji are not letters.
        "Song 🔥" to "song",
        "\$uicideboy\$" to "uicideboy",
        "" to "",
        "!!!" to "",
    )

    @Test fun `pinned outputs on tricky inputs`() {
        for ((input, expected) in pinned) {
            assertEquals("strictCanonical(\"$input\")", expected, strictCanonical(input))
        }
    }

    @Test fun `agrees with the copies it replaced`() {
        for (input in pinned.keys) {
            assertEquals("input \"$input\"", formerCopy(input), strictCanonical(input))
        }
    }

    @Test fun `is idempotent`() {
        for (input in pinned.keys) {
            val once = strictCanonical(input)
            assertEquals(once, strictCanonical(once))
        }
    }
}
