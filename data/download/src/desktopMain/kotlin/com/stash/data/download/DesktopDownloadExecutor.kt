package com.stash.data.download

import com.stash.core.auth.TokenManager
import com.stash.core.common.Log
import com.stash.core.common.platform.AppPaths
import com.stash.data.download.ytdlp.YtDlpRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Desktop implementation of [DownloadExecutor] using [YtDlpRunner] and [CookieFileWriter].
 */
@Singleton
class DesktopDownloadExecutor @Inject constructor(
    private val runner: YtDlpRunner,
    private val tokenManager: TokenManager,
    private val appPaths: AppPaths,
) : DownloadExecutor {

    override suspend fun download(
        url: String,
        outputDir: File,
        filename: String,
        qualityArgs: List<String>,
        onProgress: (Float) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val outputTemplate = outputTemplateFor(outputDir, filename)
        if (outputTemplate == null || !isAcceptedUrl(url)) {
            return@withContext DownloadResult.Error("Invalid output path or URL")
        }

        deleteStaleOutputs(outputDir, filename)

        val cookie = tokenManager.getYouTubeCookie()
        val cookieFile = if (!cookie.isNullOrBlank()) {
            val f = YtCookieFiles.newFile(appPaths.noBackupFilesDir, YtCookieFiles.DOWNLOAD_PREFIX)
            CookieFileWriter.write(cookie, f)
            f
        } else {
            null
        }

        try {
            val args = buildList {
                add("-o"); add(outputTemplate)
                add("--newline")
                cookieFile?.let {
                    add("--cookies"); add(it.absolutePath)
                }
                addAll(qualityArgs)
                add(url)
            }

            val resp = runner.execute(args) { pct, _, _ ->
                onProgress(pct / 100f)
            }

            if (resp.exitCode != 0) {
                Log.w(TAG, "yt-dlp exited with ${resp.exitCode}: ${resp.err}")
                return@withContext DownloadResult.YtDlpError(resp.err.ifBlank { resp.out })
            }

            val candidate = outputDir.listFiles { file ->
                file.name.startsWith("$filename.") && file.extension !in IGNORED_EXTENSIONS
            }?.firstOrNull()

            if (candidate == null || !candidate.exists() || candidate.length() == 0L) {
                return@withContext DownloadResult.NoOutput(resp.out, resp.err)
            }

            DownloadResult.Success(candidate)
        } catch (e: Exception) {
            Log.w(TAG, "Download execution failed for $url: ${e.message}", e)
            DownloadResult.Error(e.message ?: "download failed", e)
        } finally {
            cookieFile?.let { it.delete() }
        }
    }

    override suspend fun downloadSoundCloud(
        permalink: String,
        token: String?,
        outputDir: File,
        filename: String,
        onProgress: (Float) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val link = com.stash.data.soundcloud.SoundCloudLinks.parse(permalink)
        val template = outputTemplateFor(outputDir, filename)
        if (link?.kind != com.stash.data.soundcloud.SoundCloudLink.Kind.TRACK || template == null) {
            return@withContext DownloadResult.Error("Invalid SoundCloud download")
        }
        deleteStaleOutputs(outputDir, filename)
        var success = false
        try {
            val args = buildList {
                add("-o"); add(template)
                add("--newline")
                add("--no-playlist")
                add("--extractor-args"); add("soundcloud:formats=hls_aac,http_aac,hls_opus,http_opus,hls_mp3,http_mp3")
                if (token != null) {
                    add("--username"); add("oauth")
                    add("--password"); add(token)
                }
                add("-f"); add("bestaudio[acodec^=mp4a][format_id!*=preview]/bestaudio[format_id!*=preview]")
                add("-x"); add("--audio-format"); add("m4a")
                add(link.url)
            }
            val resp = runner.execute(args) { pct, _, _ ->
                onProgress(pct / 100f)
            }
            if (resp.exitCode != 0) {
                Log.w(TAG, "yt-dlp SoundCloud exited with ${resp.exitCode}: ${resp.err}")
                return@withContext DownloadResult.YtDlpError("SoundCloud could not download this track")
            }
            val candidate = outputDir.listFiles { file ->
                file.name.startsWith("$filename.") && file.extension !in IGNORED_EXTENSIONS
            }?.firstOrNull()
            if (candidate == null || !candidate.exists() || candidate.length() == 0L) {
                return@withContext DownloadResult.NoOutput(null, null)
            }
            success = true
            DownloadResult.Success(candidate)
        } catch (e: Exception) {
            Log.w(TAG, "SoundCloud download execution failed for $permalink: ${e.message}", e)
            DownloadResult.Error("SoundCloud download failed", e)
        } finally {
            if (!success) deleteStaleOutputs(outputDir, filename)
        }
    }

    private fun outputTemplateFor(outputDir: File, filename: String): String? {
        if (!isSafeFilename(filename)) return null
        val template = File(outputDir, "$filename.%(ext)s")
        val dir = runCatching { outputDir.canonicalFile }.getOrNull() ?: return null
        val resolved = runCatching { template.canonicalFile }.getOrNull() ?: return null
        return template.absolutePath.takeIf { resolved.parentFile == dir }
    }

    private fun deleteStaleOutputs(outputDir: File, filename: String) {
        outputDir.listFiles { file -> file.name.startsWith("$filename.") }?.forEach { it.delete() }
    }

    private fun isAcceptedUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
    }

    private fun isSafeFilename(name: String): Boolean {
        if (name.isBlank() || name.contains('/') || name.contains('\\') || name.contains("..")) return false
        return !name.startsWith(".")
    }

    companion object {
        private const val TAG = "DesktopDownloadExecutor"
        private val IGNORED_EXTENSIONS = setOf("part", "ytdl", "tmp", "json")
    }
}
