package com.stash.core.data.sync.workers

import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import com.stash.core.model.SyncMode
import com.stash.data.spotify.model.SpotifyImage
import com.stash.data.spotify.model.SpotifyOwner
import com.stash.data.spotify.model.SpotifyPlaylistItem
import com.stash.data.spotify.model.SpotifyTracksRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The skip-unchanged-playlists decision ([SpotifyMirrorStamp], [stampToSkipOn],
 * [revisionToRecord]) as pure logic. A wrong "skip" leaves a playlist silently
 * stale while an unneeded "read" costs one fetch, so every case that is not an
 * exact, fresh match must come out as a read.
 */
class SpotifyMirrorStampTest {

    private val now = 1_800_000_000_000L
    private val revision = "AAAAAMv6SY4RNBP4lRibleC3UcWweCUH"
    private val digest = "9f2c1d0b7a4e6f8812ab34cd56ef7890"
    private val appVersion = 150L

    private fun stamp(
        revision: String = this.revision,
        syncMode: SyncMode = SyncMode.REFRESH,
        recordedAtMs: Long = now - 60_000,
        digest: String = this.digest,
        appVersion: Long = this.appVersion,
    ) = SpotifyMirrorStamp(revision, syncMode, recordedAtMs, digest, appVersion).encode()

    private fun skipOn(
        stored: String?,
        listRevision: String? = revision,
        currentDigest: String? = digest,
        syncMode: SyncMode? = SyncMode.REFRESH,
        nowMs: Long = now,
        appVersion: Long = this.appVersion,
    ) = stampToSkipOn(listRevision, stored, currentDigest, syncMode, nowMs, appVersion)

    // ── Encoding ─────────────────────────────────────────────────────────

    @Test fun `a stamp reads back exactly as written`() {
        val written = SpotifyMirrorStamp(revision, SyncMode.ACCUMULATE, 1234L, digest, 150L)

        assertEquals(written, SpotifyMirrorStamp.decode(written.encode()))
    }

    @Test fun `anything that isn't a whole current-version stamp reads as no stamp`() {
        val good = stamp()
        listOf(
            null,
            "",
            revision, // a bare revision, as a fetched snapshot carries it
            good.replaceFirst("v2|", "v1|"),
            good.replace("|REFRESH|", "|SOMETIMES|"),
            good.replace("|${now - 60_000}|", "|yesterday|"),
            "$good|extra",
            good.substringBeforeLast('|') + "|",
        ).forEach { assertNull("'$it' must not decode", SpotifyMirrorStamp.decode(it)) }
    }

    @Test fun `a revision carrying the separator is never used`() {
        assertTrue(SpotifyMirrorStamp.isUsableRevision(revision))
        listOf(null, "", "  ", "AAAA|BBBB").forEach {
            assertEquals("'$it'", false, SpotifyMirrorStamp.isUsableRevision(it))
        }
    }

    // ── stampToSkipOn: only an exact, fresh match skips ──────────────────

    @Test fun `an exact fresh match skips, on the stored stamp itself`() {
        val stored = stamp()

        assertEquals(stored, skipOn(stored))
    }

    @Test fun `a new revision on Spotify reads the playlist`() {
        assertNull(skipOn(stamp(), listRevision = "AAAAANewRevisionAfterAnEditXXXXX"))
    }

    @Test fun `a listing with no revision reads the playlist`() {
        assertNull(skipOn(stamp(), listRevision = null))
        assertNull(skipOn(stamp(), listRevision = ""))
    }

    @Test fun `no stamp, a forgotten one or garbage reads the playlist`() {
        assertNull(skipOn(null))
        assertNull(skipOn("not a stamp"))
    }

    @Test fun `a change to the playlist's rows here reads the playlist`() {
        // A song removed, deleted, blocked or added since the mirror moves the digest.
        assertNull(skipOn(stamp(), currentDigest = "0000000000000000aaaaaaaaaaaaaaaa"))
        assertNull(skipOn(stamp(), currentDigest = null))
    }

    @Test fun `a switch between Refresh and Accumulate reads the playlist`() {
        assertNull(skipOn(stamp(syncMode = SyncMode.ACCUMULATE), syncMode = SyncMode.REFRESH))
        assertNull(skipOn(stamp(syncMode = SyncMode.REFRESH), syncMode = SyncMode.ACCUMULATE))
        // An unreadable mode setting skips nothing.
        assertNull(skipOn(stamp(), syncMode = null))
    }

