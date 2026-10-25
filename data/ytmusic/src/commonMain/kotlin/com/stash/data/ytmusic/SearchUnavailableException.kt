package com.stash.data.ytmusic

import java.io.IOException

/**
 * YouTube Music did not answer a search: no connection, a timeout, or an HTTP
 * error. Thrown by [YTMusicApiClient.searchAll] so the Search tab can tell
 * "YouTube didn't answer" (worth a Retry) apart from "YouTube answered with
 * nothing" (No results). The two used to look the same: an offline search
 * showed "No results".
 *
 * @property statusCode The HTTP status, or a negative value when no response
 *   came back at all (InnerTubeClient's `RequestOutcome.STATUS_NETWORK_ERROR`).
 */
class SearchUnavailableException(val statusCode: Int) :
    IOException("YouTube Music didn't answer the search (status $statusCode)") {

    /** True when no HTTP response came back: offline, DNS, a timeout, TLS. */
    val noResponse: Boolean get() = statusCode < 0
}
