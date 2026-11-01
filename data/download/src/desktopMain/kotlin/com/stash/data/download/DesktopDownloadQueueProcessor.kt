package com.stash.data.download

import com.stash.core.common.Log
import com.stash.core.data.audio.AudioDurationExtractor
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.files.LocalFileOps
import com.stash.core.data.mapper.toDomain
import com.stash.core.data.sync.DownloadQueueTrigger
import com.stash.core.model.DownloadFailureType
import com.stash.core.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopDownloadQueueProcessor @Inject constructor(
    private val downloadManager: DownloadManager,
    private val downloadQueueDao: DownloadQueueDao,
    private val trackDao: TrackDao,
    private val localFileOps: LocalFileOps,
    private val audioDurationExtractor: AudioDurationExtractor,
) : DownloadQueueTrigger {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val triggerChannel = Channel<Unit>(Channel.CONFLATED)
    private val drainMutex = Mutex()

    init {
        scope.launch {
            triggerDrain()
            for (trigger in triggerChannel) {
                drain()
            }
        }
    }

    override fun triggerDrain() {
        triggerChannel.trySend(Unit)
    }

    override suspend fun enqueue(queueId: Long) {
        triggerDrain()
    }

    private suspend fun drain() {
        drainMutex.withLock {
            while (scope.isActive) {
                val pendingList = downloadQueueDao.pendingDiscoveryDownloads()
                if (pendingList.isEmpty()) break

                var processedAny = false
                for (entry in pendingList) {
                    if (!scope.isActive) break
                    if (downloadQueueDao.claimForDownload(entry.id) != 1) continue

                    processedAny = true
                    val trackEntity = trackDao.getById(entry.trackId)
                    if (trackEntity == null) {
                        downloadQueueDao.failIfInProgress(
                            id = entry.id,
                            errorMessage = "Track not found in library",
                            failureType = DownloadFailureType.UNKNOWN,
                            completedAt = System.currentTimeMillis(),
                        )
                        continue
                    }

                    if (trackEntity.isDownloaded && trackEntity.filePath != null && File(trackEntity.filePath).exists()) {
                        downloadQueueDao.completeIfInProgress(entry.id, System.currentTimeMillis())
                        continue
                    }

                    val track = trackEntity.toDomain()
                    val result = try {
                        downloadManager.downloadTrack(track, entry.youtubeUrl)
                    } catch (e: CancellationException) {
                        downloadQueueDao.revertToPendingAfterInterruption(
                            queueId = entry.id,
                            lastErrorMessage = "Download cancelled",
                            lastFailureType = DownloadFailureType.NETWORK,
                        )
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Download failed for track ${track.id}: ${e.message}", e)
                        TrackDownloadResult.Failed(e.message ?: "Unknown error")
                    }

                    when (result) {
                        is TrackDownloadResult.Success -> {
                            if (localFileOps.acceptDownloadOrDelete(result.filePath)) {
                                val fileSize = try { File(result.filePath).length() } catch (_: Exception) { 0L }
                                val meta = audioDurationExtractor.extract(result.filePath)
                                trackDao.markAsDownloaded(
                                    trackId = entry.trackId,
                                    filePath = result.filePath,
                                    fileSizeBytes = fileSize,
                                    sampleRateHz = meta?.sampleRateHz,
                                    bitsPerSample = meta?.bitsPerSample,
                                )
                                downloadQueueDao.completeIfInProgress(entry.id, System.currentTimeMillis())
                            } else {
                                downloadQueueDao.failIfInProgress(
                                    id = entry.id,
                                    errorMessage = "Downloaded file too small",
                                    failureType = DownloadFailureType.STORAGE_ERROR,
                                    completedAt = System.currentTimeMillis(),
                                )
                            }
                        }
                        is TrackDownloadResult.Unmatched -> {
                            downloadQueueDao.failIfInProgress(
                                id = entry.id,
                                errorMessage = "No matching audio found",
                                failureType = DownloadFailureType.NO_MATCH,
                                completedAt = System.currentTimeMillis(),
                            )
                        }
                        is TrackDownloadResult.Failed -> {
                            val lower = result.error.lowercase()
                            val failureType = when {
                                lower.contains("login") || lower.contains("sign in") || lower.contains("403") || lower.contains("unauthorized") || lower.contains("401") ->
                                    DownloadFailureType.AUTH_EXPIRED
                                lower.contains("timeout") || lower.contains("network") || lower.contains("connection") || lower.contains("offline") ->
                                    DownloadFailureType.NETWORK
                                lower.contains("ffmpeg") ->
                                    DownloadFailureType.FFMPEG_ERROR
                                lower.contains("space") || lower.contains("disk") || lower.contains("storage") ->
                                    DownloadFailureType.STORAGE_ERROR
                                else ->
                                    DownloadFailureType.UNKNOWN
                            }
                            downloadQueueDao.failIfInProgress(
                                id = entry.id,
                                errorMessage = result.error,
                                failureType = failureType,
                                completedAt = System.currentTimeMillis(),
                            )
                        }
                        is TrackDownloadResult.Deferred -> {
                            downloadQueueDao.deferIfInProgress(entry.id, result.reason.code)
                        }
                    }
                }

                if (!processedAny) break
            }
        }
    }

    companion object {
        private const val TAG = "DesktopDownloadQueue"
    }
}