    @Test fun `a mirror older than a week is read again however unchanged`() {
        val justInside = stamp(recordedAtMs = now - SpotifyMirrorStamp.MAX_AGE_MS + 1)
        val atTheLimit = stamp(recordedAtMs = now - SpotifyMirrorStamp.MAX_AGE_MS)

        assertEquals(justInside, skipOn(justInside))
        assertNull(skipOn(atTheLimit))
    }

    @Test fun `an app update reads every playlist once`() {
        assertNull(skipOn(stamp(appVersion = 149L), appVersion = 150L))
    }

    @Test fun `a stamp from the future is not trusted`() {
        // The clock went back since it was written: its age can't be told.
        assertNull(skipOn(stamp(recordedAtMs = now + 60_000)))
    }

    @Test fun `a candidate is judged without the fingerprint, which is computed only for it`() {
        val stored = stamp()
        assertEquals(SpotifyMirrorStamp.decode(stored), skipCandidate(revision, stored, SyncMode.REFRESH, now, appVersion))
        assertNull(skipCandidate("AAAAAOther", stored, SyncMode.REFRESH, now, appVersion))
        assertNull(skipCandidate(revision, stored, SyncMode.ACCUMULATE, now, appVersion))
        assertNull(skipCandidate(revision, stamp(recordedAtMs = now - SpotifyMirrorStamp.MAX_AGE_MS), SyncMode.REFRESH, now, appVersion))
    }

    // ── revisionToRecord: only a verified fetch of the listed revision is stamped ──

    @Test fun `a verified fetch of the listed revision carries it`() {
        assertEquals(revision, revisionToRecord(revision, countVerified = true, fetchedRevision = revision))
    }

    @Test fun `an unverified fetch carries none, so it is read again next time`() {
        assertNull(revisionToRecord(revision, countVerified = false, fetchedRevision = revision))
        assertNull(revisionToRecord(null, countVerified = true, fetchedRevision = null))
        assertNull(revisionToRecord("AAAA|BBBB", countVerified = true, fetchedRevision = "AAAA|BBBB"))
    }

    @Test fun `a fetch that read another revision than the listing carries none`() {
        // The playlist changed between the two reads, or the walk reported no revision.
        assertNull(revisionToRecord(revision, countVerified = true, fetchedRevision = "AAAAANewerRevisionMidSyncXXXXXXX"))
        assertNull(revisionToRecord(revision, countVerified = true, fetchedRevision = null))
    }

    // ── The skipped playlist's snapshot row ──────────────────────────────

    private val playlist = SpotifyPlaylistItem(
        id = "6wyGbrLCQHiRK1pESUTo8Z",
        name = "Road Trip",
        owner = SpotifyOwner(id = "testuser"),
        images = listOf(SpotifyImage(url = "https://i.scdn.co/image/road")),
        tracks = SpotifyTracksRef(total = 0),
        revisionId = revision,
    )

    @Test fun `a skipped playlist's row carries the trusted stamp and admits it holds no tracks`() {
        val stored = stamp()

        val row = unchangedSpotifyPlaylistSnapshot(playlist, stored, syncId = 7L)

        assertEquals("the diff matches this against the playlist's own", stored, row.snapshotId)
        assertTrue("never a mirror source: the diff must not clear against it", row.partial)
        assertEquals(7L, row.syncId)
    }

    @Test fun `a skipped playlist reads to the diff like a fetched one, apart from its tracks`() {
        val skipped = unchangedSpotifyPlaylistSnapshot(playlist, stamp(), syncId = 7L)
        val fetched = spotifyLibraryPlaylistSnapshot(playlist, syncId = 7L, partial = false, snapshotId = revision)

        // Name, type and cover are what the diff renames, re-files and shows by.
        assertEquals(fetched.copy(partial = true, snapshotId = skipped.snapshotId, fetchedAt = skipped.fetchedAt), skipped)
        assertEquals(MusicSource.SPOTIFY, skipped.source)
        assertEquals(PlaylistType.CUSTOM, skipped.playlistType)
        assertEquals("Road Trip", skipped.playlistName)
    }
}
