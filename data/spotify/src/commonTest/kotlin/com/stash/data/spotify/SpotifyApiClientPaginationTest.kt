package com.stash.data.spotify

import com.stash.core.auth.TokenManager
import com.stash.core.auth.spotify.SpotifyAuthManager
import com.stash.core.model.SyncResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sync's REFRESH mode mirrors a playlist DOWN to whatever the fetch
 * returned, so a fetch that quietly stops early deletes real data. These pin
 * the two Spotify walks that used to do exactly that:
 *
 *  - `fetchPlaylist` (GraphQL): a page after the first failing (HTTP 429/5xx,
 *    or a 200 carrying only `errors`) ended the walk and the pages read so far
 *    came back as an ordinary, complete success.
 *  - `fetchLibraryTracks` (Liked Songs): one filtered item made a full page
 *    parse short, so the sync's `parsed < pageSize` test ended the walk there.
 */
class SpotifyApiClientPaginationTest {

    /** Response per (operation, offset); anything unlisted answers HTTP 500. */
    private val pages = mutableMapOf<Pair<String, Int>, Pair<Int, String>>()

    private val tokenManager = mockk<TokenManager> {
        coEvery { getSpotifyAccessToken() } returns "access-token"
        coEvery { getSpotifyClientId() } returns "client-id"
        coEvery { forceRefreshSpotifyAccessToken() } returns null
    }

    private val authManager = mockk<SpotifyAuthManager> {
        every { getClientVersion() } returns "1.2.3.4.g0"
        coEvery { getClientToken("client-id") } returns "client-token"
        // No client_credentials token: the Web API prong is skipped, so every
        // playlist fetch goes through the GraphQL walk under test.
        coEvery { getClientCredentialsToken() } returns null
        every { scrapePersistedQueryHash(any(), any()) } returns null
    }

    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val url = chain.request().url
        val operation = url.queryParameter("operationName").orEmpty()
        val offset = Regex(""""offset":(\d+)""")
            .find(url.queryParameter("variables").orEmpty())!!.groupValues[1].toInt()
        val (code, body) = pages[operation to offset] ?: (500 to """{"error":"boom"}""")
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()

    private val client = SpotifyApiClient(http, tokenManager, authManager)

    // ── fetchPlaylist (GraphQL) ──────────────────────────────────────────

