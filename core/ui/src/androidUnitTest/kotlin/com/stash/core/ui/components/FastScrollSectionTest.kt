package com.stash.core.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The fast-scroll bubble's letter. Keys are the names the Library sorts by (case-insensitively,
 * with nothing folded or stripped), so the letter must follow that string exactly, not a
 * prettier reading of it.
 */
class FastScrollSectionTest {

    @Test
    fun `a latin key shows its first letter in upper case`() {
        assertThat(fastScrollSectionLetter("abba")).isEqualTo("A")
        assertThat(fastScrollSectionLetter("zz top")).isEqualTo("Z")
    }

    @Test
    fun `either case of a letter shares its section, as the case-insensitive sort shares its place`() {
        assertThat(fastScrollSectionLetter("ABBA")).isEqualTo("A")
        assertThat(fastScrollSectionLetter("aphex Twin")).isEqualTo("A")
        assertThat(fastScrollSectionLetter("Édith Piaf")).isEqualTo(fastScrollSectionLetter("édith piaf"))
    }

    @Test
    fun `section letters run in the order the sort puts the names in`() {
        val names = listOf("zz top", "ABBA", "Édith Piaf", "beck", "1999", "Björk", "Air")
        val sorted = names.sortedWith(String.CASE_INSENSITIVE_ORDER)
        // Each section appears in one run, in order: the bubble never jumps back mid-list.
        val letters = sorted.map(::fastScrollSectionLetter)
        assertThat(letters).containsExactly("#", "A", "A", "B", "B", "Z", "É").inOrder()
    }

    @Test
    fun `nothing is stripped because the sort strips nothing`() {
        // "The Beatles" sorts under T in the Library, so the bubble says T too.
        assertThat(fastScrollSectionLetter("the beatles")).isEqualTo("T")
        assertThat(fastScrollSectionLetter("a tribe called quest")).isEqualTo("A")
    }

    @Test
    fun `accents are kept because the sort keeps them`() {
        // "édith piaf" sorts after every z-word: the bubble must not claim it's an E.
        assertThat(fastScrollSectionLetter("édith piaf")).isEqualTo("É")
        assertThat(fastScrollSectionLetter("über")).isEqualTo("Ü")
    }

    @Test
    fun `a decomposed accent shows its base letter, as it sorts with the base letter`() {
        // "e" + U+0301 COMBINING ACUTE ACCENT: the key starts with a plain "e".
        assertThat(fastScrollSectionLetter("édith")).isEqualTo("E")
    }

    @Test
    fun `other scripts show their own letter`() {
        assertThat(fastScrollSectionLetter("ωmega")).isEqualTo("Ω")
        assertThat(fastScrollSectionLetter("кино")).isEqualTo("К")
        assertThat(fastScrollSectionLetter("日本")).isEqualTo("日")
    }

    @Test
    fun `a letter outside the basic plane stays whole`() {
        // U+1D400 MATHEMATICAL BOLD CAPITAL A: a letter written as a surrogate pair.
        val boldA = String(Character.toChars(0x1D400))
        assertThat(fastScrollSectionLetter("${boldA}rt")).isEqualTo(boldA)
    }

    @Test
    fun `digits and symbols share the hash section`() {
        assertThat(fastScrollSectionLetter("1999")).isEqualTo("#")
        assertThat(fastScrollSectionLetter("(what's the story)")).isEqualTo("#")
        assertThat(fastScrollSectionLetter("¿qué?")).isEqualTo("#")
        assertThat(fastScrollSectionLetter(" leading space")).isEqualTo("#")
        assertThat(fastScrollSectionLetter("🎵 notes")).isEqualTo("#")
    }

    @Test
    fun `an empty key is the hash section`() {
        assertThat(fastScrollSectionLetter("")).isEqualTo("#")
    }
}
