package com.stash.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [TrackCanonical], the one rule behind `tracks.canonical_title` / `canonical_artist`. The
 * sync wrote it for years and the blocklist is keyed by it, so its output on these inputs must
 * never move: a change re-keys every stored row and every block.
 */
class TrackCanonicalTest {

    /** TrackMatcher's body before the rule moved here, verbatim: the move must not change a single key. */
    private val parenthetical = Regex("""\s*[\(\[][^)\]]*[\)\]]""")
    private val feat = Regex("""\s*(feat\.?|ft\.?|featuring)\s+.*""", RegexOption.IGNORE_CASE)
    private val separator = Regex("""\s*[,;&/]\s*|\s+(?:and|&)\s+""", RegexOption.IGNORE_CASE)
    private fun formerTitle(t: String) = t.replace(parenthetical, "").replace(feat, "").lowercase().trim()
    private fun formerArtist(a: String) =
        a.split(separator).map { it.lowercase().trim() }.filter { it.isNotEmpty() }.sorted().joinToString(", ")

    private val titles = linkedMapOf(
        // The owner's example: brackets and credits go, punctuation stays.
        "Don't Stop (feat. X)" to "don't stop",
        // Every bracketed group goes, version tags included: two versions share a key.
        "Song (Remastered 2011)" to "song",
        "Song [Live at Wembley]" to "song",
        "Song (Live) [2011 Remaster]" to "song",
        // A " - " suffix is not a bracket, so it stays.
        "Song - Radio Edit" to "song - radio edit",
        // A credit outside brackets goes, with everything after it.
        "Song ft. Artist B" to "song",
        "Song feat. Artist B" to "song",
        "Song Featuring B" to "song",
        // No word boundary before "ft": a mid-word "ft " cuts the title. Pinned, not endorsed.
        "Left Hand Free" to "le",
        // Only lowercased and trimmed: inner whitespace and punctuation are kept.
        "  Don't  Stop   Me Now!!  " to "don't  stop   me now!!",
        "Track_Name-v2.0" to "track_name-v2.0",
        // An unclosed bracket is not a group.
        "Song (unclosed" to "song (unclosed",
        // A title that is only a bracket is blank: lookups skip blank keys.
        "(Intro)" to "",
        // Unicode letters lowercase locale-free.
        "Beyoncé" to "beyoncé",
        "坂本龍一" to "坂本龍一",
        "İstanbul" to "i̇stanbul",
        "" to "",
    )

    private val artists = linkedMapOf(
        // The owner's example: a two-artist credit, however it's joined, is one sorted list.
        "A & B" to "a, b",
        "B & A" to "a, b",
        "B, A" to "a, b",
        "A;B" to "a, b",
        "AC/DC" to "ac, dc",
        "Simon & Garfunkel" to "garfunkel, simon",
        "Earth, Wind & Fire" to "earth, fire, wind",
        "Florence and the Machine" to "florence, the machine",
        // Not separators.
        "Artist A x Artist B" to "artist a x artist b",
        "Dan + Shay" to "dan + shay",
        "Beyoncé feat. Jay-Z" to "beyoncé feat. jay-z",
        "  " to "",
        "" to "",
    )

    @Test fun `titles on tricky inputs`() {
        for ((input, expected) in titles) {
            assertEquals("title(\"$input\")", expected, TrackCanonical.title(input))
        }
    }

    @Test fun `artists on tricky inputs`() {
        for ((input, expected) in artists) {
            assertEquals("artist(\"$input\")", expected, TrackCanonical.artist(input))
        }
    }

    @Test fun `agrees with the TrackMatcher body it replaced`() {
        for (input in titles.keys + artists.keys) {
            assertEquals("title \"$input\"", formerTitle(input), TrackCanonical.title(input))
            assertEquals("artist \"$input\"", formerArtist(input), TrackCanonical.artist(input))
        }
    }

    /**
     * Pairs that share a canonical title and ARE the same version: a row may be reused. Everything
     * but a performance word is the same version, so these must keep meeting: for people who sync
     * both Spotify and YouTube, this match is the only link between the two services' rows.
     */
    private val sameVersions = listOf(
        // Real titles: Spotify's credit or YouTube's presentation tag on one side only.
        "STAY (with Justin Bieber)" to "STAY",
        "PRBLMS [Official Music Video]" to "PRBLMS",
        "When the Sun Hits (Audio)" to "When the Sun Hits",
        "Don't Stop (feat. X)" to "Don't Stop",
        "Song (ft. X & Y)" to "Song",
        "Song [Featuring X]" to "Song",
        "Song ft. X" to "Song",
        "Song (w/ X)" to "Song",
        "Song (prod. X)" to "Song",
        "Song (Produced by X)" to "Song",
        "Song (Official Video)" to "Song",
        "Song (Official Video HD)" to "Song",
        "Song [Official Music Video] (HD)" to "Song",
        "Song (Lyrics)" to "Song",
        "Song (Explicit)" to "Song",
        "Song (Clean)" to "Song",
        // A remaster, with or without a year, a reissue, a single or radio version, mono or stereo.
        "Song (Remastered 2011)" to "Song",
        "Song [2011 Remaster]" to "Song",
        "Song (2011)" to "Song",
        "Song (Deluxe)" to "Song",
        "Song (Mono)" to "Song (Stereo)",
        "Song (Single Version)" to "Song (Album Version)",
        "Song (Radio Edit)" to "Song",
        "Song (2009 Mix)" to "Song",
        // The same performance word on both sides.
        "Song (feat. X) [Live]" to "Song (Live)",
        "SONG (LIVE)" to "song [live]",
        "Song (Remix)" to "Song [Remix] (Official Audio)",
        // A performance word inside a credit is a name, not a version.
        "Song (feat. The Cover Girls)" to "Song",
    )

