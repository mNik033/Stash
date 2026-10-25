package com.stash.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedDownloadsTest {

    private fun approve(n: Int, from: ApprovedDownloads = ApprovedDownloads()) =
        (1..n).fold(from) { d, _ -> d.started() }

    @Test fun `approving starts a run that counts every approval`() {
        val d = approve(12)

        assertTrue(d.active)
        assertEquals(12, d.total)
        assertEquals("Downloading 12 approved songs", approvedDownloadsTitle(d))
        assertEquals("0 of 12 done", approvedDownloadsDetail(d))
        assertEquals(0f, d.fraction)
    }

    @Test fun `progress counts finished songs, downloaded or not`() {
        val d = approve(4).finished(downloaded = true).finished(downloaded = false)

        assertEquals(4, d.total)
        assertEquals(0.5f, d.fraction)
        assertEquals("2 of 4 done · 1 didn't download", approvedDownloadsDetail(d))
        assertNull(approvedDownloadsFinishedMessage(d))
    }

    @Test fun `a run ends when the last song finishes`() {
        val d = approve(2).finished(downloaded = true).finished(downloaded = true)

        assertFalse(d.active)
        assertNull(approvedDownloadsTitle(d))
        assertEquals("Downloaded 2 approved songs.", approvedDownloadsFinishedMessage(d))
    }

    @Test fun `a run where nothing downloaded has no success message`() {
        val d = approve(1).finished(downloaded = false)

        assertNull(approvedDownloadsFinishedMessage(d))
    }

    @Test fun `approving after a finished run starts a new count`() {
        val finished = approve(3).finished(true).finished(true).finished(false)

        val next = finished.started()

        assertEquals(ApprovedDownloads(waiting = 1), next)
        assertEquals("Downloading 1 approved song", approvedDownloadsTitle(next))
    }

    @Test fun `approving more mid-run joins the same run`() {
        val d = approve(2).finished(true).started()

        assertEquals(3, d.total)
        assertEquals("1 of 3 done", approvedDownloadsDetail(d))
    }

    @Test fun `a stray finish never goes negative`() {
        assertEquals(0, ApprovedDownloads().finished(true).waiting)
    }
}
