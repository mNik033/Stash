package com.stash.data.spotify

import com.stash.data.spotify.model.SpotifyTrackItem

/**
 * A playlist's tracks as [SpotifyApiClient.getPlaylistTracks] fetched them,
 * plus whether that walk saw the whole playlist.
 *
 * [partial] is the Spotify analogue of the YouTube client's `PagedTracks.partial`
 * and feeds the same snapshot flag. It exists because a GraphQL page that failed
 * part-way through a long playlist used to end the walk silently and come back
 * as an ordinary success: the sync snapshotted the first N pages as the complete
 * playlist, and REFRESH then mirrored the playlist down to them, unlinking (and,
 * via the orphan sweep, deleting) everything after the failed page.
 *
 * @property tracks        The tracks that were fetched and parsed.
 * @property partial       True when the walk is known to be short — a page after
 *                         the first failed, or Spotify's own count says items
 *                         are missing. Callers must not treat the list as the
 *                         complete playlist.
 * @property partialReason Human-readable diagnostics for [partial]; null when complete.
 * @property countVerified True only when the walk saw every item Spotify's own
 *                         count (`content.totalCount`) says the playlist holds,
 *                         with no failed page. Stricter than "not [partial]",
 *                         whose 95% tolerance is right for deciding a mirror but
 *                         not for proving one complete: the sync lets the next
 *                         run skip a playlist only on this proof, since a short
 *                         mirror it skipped would then stay short. False when
 *                         the response carried no count (the Web API prong
 *                         never asks for one).
 * @property revisionId    The playlist's version as the walk's first page
 *                         reported it (`playlistV2.revisionId`), null when it
 *                         carried none (the Web API prong). The sync stamps a
 *                         playlist only when this equals the revision the
 *                         library listing gave before the fetch, so a playlist
 *                         that changed between the two reads is read again.
 */
data class SpotifyPlaylistTracks(
    val tracks: List<SpotifyTrackItem>,
    val partial: Boolean = false,
    val partialReason: String? = null,
    val countVerified: Boolean = false,
    val revisionId: String? = null,
)

/**
 * One page of the `fetchLibraryTracks` (Liked Songs) listing.
 *
 * @property tracks       The tracks on this page that parsed, post-filter.
 * @property rawItemCount Items Spotify returned BEFORE filtering. Pagination
 *                        must advance on this, not on [tracks].size: a liked
 *                        local file or an unavailable track is dropped by the
 *                        parser, and a "short page" test on the parsed count
 *                        ended the walk right there — mirroring a 1,200-song
 *                        library down to ~149 in REFRESH mode. Same trap that
 *                        [SpotifyLibraryPage.rawItemCount] closes for libraryV3.
 * @property totalCount   `data.me.library.tracks.totalCount`: how many liked
 *                        songs Spotify says the account has, or null when the
 *                        response does not carry it.
 */
data class SpotifyLikedSongsPage(
    val tracks: List<SpotifyTrackItem>,
    val rawItemCount: Int,
    val totalCount: Int? = null,
)

/**
 * Whether [fetched] raw items fall short of the [expected] count a Spotify
 * response advertised. Uses the same 95% tolerance as the YouTube client's
 * `verifyExpectedCount`, so a playlist edited mid-walk (or a total that counts
 * a few items the listing never returns) doesn't flag every sync as partial —
 * that would quietly stop REFRESH from ever propagating a removal.
 */
fun fetchedBelowExpected(fetched: Int, expected: Int?): Boolean =
    expected != null && fetched < expected * 0.95
