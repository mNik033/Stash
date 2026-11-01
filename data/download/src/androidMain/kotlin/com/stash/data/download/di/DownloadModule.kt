package com.stash.data.download.di

import com.stash.core.data.prefs.QualityPreference
import com.stash.core.data.sync.TrackDownloader
import com.stash.data.download.AndroidDownloadExecutor
import com.stash.data.download.DownloadExecutor
import com.stash.data.download.TrackDownloaderImpl
import com.stash.data.download.files.AndroidEmbedArtScaler
import com.stash.data.download.files.AndroidFfmpegBinaryResolver
import com.stash.data.download.files.AndroidFileOrganizer
import com.stash.data.download.files.EmbedArtScaler
import com.stash.data.download.files.FfmpegBinaryResolver
import com.stash.data.download.files.FileOrganizer
import com.stash.data.download.prefs.QualityPreferencesManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {

    @Binds
    @Singleton
    abstract fun bindTrackDownloader(impl: TrackDownloaderImpl): TrackDownloader

    @Binds
    @Singleton
    abstract fun bindQualityPreference(impl: QualityPreferencesManager): QualityPreference

    @Binds
    @Singleton
    abstract fun bindUploadNaming(impl: com.stash.data.download.cloud.DriveUploadNaming): com.stash.data.cloud.upload.UploadNaming

    @Binds
    @Singleton
    abstract fun bindYtDlpRunner(impl: com.stash.data.download.ytdlp.AndroidYtDlpRunner): com.stash.data.download.ytdlp.YtDlpRunner

    @Binds
    @Singleton
    abstract fun bindLocalImportCoordinator(impl: com.stash.data.download.files.RealLocalImportCoordinator): com.stash.data.download.files.LocalImportCoordinator

    @Binds
    @Singleton
    abstract fun bindLibrarySizeHolder(impl: com.stash.data.download.files.AndroidLibrarySizeHolder): com.stash.data.download.files.LibrarySizeHolder

    @Binds
    @Singleton
    abstract fun bindDownloadProgressTracker(impl: com.stash.data.download.DownloadManager): com.stash.data.download.DownloadProgressTracker

    @Binds
    @Singleton
    abstract fun bindFfmpegBinaryResolver(impl: AndroidFfmpegBinaryResolver): FfmpegBinaryResolver

    @Binds
    @Singleton
    abstract fun bindEmbedArtScaler(impl: AndroidEmbedArtScaler): EmbedArtScaler

    @Binds
    @Singleton
    abstract fun bindFileOrganizer(impl: AndroidFileOrganizer): FileOrganizer

    @Binds
    @Singleton
    abstract fun bindDownloadExecutor(impl: AndroidDownloadExecutor): DownloadExecutor
}
