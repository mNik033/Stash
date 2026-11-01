package com.stash.core.data.audio

import com.stash.core.common.Log
import com.stash.core.data.db.dao.TrackDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Measures integrated loudness (LUFS) and true peak (dBFS) of an audio file
 * using ffmpeg's `ebur128` filter.
 */
@Singleton
class LoudnessMeasurer @Inject constructor(
    private val bridge: FFmpegBridge,
    private val trackDao: TrackDao,
    private val chargingMonitor: ChargingMonitor,
) {
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
    private val mutex = Mutex()

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun measure(file: File): Result = mutex.withLock {
        withContext(dispatcher) {
            val args = listOf(
                "-nostats", "-hide_banner",
                "-i", file.absolutePath,
                "-af", "ebur128=peak=true",
                "-f", "null", "-",
            )
            val stderr = try {
                bridge.runWithStderrCapture(args)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext Result.Failed(
                    "ffmpeg invocation failed: ${e.message}",
                )
            }
            parseSummary(stderr)
        }
    }

    internal fun parseSummary(stderr: String): Result {
        if (!stderr.contains("Summary:")) {
            return Result.Failed("no summary block")
        }
        val summary = stderr.substringAfter("Summary:")
        val lufsMatch = LUFS_REGEX.find(summary)
            ?: return Result.Failed("could not parse integrated loudness")
        val lufsRaw = lufsMatch.groupValues[1]
        if (lufsRaw.equals("-inf", ignoreCase = true) ||
            lufsRaw.equals("inf", ignoreCase = true)
        ) {
            return Result.Failed("integrated loudness is -inf (track too short / silent)")
        }
        val lufs = lufsRaw.toFloatOrNull()
            ?: return Result.Failed("could not parse integrated loudness number: $lufsRaw")
        if (lufs.isInfinite() || lufs.isNaN()) {
            return Result.Failed("integrated loudness is inf/NaN (track too short?)")
        }
        val peakMatch = PEAK_REGEX.find(summary)
            ?: return Result.Failed("could not parse true peak")
        val peak = peakMatch.groupValues[1].toFloatOrNull()
            ?: return Result.Failed("could not parse true peak number")
        return Result.Success(lufs = lufs, truePeakDbfs = peak)
    }

    fun measureAndPersistInBackground(trackId: Long, file: File) {
        if (!chargingMonitor.isCharging()) {
            Log.i(TAG, "not charging — deferring loudness for track $trackId to the backfill worker")
            return
        }
        backgroundScope.launch {
            when (val r = measure(file)) {
                is Result.Success -> runCatching {
                    trackDao.updateLoudness(
                        id = trackId,
                        lufs = r.lufs,
                        peak = r.truePeakDbfs,
                        now = System.currentTimeMillis(),
                    )
                }.onFailure { e ->
                    Log.w(TAG, "updateLoudness failed for track $trackId: ${e.message}")
                }
                is Result.Failed -> {
                    Log.w(
                        TAG,
                        "background loudness measurement failed for ${file.absolutePath}: ${r.reason}",
                    )
                    runCatching {
                        trackDao.markLoudnessFailed(
                            id = trackId,
                            now = System.currentTimeMillis(),
                        )
                    }.onFailure { e ->
                        Log.w(TAG, "markLoudnessFailed failed for track $trackId: ${e.message}")
                    }
                }
            }
        }
    }

    sealed class Result {
        data class Success(val lufs: Float, val truePeakDbfs: Float) : Result()
        data class Failed(val reason: String) : Result()
    }

    private companion object {
        private const val TAG = "LoudnessMeasurer"

        val LUFS_REGEX = Regex("""\bI:\s+(-?[\d.]+|-?inf)\s+LUFS""")
        val PEAK_REGEX = Regex("""\bPeak:\s+(-?[\d.]+)\s+dBFS""")
    }
}

