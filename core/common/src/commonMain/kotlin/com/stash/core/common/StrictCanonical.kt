package com.stash.core.common

/**
 * The strict word form of a title or artist. Lowercased; every character that isn't a letter, a
 * digit or whitespace becomes a space; runs of whitespace collapse to one space; the ends are
 * trimmed. Letters and digits of every script count (`\p{L}`, `\p{N}`), so "Beyoncé" and "坂本龍一"
 * keep their characters. Nothing is dropped as noise: "(feat. X)", "[Live]" or "- Remastered" stay
 * as words.
 *
 * NOT what `tracks.canonical_title` / `canonical_artist` hold: every writer stores
 * [TrackCanonical]'s loose rule. Until schema 55, songs saved from search, a stream, a hand-off or
 * a tap were stored in this strict form instead, so a song synced from Spotify and the same song
 * saved from search were filed as two rows. It stays, unchanged, for the jobs that need strict
 * words: rows written by it are still in users' libraries and are recognised by exactly this
 * output to be re-keyed, [TrackCanonical.version] compares the base of two titles in it, and a song
 * the user saves by hand while its loose key is blocked is still stored in it, as before schema 55
 * (`canonicalWordsForSave` in core:data), so the block doesn't hide and sweep the new row.
 */
fun strictCanonical(s: String): String =
    s.lowercase()
        .replace(NOT_LETTER_DIGIT_OR_SPACE, " ")
        .replace(WHITESPACE_RUN, " ")
        .trim()

private val NOT_LETTER_DIGIT_OR_SPACE = Regex("[^\\p{L}\\p{N}\\s]")
private val WHITESPACE_RUN = Regex("\\s+")
