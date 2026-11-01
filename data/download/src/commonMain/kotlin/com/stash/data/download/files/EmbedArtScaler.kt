package com.stash.data.download.files

/**
 * Platform abstraction for scaling downloaded album art before embedding.
 */
interface EmbedArtScaler {
    /** [bytes] fitted within the limit, and its long side in pixels (0: unknown / not decodable). */
    class Fitted(val bytes: ByteArray, val longSidePx: Int)

    /**
     * [bytes] fitted within [maxPx] on the long side; [bytes] as they are when within the limit
     * or not decodable; or null when they couldn't be fitted for lack of memory.
     */
    suspend fun fit(bytes: ByteArray, maxPx: Int): Fitted?
}

