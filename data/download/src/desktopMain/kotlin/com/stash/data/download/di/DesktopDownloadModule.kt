package com.stash.data.download.di

import com.stash.core.data.prefs.QualityPreference
import com.stash.data.download.DesktopDownloadExecutor
import com.stash.data.download.DownloadExecutor
import com.stash.data.download.DownloadManager
import com.stash.data.download.DownloadProgressTracker
import com.stash.data.download.files.DesktopEmbedArtScaler
import com.stash.data.download.files.DesktopFfmpegBinaryResolver
import com.stash.data.download.files.DesktopFileOrganizer
import com.stash.data.download.files.DesktopLocalImportCoordinator
import com.stash.data.download.files.EmbedArtScaler
import com.stash.data.download.files.FfmpegBinaryResolver
import com.stash.data.download.files.FileOrganizer
import com.stash.data.download.files.LocalImportCoordinator
import com.stash.data.download.lossless.LosslessSource
import com.stash.data.download.lossless.qbdlx.QbdlxQobuzSource
import com.stash.data.download.lossless.tidal.TidalLosslessSource
import com.stash.data.download.lyrics.LyricsFetchTrigger
import com.stash.data.download.prefs.QualityPreferencesManager
import com.stash.data.download.ytdlp.DesktopYtDlpRunner
import com.stash.data.download.ytdlp.YtDlpRunner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
abstract class DesktopDownloadModule {

    @Binds
    @Singleton
    abstract fun bindYtDlpRunner(impl: DesktopYtDlpRunner): YtDlpRunner

    @Binds
    @Singleton
    abstract fun bindLocalImportCoordinator(impl: DesktopLocalImportCoordinator): LocalImportCoordinator

    @Binds
    @Singleton
    abstract fun bindLibrarySizeHolder(impl: com.stash.data.download.files.DesktopLibrarySizeHolder): com.stash.data.download.files.LibrarySizeHolder

    @Binds
    @Singleton
    abstract fun bindDownloadProgressTracker(impl: DownloadManager): DownloadProgressTracker

    @Binds
    @Singleton
    abstract fun bindQualityPreference(impl: QualityPreferencesManager): QualityPreference

    @Binds
    @Singleton
    abstract fun bindFfmpegBinaryResolver(impl: DesktopFfmpegBinaryResolver): FfmpegBinaryResolver

    @Binds
    @Singleton
    abstract fun bindEmbedArtScaler(impl: DesktopEmbedArtScaler): EmbedArtScaler

    @Binds
    @Singleton
    abstract fun bindFileOrganizer(impl: DesktopFileOrganizer): FileOrganizer

    @Binds
    @Singleton
    abstract fun bindDownloadExecutor(impl: DesktopDownloadExecutor): DownloadExecutor

    @Multibinds
    abstract fun losslessSources(): Set<LosslessSource>

    @Binds
    @IntoSet
    abstract fun bindQbdlxQobuzSource(impl: QbdlxQobuzSource): LosslessSource

    @Binds
    @IntoSet
    abstract fun bindTidalLosslessSource(impl: TidalLosslessSource): LosslessSource

    companion object {
        @Provides
        @Singleton
        fun provideLyricsFetchTrigger(): LyricsFetchTrigger = object : LyricsFetchTrigger {
            override fun enqueueFor(trackId: Long) = Unit
        }
    }
}
