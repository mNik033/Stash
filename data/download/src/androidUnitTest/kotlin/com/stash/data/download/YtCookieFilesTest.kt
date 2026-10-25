package com.stash.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A yt-dlp cookie file holds the whole YouTube session and was deleted only in the
 * `finally` of its run, so one left by process death stayed in noBackupFilesDir for
 * good, past "Disconnect YouTube".
 */
class YtCookieFilesTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `sweep deletes leftover download and preview cookie files and nothing else`() {
        val download = YtCookieFiles.newFile(tmp.root, YtCookieFiles.DOWNLOAD_PREFIX).apply { writeText("SAPISID") }
        val preview = YtCookieFiles.newFile(tmp.root, YtCookieFiles.PREVIEW_PREFIX).apply { writeText("SAPISID") }
        val other = tmp.newFile("yt_cookies_notes.json")
        val unrelated = tmp.newFile("settings.txt")
        val folder = tmp.newFolder("youtubedl-android")

        assertEquals(2, YtCookieFiles.sweep(tmp.root))

        assertFalse(download.exists())
        assertFalse(preview.exists())
        assertTrue(other.exists())
        assertTrue(unrelated.exists())
        assertTrue(folder.exists())
    }

    @Test
    fun `the names the runs write are the names the sweep removes`() {
        assertTrue(YtCookieFiles.isCookieFileName(YtCookieFiles.newFile(tmp.root, YtCookieFiles.DOWNLOAD_PREFIX).name))
        assertTrue(YtCookieFiles.isCookieFileName(YtCookieFiles.newFile(tmp.root, YtCookieFiles.PREVIEW_PREFIX).name))
    }

    @Test
    fun `sweeping a missing folder deletes nothing`() {
        assertEquals(0, YtCookieFiles.sweep(tmp.root.resolve("absent")))
    }
}
