package com.stash.core.data.weblink.merge

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.stash.core.data.weblink.handoff.WireSong
import com.stash.core.model.weblink.Hlc
import org.junit.Test

/**
 * Two devices editing one mirrored playlist from the same version, both pushing before either reads the other (audit A5). Each
 * device runs sync-v1's playlist run in miniature: pull the log in order and merge each remote `pl` op, then push its version
 * when it differs from the base, and the base moves when the batch lands. Both log orders; the corrected rule on both sides
 * (what the web must adopt), the corrected phone with today's web, and today's rule on both (which diverges for good).
 */
class PlaylistConvergenceTest {
    private class Entry(val device: String, val parent: String?, val v: PlVersion<WireSong>)

    private fun s(t: String) = WireSong(t, "Artist")

    private fun hashOf(v: PlVersion<WireSong>) = SyncHashes.playlistHash(v.name, v.items, v.follow)

    /** One device. [corrected]: it passes `baseHash` and the kept [past] bases to [PlaylistMerge.merge]. */
    private class Device(val id: String, val phone: Boolean, val corrected: Boolean, start: PlVersion<WireSong>, startHash: String) {
        var local: PlVersion<WireSong> = start
        var base: PlVersion<WireSong> = start
        var baseHash: String = startHash
        val past = ArrayList<Pair<String, PlVersion<WireSong>>>()
        var seen = 1
        var last: Hlc? = start.at

        fun moveBase(v: PlVersion<WireSong>, h: String) {
            if (h != baseHash) {
                past.removeAll { it.first == baseHash }
                past += baseHash to base
                while (past.size > 4) past.removeAt(0)
            }
            base = v
            baseHash = h
        }
    }

    private fun Device.edit(wall: Long, vararg titles: String) {
        last = Hlc.tick(last, wall, id)
        local = local.copy(items = titles.map(::s), at = last!!)
    }

    private fun Device.pull(log: List<Entry>, wall: Long) {
        while (seen < log.size) {
            val e = log[seen++]
            last = Hlc.recv(last, e.v.at, wall, id)
            if (e.device == id) continue
            val ancestor = if (corrected && e.parent != null && e.parent != baseHash) past.lastOrNull { it.first == e.parent }?.second else null
            val res = PlaylistMerge.merge(
                phone = phone, base = base, local = local, localHash = hashOf(local), remote = e.v, parent = e.parent,
                fromPhone = !phone, baseHash = if (corrected) baseHash else null, ancestor = ancestor,
            )
            if (res.action != PlaylistMerge.Action.KEEP) local = res.local!!
            moveBase(res.base!!, hashOf(res.base!!))
        }
    }

    private fun Device.push(log: MutableList<Entry>, wall: Long): Boolean {
        if (PlaylistMerge.sameVersion(local, base)) return false
        last = Hlc.tick(last, wall, id)
        val v = local.copy(at = last!!)
        log += Entry(id, baseHash, v)
        local = v
        moveBase(v, hashOf(v))
        return true
    }

    private class Outcome(val phone: List<String>, val web: List<String>, val view: List<String>)

    /** V0 = 1 2 3 4 on both; the phone removes 2 and adds P at the end, the web adds W after 1; both push, then both keep running. */
    private fun run(phoneCorrected: Boolean, webCorrected: Boolean, phoneFirst: Boolean): Outcome {
        val v0 = PlVersion("Night drive", listOf(s("1"), s("2"), s("3"), s("4")), Hlc(1_000, 0, "d_phone000"))
        val log = mutableListOf(Entry("d_phone000", null, v0))
        val phone = Device("d_phone000", phone = true, corrected = phoneCorrected, v0, hashOf(v0))
        val web = Device("d_web00000", phone = false, corrected = webCorrected, v0, hashOf(v0))
        var wall = 2_000L
        phone.edit(wall++, "1", "3", "4", "P")
        web.edit(wall++, "1", "W", "2", "3", "4")
        // Both pushed before either read the other's: the server takes appends without checking the writer's head.
        if (phoneFirst) {
            phone.push(log, wall++)
            web.push(log, wall++)
        } else {
            web.push(log, wall++)
            phone.push(log, wall++)
        }
        repeat(6) {
            for (d in listOf(phone, web)) {
                d.pull(log, wall++)
                d.push(log, wall++)
            }
        }
        val quiet = log.size
        phone.pull(log, wall++)
        web.pull(log, wall++)
        assertThat(phone.push(log, wall++) || web.push(log, wall++)).isFalse()
        assertThat(log.size).isEqualTo(quiet)
        return Outcome(phone.local.items!!.map { it.title }, web.local.items!!.map { it.title }, log.last().v.items!!.map { it.title })
    }

    @Test fun `corrected on both sides - both edits survive on both devices and in the view, whichever push landed first`() {
        for (phoneFirst in listOf(true, false)) {
            val o = run(phoneCorrected = true, webCorrected = true, phoneFirst = phoneFirst)
            assertWithMessage("phone first: $phoneFirst").that(o.phone).containsExactly("1", "W", "3", "4", "P").inOrder()
            assertWithMessage("phone first: $phoneFirst").that(o.web).isEqualTo(o.phone)
            assertWithMessage("phone first: $phoneFirst").that(o.view).isEqualTo(o.phone)
        }
    }

    @Test fun `the corrected phone alone brings today's web to the merge too`() {
        for (phoneFirst in listOf(true, false)) {
            val o = run(phoneCorrected = true, webCorrected = false, phoneFirst = phoneFirst)
            assertWithMessage("phone first: $phoneFirst").that(o.phone).containsExactly("1", "W", "3", "4", "P").inOrder()
            assertWithMessage("phone first: $phoneFirst").that(o.web).isEqualTo(o.phone)
            assertWithMessage("phone first: $phoneFirst").that(o.view).isEqualTo(o.phone)
        }
    }

    @Test fun `today's rule on both sides swaps the two edits for good (the bug)`() {
        for (phoneFirst in listOf(true, false)) {
            val o = run(phoneCorrected = false, webCorrected = false, phoneFirst = phoneFirst)
            assertWithMessage("phone first: $phoneFirst").that(o.phone).isNotEqualTo(o.web)
        }
    }

    @Test fun `with no common version kept, an unchanged playlist keeps the newer edit, never an older concurrent one`() {
        val mine = PlVersion("Mix", listOf(s("1"), s("P")), Hlc(5_000, 0, "d_phone000"))
        val older = PlVersion("Mix", listOf(s("1"), s("W")), Hlc(4_000, 0, "d_web00000"))
        val newer = older.copy(at = Hlc(6_000, 0, "d_web00000"))
        fun merge(r: PlVersion<WireSong>) = PlaylistMerge.merge(
            phone = true, base = mine, local = mine, localHash = hashOf(mine), remote = r, parent = "h_unknownParent00000000",
            fromPhone = false, baseHash = hashOf(mine), ancestor = null,
        )
        val kept = merge(older)
        assertThat(kept.action).isEqualTo(PlaylistMerge.Action.KEEP)
        assertThat(kept.local).isEqualTo(mine)
        assertThat(kept.base).isEqualTo(older) // the base moves to the remote, so the next push sends this version back over it
        assertThat(merge(newer).action).isEqualTo(PlaylistMerge.Action.TAKE)
    }
}
