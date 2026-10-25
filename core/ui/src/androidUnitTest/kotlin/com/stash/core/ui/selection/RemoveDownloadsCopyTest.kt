package com.stash.core.ui.selection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A batch "Remove download" (up to a whole Select all) deleted every file with no confirmation,
 * one mis-tap from the confirmed Delete. These pin when the shared dialog appears and what it says.
 */
class RemoveDownloadsCopyTest {

    @Test
    fun `more than one song needs confirming, one does not`() {
        assertThat(removeDownloadsNeedsConfirm(1)).isFalse()
        assertThat(removeDownloadsNeedsConfirm(2)).isTrue()
        assertThat(removeDownloadsNeedsConfirm(10_000)).isTrue()
    }

    @Test
    fun `title names the count`() {
        assertThat(removeDownloadsTitle(12)).isEqualTo("Remove 12 downloads?")
        assertThat(removeDownloadsTitle(1)).isEqualTo("Remove 1 download?")
    }

    @Test
    fun `body names the space freed only when sizes are known`() {
        assertThat(removeDownloadsBody(1_288_490_189L)).contains("(1.2 GB)")
        assertThat(removeDownloadsBody(0L)).doesNotContain("(")
        assertThat(removeDownloadsBody(0L)).contains("can still stream")
    }

    @Test
    fun `bytes format over powers of 1024`() {
        assertThat(formatDownloadBytes(512L)).isEqualTo("512.0 B")
        assertThat(formatDownloadBytes(5L * 1024 * 1024)).isEqualTo("5.0 MB")
        assertThat(formatDownloadBytes(0L)).isEqualTo("0 B")
    }
}
