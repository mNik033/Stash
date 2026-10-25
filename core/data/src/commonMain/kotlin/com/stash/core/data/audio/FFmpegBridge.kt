package com.stash.core.data.audio

/**
 * Adapter interface around an ffmpeg runner.
 *
 * The ebur128 filter writes its Summary block to **stderr**, so the contract
 * captures stderr specifically. stdout (typically empty for `-f null -`) is
 * discarded.
 */
interface FFmpegBridge {
    /**
     * Runs ffmpeg with [args] and returns its captured **stderr** as a single
     * String. stdout is consumed (to keep the process pipe drained) but
     * discarded.
     *
     * Suspends on `Dispatchers.IO` — safe to call from any coroutine.
     */
    suspend fun runWithStderrCapture(args: List<String>): String
}
