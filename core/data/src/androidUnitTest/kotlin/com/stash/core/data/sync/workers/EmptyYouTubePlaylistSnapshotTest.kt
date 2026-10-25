package com.stash.core.data.sync.workers

import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import com.stash.data.ytmusic.model.YTMusicPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A YouTube library playlist whose track fetch came back Empty used to get no
 * snapshot at all, so REFRESH treated it as missing and hid it every sync. The
 * snapshot written instead must keep it in the inventory WITHOUT letting
 * DiffWorker mirror-clear real tracks: its REFRESH guard reads `partial`, and
 * `trackCount > 0` with zero track rows (DiffWorkerTest covers both guards).
 */
class EmptyYouTubePlaylistSnapshotTest {

    private fun playlist(trackCount: Int?) = YTMusicPlaylist(
        playlistId = "PLmine",
        title = "Mine",
        trackCount = trackCount,
    )

    @Test fun `listing that says empty is mirrored as genuinely empty`() {
        val snapshot = emptyYouTubePlaylistSnapshot(playlist(trackCount = 0), syncId = 7L)

        assertEquals(7L, snapshot.syncId)
        assertEquals(MusicSource.YOUTUBE, snapshot.source)
        assertEquals("PLmine", snapshot.sourcePlaylistId)
        assertEquals(PlaylistType.CUSTOM, snapshot.playlistType)
        assertEquals(0, snapshot.trackCount)
        assertFalse(snapshot.partial)
    }

    @Test fun `listing that claims tracks keeps the claim so the empty fetch looks suspicious`() {
        val snapshot = emptyYouTubePlaylistSnapshot(playlist(trackCount = 42), syncId = 7L)

        assertEquals(42, snapshot.trackCount)
        assertFalse(snapshot.partial)
    }

    @Test fun `listing without a count cannot vouch for empty so the snapshot is partial`() {
        val snapshot = emptyYouTubePlaylistSnapshot(playlist(trackCount = null), syncId = 7L)

        assertTrue(snapshot.partial)
    }
}
