package com.stash.core.data.lastfm

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * [isTransientLastFmFailure] is what stands between a 30 s Wi-Fi drop and a
 * permanent "Last.fm has nothing for this track" sentinel in the enrichment
 * workers. Only Last.fm's own "not found" answer may count as definitive.
 */
class LastFmApiExceptionTest {

    @Test fun `a success is never transient`() {
        assertFalse(Result.success(emptyList<String>()).isTransientLastFmFailure())
    }

    @Test fun `Last_fm not found is a definitive answer`() {
        val notFound = Result.failure<Unit>(LastFmApiException(LastFmApiException.NOT_FOUND, "Track not found"))
        assertFalse(notFound.isTransientLastFmFailure())
    }

    @Test fun `network, HTTP and rate-limit failures are transient`() {
        assertTrue(Result.failure<Unit>(IOException("timeout")).isTransientLastFmFailure())
        assertTrue(Result.failure<Unit>(IllegalStateException("Last.fm GET failed: HTTP 503")).isTransientLastFmFailure())
        assertTrue(Result.failure<Unit>(LastFmApiException("29", "Rate Limit Exceeded")).isTransientLastFmFailure())
        assertTrue(Result.failure<Unit>(LastFmApiException("11", "Service Offline")).isTransientLastFmFailure())
    }

    @Test(expected = CancellationException::class)
    fun `a captured cancellation is rethrown, not treated as a failed request`() {
        Result.failure<Unit>(CancellationException("stopped")).isTransientLastFmFailure()
    }
}
