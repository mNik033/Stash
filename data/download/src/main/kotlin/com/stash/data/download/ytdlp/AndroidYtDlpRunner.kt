package com.stash.data.download.ytdlp

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidYtDlpRunner @Inject constructor(
    private val ytDlpManager: YtDlpManager,
) : YtDlpRunner {

    override suspend fun execute(
        args: List<String>,
        onProgress: ((Float, Long, String) -> Unit)?,
    ): YtDlpResponse = withContext(Dispatchers.IO) {
        ytDlpManager.initialize()
        val request = YoutubeDLRequest(emptyList())
        for (arg in args) {
            request.addOption(arg)
        }
        val response = YoutubeDL.getInstance().execute(
            request,
            null,
            if (onProgress != null) {
                { progress, eta, line -> onProgress(progress, eta, line) }
            } else null,
        )
        YtDlpResponse(
            exitCode = response.exitCode,
            out = response.out,
            err = response.err,
            elapsedTime = response.elapsedTime,
        )
    }
}
