package com.stash.core.ui.components

/**
 * The letter the fast-scroll bubble shows for an item while the listener drags the scrollbar
 * cap down an A–Z list ([VerticalScrollbar]'s `sectionLabel`).
 *
 * Pass the exact string the list is ordered by (the Library sorts its A–Z lists by title or
 * name with `String.CASE_INSENSITIVE_ORDER`): the bubble then can't disagree with the order the
 * rows are actually in. That comparator matches characters by their upper case, which is what
 * this shows, and it folds or strips nothing, so neither does this: "The Beatles" files under T,
 * and "Édith Piaf" sorts after Z, so its bubble says "É" rather than claiming an E that isn't
 * there. If the sort ever learns to fold accents or skip a leading "The", change this with it.
 *
 * - A key starting with a letter (any script) shows that letter in upper case: "abba" → "A",
 *   "édith" → "É", "ωmega" → "Ω", "日本" → "日".
 * - Anything else (digits, punctuation, spaces, emoji, an empty key) shows "#", the usual
 *   catch-all for the rows that sort around the letters.
 *
 * Works on the first code point, so a letter outside the Basic Multilingual Plane stays whole.
 * Pure and allocation-light: it runs once per frame while the cap is dragged.
 */
fun fastScrollSectionLetter(sortKey: String): String {
    if (sortKey.isEmpty()) return SECTION_OTHER
    val first = sortKey.codePointAt(0)
    if (!Character.isLetter(first)) return SECTION_OTHER
    return String(Character.toChars(Character.toUpperCase(first)))
}

/** The bubble for rows that don't start with a letter. */
private const val SECTION_OTHER = "#"
