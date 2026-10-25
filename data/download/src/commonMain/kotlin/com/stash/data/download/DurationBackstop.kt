package com.stash.data.download

/** Absolute ceiling (ms) under which a finished file is "sample-length". */
internal const val LOSSLESS_SAMPLE_MAX_MS = 35_000L

/** A degraded file must also be below this fraction of the expected duration. */
internal const val LOSSLESS_SAMPLE_FRACTION = 0.5

/**
 * True when a finished lossless download is a preview stub: a readable
 * duration that is both absolutely short (≤ [LOSSLESS_SAMPLE_MAX_MS]) AND far
 * below the track's known duration (< [LOSSLESS_SAMPLE_FRACTION] of expected).
 * Requires a known expected duration (> 0) and a readable probe; otherwise
 * returns false — never reject on missing data (that's the yt-dlp path's job).
 */
internal fun isLosslessDurationDegraded(probedMs: Long?, expectedMs: Long): Boolean {
    if (probedMs == null || probedMs <= 0L) return false
    if (expectedMs <= 0L) return false
    return probedMs <= LOSSLESS_SAMPLE_MAX_MS && probedMs < expectedMs * LOSSLESS_SAMPLE_FRACTION
}