    @Test fun `a failed page after the first marks the playlist partial`() = runBlocking {
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 100, totalCount = 250)
        // offset 100 is unlisted: HTTP 500, which executeGraphQL turns into null.

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertEquals(100, result.data.tracks.size)
        assertTrue("a truncated walk must not pass for the whole playlist", result.data.partial)
    }

    @Test fun `an errors-only page after the first marks the playlist partial`() = runBlocking {
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 100, totalCount = 250)
        pages["fetchPlaylist" to 100] = 200 to """{"errors":[{"message":"rate limited"}]}"""

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertEquals(100, result.data.tracks.size)
        assertTrue(result.data.partial)
    }

    @Test fun `a walk short of Spotify's own totalCount is partial`() = runBlocking {
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 40, totalCount = 300)

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertTrue(result.data.partial)
    }

    @Test fun `a complete walk is not partial`() = runBlocking {
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 100, totalCount = 130)
        pages["fetchPlaylist" to 100] = 200 to playlistPage(range = 100 until 130, totalCount = 130)

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertEquals(130, result.data.tracks.size)
        assertFalse(result.data.partial)
    }

    @Test fun `a filtered item on a full page does not end the walk`() = runBlocking {
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 100, totalCount = 150, localAt = 7)
        pages["fetchPlaylist" to 100] = 200 to playlistPage(range = 100 until 150, totalCount = 150)

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertEquals(149, result.data.tracks.size)
        assertFalse(result.data.partial)
    }

    // ── countVerified: the proof the sync needs to skip a playlist later ──

    @Test fun `a walk that saw every item Spotify counts is count-verified`() = runBlocking {
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 100, totalCount = 130)
        pages["fetchPlaylist" to 100] = 200 to playlistPage(range = 100 until 130, totalCount = 130)

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertTrue(result.data.countVerified)
    }

    @Test fun `a filtered item still counts toward the verified walk`() = runBlocking {
        // A local file is dropped by the parser but was walked: the raw count matches Spotify's.
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 100, totalCount = 150, localAt = 7)
        pages["fetchPlaylist" to 100] = 200 to playlistPage(range = 100 until 150, totalCount = 150)

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertTrue(result.data.countVerified)
    }

    @Test fun `a walk inside the 95 percent tolerance is complete but not count-verified`() = runBlocking {
        // 97 of 100: fine to mirror (not partial), not proof enough to skip next time.
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 97, totalCount = 100)

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertFalse(result.data.partial)
        assertFalse(result.data.countVerified)
    }

    @Test fun `a walk with no count from Spotify is not count-verified`() = runBlocking {
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 30, totalCount = null)

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertFalse(result.data.partial)
        assertFalse(result.data.countVerified)
    }

    @Test fun `a partial walk is never count-verified`() = runBlocking {
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 100, totalCount = 200)
        // offset 100 is unlisted: HTTP 500 → the walk is partial.

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertTrue(result.data.partial)
        assertFalse(result.data.countVerified)
    }

    // ── revisionId: what the sync cross-checks against the listing's ─────

    @Test fun `the walk reports the revision its first page carried`() = runBlocking {
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 100, totalCount = 130, revisionId = "AAAAAFirst")
        pages["fetchPlaylist" to 100] = 200 to playlistPage(range = 100 until 130, totalCount = 130, revisionId = "AAAAALater")

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertEquals("the version the walk started from", "AAAAAFirst", result.data.revisionId)
    }

    @Test fun `a page with no revision reports none`() = runBlocking {
        pages["fetchPlaylist" to 0] = 200 to playlistPage(range = 0 until 30, totalCount = 30)

        val result = client.getPlaylistTracks("pl") as SyncResult.Success

        assertNull(result.data.revisionId)
        assertTrue("the page itself still parses", result.data.countVerified)
    }

    @Test fun `a failed first page is still an error`() = runBlocking {
        // Nothing listed: every page answers HTTP 500.
        assertTrue(client.getPlaylistTracks("pl") is SyncResult.Error)
    }

    // ── fetchLibraryTracks (Liked Songs) ─────────────────────────────────

    @Test fun `a liked page with a filtered item reports the raw count`() = runBlocking {
        pages["fetchLibraryTracks" to 0] = 200 to likedPage(range = 0 until 50, totalCount = 1200, localAt = 3)

        val page = (client.getLikedSongs(limit = 50, offset = 0) as SyncResult.Success).data

        assertEquals(49, page.tracks.size)
        assertEquals("pagination must see the full page", 50, page.rawItemCount)
        assertEquals(1200, page.totalCount)
    }

    @Test fun `a liked page whose every item is filtered is still a success`() = runBlocking {
        pages["fetchLibraryTracks" to 50] = 200 to likedPage(range = 50 until 52, totalCount = 60, localAt = null, allLocal = true)

        val page = (client.getLikedSongs(limit = 50, offset = 50) as SyncResult.Success).data

        assertEquals(0, page.tracks.size)
        assertEquals(2, page.rawItemCount)
    }

    @Test fun `an empty liked page is Empty`() = runBlocking {
        pages["fetchLibraryTracks" to 100] = 200 to likedPage(range = 100 until 100, totalCount = 100)

        assertTrue(client.getLikedSongs(limit = 50, offset = 100) is SyncResult.Empty)
    }

    @Test fun `an unrecognised liked response is an error, not the end of the library`() = runBlocking {
        pages["fetchLibraryTracks" to 50] = 200 to """{"errors":[{"message":"rate limited"}]}"""

        assertTrue(client.getLikedSongs(limit = 50, offset = 50) is SyncResult.Error)
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private fun trackData(i: Int, local: Boolean): String {
        val uri = if (local) "spotify:local:Artist:Album:Local+$i:200" else "spotify:track:track$i"
        return """{"__typename":"Track","uri":"$uri","name":"Track $i",""" +
            """"artists":{"items":[{"uri":"spotify:artist:a$i","profile":{"name":"Artist $i"}}]}}"""
    }

    /**
     * A `fetchPlaylist` page; [totalCount] null leaves the count out, as an older
     * shape would, and [revisionId] null leaves the playlist's version out.
     */
    private fun playlistPage(range: IntRange, totalCount: Int?, localAt: Int? = null, revisionId: String? = null): String {
        val items = range.joinToString(",") { i ->
            """{"itemV2":{"data":${trackData(i, local = i == localAt)}}}"""
        }
        val count = totalCount?.let { """"totalCount":$it,""" }.orEmpty()
        val revision = revisionId?.let { """"revisionId":"$it",""" }.orEmpty()
        return """{"data":{"playlistV2":{$revision"content":{$count"items":[$items]}}}}"""
    }

    private fun likedPage(
        range: IntRange,
        totalCount: Int,
        localAt: Int? = null,
        allLocal: Boolean = false,
    ): String {
        val items = range.joinToString(",") { i ->
            """{"addedAt":{"isoString":"2026-01-01T00:00:00Z"},""" +
                """"track":{"data":${trackData(i, local = allLocal || i == localAt)}}}"""
        }
        return """{"data":{"me":{"library":{"tracks":{"totalCount":$totalCount,"items":[$items]}}}}}"""
    }
}
