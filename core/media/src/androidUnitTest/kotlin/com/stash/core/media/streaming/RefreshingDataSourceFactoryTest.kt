package com.stash.core.media.streaming

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.entity.TrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for [RefreshingDataSource] — the per-track DataSource wrapper that
 * catches 403/410 mid-stream, re-resolves via Kennyy, caches the fresh URL,
 * and retries [open] at the same byte offset.
 *
 * Uses [RobolectricTestRunner] so [android.net.Uri.parse] / `Uri.EMPTY`
 * resolve to real Android implementations rather than the stubbed framework
 * defaults — the production code parses URI strings inside [open].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RefreshingDataSourceFactoryTest {

    private val fakeInner: HttpDataSource = mockk(relaxed = true)
    private val fakeResolver: StreamSourceRegistry = mockk()
    private val fakeCache: StreamUrlCache = mockk(relaxed = true)
    private val fakeTrackDao: TrackDao = mockk()

    private val track = TrackEntity(
        id = 42L,
        title = "Some Song",
        artist = "Some Artist",
        album = "Some Album",
        durationMs = 210_000L,
        isrc = "USRC17607839",
    )

    @Before
    fun setUp() {
        // Default: DAO returns the track for id 42; tests that need a
        // missing track override this.
        coEvery { fakeTrackDao.getById(42L) } returns track
        // Default: nothing refreshed this track yet.
        every { fakeCache.get(any()) } returns null
    }

    private fun newSource(
        trackId: Long = 42L,
        lastRefreshed: AtomicReference<String?> = AtomicReference(null),
    ): RefreshingDataSource =
        RefreshingDataSource(
            inner = fakeInner,
            resolver = fakeResolver,
            cache = fakeCache,
            trackId = trackId,
            trackDao = fakeTrackDao,
            lastRefreshed = lastRefreshed,
        )

    private fun staleSpec(): DataSpec = DataSpec.Builder()
        .setUri(Uri.parse("https://stale-url?etsp=1700000000"))
        .setPosition(500L)
        .build()

    @Test
    fun open_returnsLengthFromInnerOnHappyPath() {
        every { fakeInner.open(any()) } returns 1024L

        val source = newSource()

        assertThat(source.open(staleSpec())).isEqualTo(1024L)
    }

    @Test
    fun open_on403_reResolvesAndRetriesFromSameOffset() {
        val freshUrl = StreamUrl(
            url = "https://fresh-url?etsp=1800000000",
            expiresAtMs = 1_800_000_000_000L,
        )

        // First open() throws 403; second open() succeeds with the fresh URL.
        every { fakeInner.open(match { it.uri.toString().contains("stale-url") }) } throws
            invalidResponseCodeException(403)
        every { fakeInner.open(match { it.uri.toString().contains("fresh-url") }) } returns 1024L
        coEvery { fakeResolver.resolveYouTubeRefresh(track) } returns freshUrl

        val source = newSource()
        val length = source.open(staleSpec())

        assertThat(length).isEqualTo(1024L)
        verify { fakeCache.put(42L, freshUrl) }
        // Position must be preserved across the URL swap.
        verify { fakeInner.open(match { it.uri.toString().contains("fresh-url") && it.position == 500L }) }
    }

    @Test
    fun open_on410_reResolvesLikeOn403() {
        val freshUrl = StreamUrl(
            url = "https://fresh-url-410?etsp=1800000000",
            expiresAtMs = 1_800_000_000_000L,
        )

        every { fakeInner.open(match { it.uri.toString().contains("stale-url") }) } throws
            invalidResponseCodeException(410)
        every { fakeInner.open(match { it.uri.toString().contains("fresh-url-410") }) } returns 2048L
        coEvery { fakeResolver.resolveYouTubeRefresh(track) } returns freshUrl

        val source = newSource()
        val length = source.open(staleSpec())

        assertThat(length).isEqualTo(2048L)
        verify { fakeCache.put(42L, freshUrl) }
    }

    @Test
    fun open_on500_propagatesError() {
        val exception = invalidResponseCodeException(500)
        every { fakeInner.open(any()) } throws exception

        val source = newSource()
        val thrown = try {
            source.open(staleSpec())
            null
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            e
        }

        assertThat(thrown).isNotNull()
        assertThat(thrown!!.responseCode).isEqualTo(500)
        // Must NOT have attempted a re-resolve or cache write.
        coVerify(exactly = 0) { fakeResolver.resolveYouTubeRefresh(any()) }
        verify(exactly = 0) { fakeCache.put(any(), any()) }
    }

    @Test
    fun open_whenTrackMissing_propagatesOriginalError() {
        coEvery { fakeTrackDao.getById(42L) } returns null
        val exception = invalidResponseCodeException(403)
        every { fakeInner.open(any()) } throws exception

        val source = newSource()
        val thrown = try {
            source.open(staleSpec())
            null
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            e
        }

        assertThat(thrown).isNotNull()
        assertThat(thrown!!.responseCode).isEqualTo(403)
        verify(exactly = 0) { fakeCache.put(any(), any()) }
    }

    @Test
    fun open_whenResolverReturnsNull_propagatesOriginalError() {
        val exception = invalidResponseCodeException(403)
        every { fakeInner.open(any()) } throws exception
        coEvery { fakeResolver.resolveYouTubeRefresh(track) } returns null

        val source = newSource()
        val thrown = try {
            source.open(staleSpec())
            null
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            e
        }

        assertThat(thrown).isNotNull()
        assertThat(thrown!!.responseCode).isEqualTo(403)
        verify(exactly = 0) { fakeCache.put(any(), any()) }
    }

    @Test
    fun open_afterRefresh_laterOpensGoStraightToTheRefreshedUrl() {
        // Every uncached seek re-opens the MediaItem's baked (dead) URL. It used to
        // take the 403 and walk the whole resolver chain again on every one; the
        // cast server, which builds a source per HTTP request, paid that per range.
        val freshUrl = StreamUrl(url = "https://fresh-url?itag=251&clen=9000", expiresAtMs = Long.MAX_VALUE)
        every { fakeInner.open(match { it.uri.toString().contains("stale-url") }) } throws
            invalidResponseCodeException(403)
        every { fakeInner.open(match { it.uri.toString().contains("fresh-url") }) } returns 1024L
        coEvery { fakeResolver.resolveYouTubeRefresh(track) } returns freshUrl
        val shared = AtomicReference<String?>(null)

        newSource(lastRefreshed = shared).open(youTubeSpec("stale-url", position = 500L))
        // A later source for the same item (a new period, or the next cast request).
        newSource(lastRefreshed = shared).open(youTubeSpec("stale-url", position = 9_000L))

        coVerify(exactly = 1) { fakeResolver.resolveYouTubeRefresh(any()) }
        verify(exactly = 1) { fakeInner.open(match { it.uri.toString().contains("stale-url") }) }
        verify { fakeInner.open(match { it.uri.toString().contains("fresh-url") && it.position == 9_000L }) }
    }

    @Test
    fun open_on403_usesASameFileUrlAlreadyInTheCacheWithoutResolving() {
        every { fakeCache.get(42L) } returns StreamUrl(
            url = "https://cached-url?itag=251&clen=9000",
            expiresAtMs = Long.MAX_VALUE,
            origin = YouTubeStreamResolver.ORIGIN,
        )
        every { fakeInner.open(match { it.uri.toString().contains("stale-url") }) } throws
            invalidResponseCodeException(403)
        every { fakeInner.open(match { it.uri.toString().contains("cached-url") }) } returns 1024L

        assertThat(newSource().open(youTubeSpec("stale-url", position = 500L))).isEqualTo(1024L)
        coVerify(exactly = 0) { fakeResolver.resolveYouTubeRefresh(any()) }
    }

    @Test
    fun open_on403_ignoresACachedUrlFromAnotherSource() {
        // The cache is keyed by track id alone: a qbdlx FLAC URL there must never be
        // read from this offset into the WebM extractor set up for the YouTube file.
        every { fakeCache.get(42L) } returns StreamUrl(
            url = "https://qobuz-cdn/file.flac",
            expiresAtMs = Long.MAX_VALUE,
            origin = "qbdlx", // QbdlxStreamResolver's origin (its constant is private)
        )
        every { fakeInner.open(match { it.uri.toString().contains("stale-url") }) } throws
            invalidResponseCodeException(403)
        coEvery { fakeResolver.resolveYouTubeRefresh(track) } returns null

        assertThrows403 { newSource().open(youTubeSpec("stale-url", position = 500L)) }
        verify(exactly = 0) { fakeInner.open(match { it.uri.toString().contains("qobuz-cdn") }) }
    }

    @Test
    fun open_whenRefreshReturnsADifferentFile_failsWithTheOriginalError() {
        // Save Data toggled since the item was built: the refresh comes back as itag
        // 249, another encoding and length. Splicing it in at byte 500 is what this
        // must refuse.
        every { fakeInner.open(any()) } throws invalidResponseCodeException(403)
        coEvery { fakeResolver.resolveYouTubeRefresh(track) } returns StreamUrl(
            url = "https://fresh-url?itag=249&clen=4000",
            expiresAtMs = Long.MAX_VALUE,
            origin = YouTubeStreamResolver.ORIGIN,
        )

        assertThrows403 { newSource().open(youTubeSpec("stale-url", position = 500L)) }
        verify(exactly = 0) { fakeInner.open(match { it.uri.toString().contains("fresh-url") }) }
        verify(exactly = 0) { fakeCache.put(any(), any()) }
    }

    @Test
    fun open_whenTheRefreshTimesOut_failsWithTheOriginalHttpError() {
        // A CancellationException escaping open() is not an IOException: Media3 wraps
        // it as UnexpectedLoaderException and never retries the load.
        every { fakeInner.open(any()) } throws invalidResponseCodeException(403)
        coEvery { fakeResolver.resolveYouTubeRefresh(track) } coAnswers {
            withTimeout(1L) { delay(1_000L) }
            null
        }

        assertThrows403 { newSource().open(youTubeSpec("stale-url", position = 500L)) }
    }

    private fun youTubeSpec(host: String, position: Long): DataSpec = DataSpec.Builder()
        .setUri(Uri.parse("https://$host/videoplayback?itag=251&clen=9000&expire=1700000000"))
        .setPosition(position)
        .build()

    private fun assertThrows403(block: () -> Unit) {
        val thrown = try {
            block()
            null
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            e
        }
        assertThat(thrown).isNotNull()
        assertThat(thrown!!.responseCode).isEqualTo(403)
    }

    private fun invalidResponseCodeException(
        code: Int,
    ): HttpDataSource.InvalidResponseCodeException = HttpDataSource.InvalidResponseCodeException(
        code,
        "Forbidden",
        /* cause = */ null,
        /* headerFields = */ emptyMap(),
        DataSpec(Uri.EMPTY),
        ByteArray(0),
    )
}
