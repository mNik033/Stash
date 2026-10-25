package com.stash.data.ytmusic

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [YTMusicApiClient.searchAll] tells "YouTube didn't answer" (throws
 * [SearchUnavailableException], so Search shows an error with Retry) apart from
 * "YouTube answered with nothing" (empty sections, so Search shows No results).
 * Both used to come back empty: an offline search read as "No results".
 */
class SearchAllUnansweredTest {

    private fun clientWith(outcome: InnerTubeClient.RequestOutcome): YTMusicApiClient {
        val inner = mock<InnerTubeClient>()
        kotlinx.coroutines.runBlocking {
            whenever(inner.searchWithStatus(any(), anyOrNull())).thenReturn(outcome)
        }
        return YTMusicApiClient(inner)
    }

    @Test fun `no response at all throws, flagged as no response`() = runTest {
        val client = clientWith(
            InnerTubeClient.RequestOutcome(body = null, statusCode = InnerTubeClient.RequestOutcome.STATUS_NETWORK_ERROR),
        )
        try {
            client.searchAll("radiohead")
            fail("expected SearchUnavailableException")
        } catch (e: SearchUnavailableException) {
            assertTrue(e.noResponse)
        }
    }

    @Test fun `an HTTP error throws with its status`() = runTest {
        val client = clientWith(InnerTubeClient.RequestOutcome(body = null, statusCode = 429))
        try {
            client.searchAll("radiohead")
            fail("expected SearchUnavailableException")
        } catch (e: SearchUnavailableException) {
            assertEquals(429, e.statusCode)
            assertEquals(false, e.noResponse)
        }
    }

    @Test fun `an answer with no shelves is an empty result, not an error`() = runTest {
        val empty = Json.parseToJsonElement("""{"contents":{}}""").jsonObject
        val client = clientWith(InnerTubeClient.RequestOutcome(body = empty, statusCode = 200))

        assertTrue(client.searchAll("asdfghjkl").sections.isEmpty())
    }
}
