package com.stash.feature.home

/**
 * Whether Daily Discover is on its way, so Home holds the hero slot with a
 * "Building your Daily Discover…" card instead of leaving it empty (on a fresh
 * install the hero only appears once the first mix refresh has materialized
 * the playlist and its first tracks have landed, which can take a while).
 *
 * The same rule as a custom mix's BUILDING state (`mixBuildState`, which
 * built-ins otherwise skip): not materialized yet, or materialized but still
 * empty while discovery owes it tracks. Materialized, empty and owed nothing
 * means it found nothing to build from, so the slot stays empty rather than
 * promise a mix that isn't coming. An inactive recipe (Stash Mixes turned off
 * in Settings) is never building.
 *
 * @param active               The builtin recipe's `isActive`.
 * @param materialized         The recipe has a playlist (`playlistId != null`).
 * @param trackCount           That playlist's track count (0 when not materialized).
 * @param pendingDiscoveryCount The recipe's non-failed discovery rows.
 */
internal fun dailyDiscoverBuilding(
    active: Boolean,
    materialized: Boolean,
    trackCount: Int,
    pendingDiscoveryCount: Int,
): Boolean = active && trackCount == 0 && (!materialized || pendingDiscoveryCount > 0)
