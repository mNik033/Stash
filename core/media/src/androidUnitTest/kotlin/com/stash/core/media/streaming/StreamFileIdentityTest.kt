package com.stash.core.media.streaming

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [isSameFileAs] / [sameUrlFileParams] decide whether a re-open of a playing item may
 * continue at byte N on a re-resolved URL. A false "same" splices two files together
 * under one extractor, so every reported format field has to agree.
 */
class StreamFileIdentityTest {

    private fun yt(itag: Int, clen: Long, sig: String = "a") =
        "https://rr1.googlevideo.com/videoplayback?expire=1700000000&itag=$itag&clen=$clen&sig=$sig"

    @Test
    fun `googlevideo urls with the same itag and length are the same file whatever the signature`() {
        assertThat(sameUrlFileParams(yt(251, 9000, sig = "a"), yt(251, 9000, sig = "b"))).isTrue()
    }

    @Test
    fun `another itag or another length is another file`() {
        assertThat(sameUrlFileParams(yt(251, 9000), yt(249, 9000))).isFalse()
        assertThat(sameUrlFileParams(yt(251, 9000), yt(251, 8000))).isFalse()
    }

    @Test
    fun `a param only one url carries is not compared`() {
        assertThat(sameUrlFileParams("https://cdn/a.flac?etsp=1", yt(251, 9000))).isTrue()
        assertThat(sameUrlFileParams("https://cdn/a.flac", "https://cdn/b.flac#itag=1")).isTrue()
    }

    @Test
    fun `stream urls differ when source codec or format differ`() {
        val flac = StreamUrl(
            url = "https://cdn/a.flac?etsp=1",
            expiresAtMs = 1L,
            codec = "flac",
            bitsPerSample = 24,
            sampleRateHz = 96_000,
            origin = "qbdlx",
        )
        assertThat(flac.isSameFileAs(flac.copy(url = "https://cdn/a.flac?etsp=2", expiresAtMs = 2L))).isTrue()
        // The tier dropped from Hi-Res to CD between resolves.
        assertThat(flac.isSameFileAs(flac.copy(bitsPerSample = 16, sampleRateHz = 44_100))).isFalse()
        // A different rung served the re-resolve.
        assertThat(flac.isSameFileAs(flac.copy(origin = "youtube", codec = "opus"))).isFalse()
        // JioSaavn's 320 vs Save Data's 96 kbps: same codec, another file.
        val aac = StreamUrl(url = "https://aac/x.mp4", expiresAtMs = 1L, codec = "aac", bitrateKbps = 320, origin = "jiosaavn")
        assertThat(aac.isSameFileAs(aac.copy(bitrateKbps = 96))).isFalse()
    }
}
