package com.stash.core.data.weblink.mirror

import com.google.common.truth.Truth.assertThat
import com.stash.core.model.weblink.Hlc
import org.junit.Test

/**
 * Snapshots keep every tombstone, however old (the 90-day prune is gone): a device whose pull stalled for months must not
 * bring back an unliked song or a deleted playlist by out-waiting the tombstone. Snapshots written before, with old
 * tombstones already pruned, still read.
 */
class MirrorViewSnapshotTest {

    private val day = 86_400_000L
    private val now = 1_000L * day

    /** A view holding a year-old unlike, a year-old deleted playlist, and a live like and playlist. */
    private fun viewWithOldTombstones(): SpaceView {
        val old = Hlc(now - 365 * day, 0, "d_web")
        val fresh = Hlc(now - day, 0, "d_web")
        return SpaceView(
            likes = listOf(
                ViewLike(song("Unliked long ago"), on = false, at = old, seq = 3),
                ViewLike(song("Still liked"), on = true, at = fresh, seq = 4),
            ),
            playlists = mapOf(
                "gone" to ViewPl(name = "Deleted long ago", items = null, at = old, hash = "h0", seq = 5),
                "live" to ViewPl(name = "Road trip", items = listOf(song("A")), at = fresh, hash = "h1", seq = 6),
            ),
        )
    }

    @Test fun `a snapshot keeps an unlike and a deleted playlist however old they are`() {
        val st = MirrorView.stateOf(viewWithOldTombstones(), uptoSeq = 6)

        assertThat(st.likes.map { it.s.title to it.on })
            .containsExactly("Unliked long ago" to false, "Still liked" to true)
        assertThat(st.playlists.map { it.id to (it.items == null) })
            .containsExactly("gone" to true, "live" to false)
    }

    @Test fun `a stalled device's older like loses to the kept tombstone after a snapshot`() {
        // Written as a snapshot, read back (what every device does after compaction)...
        val view = MirrorView.fromState(MirrorView.stateOf(viewWithOldTombstones(), uptoSeq = 6))
        // ...then a device that stalled for months finally sends its like from before the unlike.
        val stale = MirrorOp.Like(song("Unliked long ago"), true, Hlc(now - 400 * day, 0, "d_stalled"))

        val after = MirrorView.applyBatch(view, "d_stalled", seq = 7, ops = listOf(stale), fromPhone = false)

        assertThat(after.likes.single { it.s.title == "Unliked long ago" }.on).isFalse()
    }

    @Test fun `a snapshot written with old tombstones already pruned still reads`() {
        // What a phone (or the web) wrote before this change: the year-old tombstones simply aren't there.
        val pruned = MirrorState(
            uptoSeq = 6,
            likes = listOf(MirrorState.StateLike(song("Still liked"), true, Hlc(now - day, 0, "d_web"))),
            playlists = listOf(
                MirrorState.StatePl("live", "Road trip", listOf(song("A")), follow = null, ro = false, at = Hlc(now - day, 0, "d_web"), hash = "h1"),
            ),
        )

        val view = MirrorView.fromState(pruned)

        assertThat(view.likes.map { it.s.title }).containsExactly("Still liked")
        assertThat(view.playlists.keys).containsExactly("live")
        // And it writes back unchanged: nothing is invented for what was pruned.
        assertThat(MirrorView.stateOf(view, uptoSeq = 6)).isEqualTo(pruned)
    }
}
