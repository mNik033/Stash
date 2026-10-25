package com.stash.data.download.files

import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the music library's on-disk size + lossless breakdown.
 */
interface LibrarySizeHolder {
    val size: StateFlow<LibrarySizeBreakdown>
    fun refresh()
}
