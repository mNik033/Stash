package com.stash.data.download.di

import com.stash.data.download.files.DesktopLocalImportCoordinator
import com.stash.data.download.files.LocalImportCoordinator
import com.stash.data.download.ytdlp.DesktopYtDlpRunner
import com.stash.data.download.ytdlp.YtDlpRunner
import dagger.Binds
import dagger.Module
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
    abstract fun bindDownloadProgressTracker(impl: com.stash.data.download.DesktopDownloadProgressTracker): com.stash.data.download.DownloadProgressTracker
}



