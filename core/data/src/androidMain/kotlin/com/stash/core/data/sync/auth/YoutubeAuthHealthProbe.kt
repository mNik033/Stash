package com.stash.core.data.sync.auth

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YoutubeAuthHealthProbe @Inject constructor() : AuthHealthProbe {
    override val source: AuthSource = AuthSource.YOUTUBE

    /**
     * Always false: YouTube cookie expiry can't be detected from a content
     * browse yet, so the probe makes no request at all.
     *
     * It used to walk the whole paginated library (getUserPlaylists: up to 100
     * continuation pages with retry backoffs, outside the sync's auth session)
     * on every sync, then ignore the answer in every branch, while
     * PlaylistFetchWorker fetched the same list again straight after. That
     * doubled the requests, the sync-start latency and the rate-limit
     * exposure for nothing.
     *
     * Why no answer was usable:
     *  - Emptiness is NOT an expiry signal. getUserPlaylists() returns
     *    SyncResult.Empty both for a logged-out request and for an
     *    authenticated account with no playlists in its YouTube Music library
     *    (very common for users who linked YouTube only as a download source).
     *    The old `Empty -> true` mapping false-flagged the latter as
     *    "credentials expired", which short-circuited the ENTIRE sync (Spotify
     *    included) and surfaced a "re-authenticate YouTube" prompt no cookie
     *    re-paste could ever clear.
     *  - An Error is no better: the InnerTube layer turns an unauthorized
     *    browse into a null body, not a distinct status code, so a 401 and a
     *    network error look the same.
     *
     * KNOWN GAP: genuine YouTube cookie expiry is therefore NOT proactively
     * detected; YT just returns no data until the user re-pastes a cookie
     * (PlaylistFetchWorker keeps that from hiding the library in REFRESH).
     * Unlike Spotify's sp_dc there is no silent YT re-auth flow to drive, so a
     * missing banner is tolerable; the false POSITIVE this replaced was
     * actively blocking sync, which is far worse. To restore a real signal,
     * thread the 401 status from InnerTubeClient.browse() through a single
     * non-paginated browse and map it to `true` here.
     */
    override suspend fun isExpired(): Boolean = false
}