    /** Pairs that share a canonical title but are NOT the same version: each keeps its own row. */
    private val otherVersions = listOf(
        "Song (Live)" to "Song",
        "Song [Live at Wembley]" to "Song",
        "Song (Remix)" to "Song",
        "Song (Acoustic)" to "Song",
        "Song (Acoustic)" to "Song (Live)",
        "Song (Taylor's Version)" to "Song",
        "Song (Sped Up)" to "Song",
        "Song (Slowed + Reverb)" to "Song (Slowed)",
        "Song (Instrumental)" to "Song",
        "Song (MTV Unplugged)" to "Song",
        "Song (Official Live Video)" to "Song (Official Video)",
        "Song (with X - Live)" to "Song (with X)",
        // The loose rule drops everything after "feat." (so this is "song"); the version keeps "- Live".
        "Song feat. Jay-Z - Live" to "Song",
        // The loose rule cuts a mid-word "ft " ("le" for both); the base doesn't.
        "Left Hand Free" to "Left Outside Alone",
        "Lift Me Up" to "Lift Off",
    )

    @Test fun `credits, presentation tags, remasters and editions are the same version`() {
        for ((a, b) in sameVersions) {
            assertEquals("canonical title of \"$a\" / \"$b\"", TrackCanonical.title(a), TrackCanonical.title(b))
            assertTrue("\"$a\" and \"$b\" are one version", TrackCanonical.sameVersion(a, b))
            assertTrue("\"$b\" and \"$a\" are one version", TrackCanonical.sameVersion(b, a))
        }
    }

    @Test fun `only a performance word one side lacks makes another version`() {
        for ((a, b) in otherVersions) {
            assertEquals("canonical title of \"$a\" / \"$b\"", TrackCanonical.title(a), TrackCanonical.title(b))
            assertFalse("\"$a\" and \"$b\" are two versions", TrackCanonical.sameVersion(a, b))
            assertFalse("\"$b\" and \"$a\" are two versions", TrackCanonical.sameVersion(b, a))
        }
    }

    @Test fun `a dash suffix is version text too`() {
        // These pairs differ in their canonical title (a dash suffix stays in it), so they never meet
        // by words; the version rule still reads them as the owner does.
        assertTrue(TrackCanonical.sameVersion("Song - Remastered 2011", "Song"))
        assertTrue(TrackCanonical.sameVersion("Loser (Fcukers Remix - Official Audio)", "Loser - Fcukers Remix"))
        assertTrue(TrackCanonical.sameVersion("Song - Live", "Song (Live)"))
        assertFalse(TrackCanonical.sameVersion("Song - Live", "Song"))
    }

    @Test fun `a song whose name holds a performance word is not a version of it`() {
        assertTrue(TrackCanonical.sameVersion("Live Forever", "Live Forever (Remastered)"))
        assertTrue(TrackCanonical.sameVersion("Remix Culture", "Remix Culture [Official Video]"))
        assertFalse(TrackCanonical.sameVersion("Live Forever", "Live Forever (Live)"))
    }

    @Test fun `versions`() {
        assertEquals(TrackCanonical.Version("song", emptySet()), TrackCanonical.version("Song (feat. X) [Official Video]"))
        assertEquals(TrackCanonical.Version("song", setOf("live")), TrackCanonical.version("Song (Live) (feat. X)"))
        assertEquals(TrackCanonical.Version("song", setOf("mix")), TrackCanonical.version("Song feat. X - Y Remix"))
        assertEquals(TrackCanonical.Version("left hand free", emptySet()), TrackCanonical.version("Left Hand Free"))
        assertEquals(TrackCanonical.Version("ft lauderdale", emptySet()), TrackCanonical.version("Ft. Lauderdale"))
        assertEquals(TrackCanonical.Version("", emptySet()), TrackCanonical.version("(Intro)"))
        assertEquals(TrackCanonical.Version("", emptySet()), TrackCanonical.version(""))
    }

    @Test fun `a key blank on either side can't identify a song`() {
        assertTrue(TrackCanonical.isBlankKey(TrackCanonical.title("(Intro)"), TrackCanonical.artist("Artist")))
        assertTrue(TrackCanonical.isBlankKey(TrackCanonical.title("[Untitled]"), "artist"))
        assertTrue(TrackCanonical.isBlankKey(TrackCanonical.title("Ft. Lauderdale"), "artist"))
        assertTrue(TrackCanonical.isBlankKey("song", TrackCanonical.artist(" ")))
        assertTrue("a never-keyed local import", TrackCanonical.isBlankKey("", ""))
        assertFalse(TrackCanonical.isBlankKey("song", "artist"))
    }

    @Test fun `is idempotent on its own output`() {
        for (input in titles.keys) {
            val once = TrackCanonical.title(input)
            assertEquals(once, TrackCanonical.title(once))
        }
        for (input in artists.keys) {
            val once = TrackCanonical.artist(input)
            assertEquals(once, TrackCanonical.artist(once))
        }
    }
}
