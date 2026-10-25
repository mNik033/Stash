package com.stash.data.download.search

import com.stash.data.download.lossless.AudioFormat
import com.stash.data.download.lossless.SourceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Search tab's lossless download read the preview cache under the preview's
 * `lossless:<videoId>` key, which names the song, not the file: a CD-quality preview
 * followed by a hi-res download spliced the two files and stopped at the preview's
 * length. And nothing checked the result before it was committed.
 */
class SearchLosslessFileTest {

    private fun match(
        sourceTrackId: String? = "12345",
        bits: Int = 24,
        rate: Int = 96_000,
        url: String = "https://cdn.example/file.flac?sig=a",
    ) = SourceResult(
        sourceId = "qbdlx_qobuz",
        downloadUrl = url,
        format = AudioFormat(codec = "flac", bitrateKbps = 0, sampleRateHz = rate, bitsPerSample = bits),
        confidence = 0.95f,
        sourceTrackId = sourceTrackId,
    )

    @Test
    fun `the cache key names the file, not the song`() {
        val hiRes = SearchLosslessFile.cacheKey(match())
        val cd = SearchLosslessFile.cacheKey(match(bits = 16, rate = 44_100))
        val otherTrack = SearchLosslessFile.cacheKey(match(sourceTrackId = "67890"))

        assertNotEquals(hiRes, cd)
        assertNotEquals(hiRes, otherTrack)
        // The same file through a freshly signed URL shares its cached bytes.
        assertEquals(hiRes, SearchLosslessFile.cacheKey(match(url = "https://cdn.example/file.flac?sig=b")))
        assertNotEquals("lossless:dQw4w9WgXcQ", hiRes)
    }

    @Test
    fun `without a source track id the URL keys the file`() {
        assertNotEquals(
            SearchLosslessFile.cacheKey(match(sourceTrackId = null, url = "https://a/1")),
            SearchLosslessFile.cacheKey(match(sourceTrackId = null, url = "https://a/2")),
        )
    }

    private fun problem(
        bytesWritten: Long = 1_000,
        expectedBytes: Long = 1_000,
        probedFormat: String? = "flac",
        probedMs: Long? = 240_000,
        expectedMs: Long = 240_000,
    ) = SearchLosslessFile.problem("flac", bytesWritten, expectedBytes, probedFormat, probedMs, expectedMs)

    @Test
    fun `a complete FLAC of the track's length passes`() {
        assertNull(problem())
    }

    @Test
    fun `a short copy is rejected`() {
        assertNotNull(problem(bytesWritten = 600))
    }

    @Test
    fun `a file that is not FLAC is rejected`() {
        assertNotNull(problem(probedFormat = "aac"))
    }

    @Test
    fun `a preview-length stub is rejected`() {
        assertNotNull(problem(probedMs = 30_000))
    }

    @Test
    fun `missing data never rejects`() {
        assertNull(problem(expectedBytes = -1, bytesWritten = 5))
        assertNull(problem(probedFormat = null, probedMs = null))
        assertNull(problem(probedFormat = "unknown"))
        assertNull(problem(probedMs = 30_000, expectedMs = 0))
    }
}
