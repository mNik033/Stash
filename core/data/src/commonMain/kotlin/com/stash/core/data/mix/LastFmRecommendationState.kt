package com.stash.core.data.mix

/**
 * UI state for the Last.fm source surface (issue #255). Everything the
 * Sync-tab card and its manage screen render, derived reactively.
 *
 * @property connected   A Last.fm session exists (user completed web auth).
 * @property enabled     The user hasn't turned Recommendations off.
 * @property trackCount  Tracks currently in the materialized playlist;
 *                       null until the first refresh has created it.
 * @property lastRefreshedAt  Epoch-millis of the last successful recipe
 *                       refresh; null never-refreshed.
 */
data class LastFmRecommendationState(
    val connected: Boolean = false,
    val enabled: Boolean = true,
    val trackCount: Int? = null,
    val lastRefreshedAt: Long? = null,
)
