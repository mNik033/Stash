package com.stash.data.download.di

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
}
