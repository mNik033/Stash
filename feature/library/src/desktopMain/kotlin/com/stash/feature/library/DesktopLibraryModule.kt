package com.stash.feature.library

import com.stash.feature.library.allsongs.AllSongsScanner
import com.stash.feature.library.allsongs.DesktopAllSongsScanner
import com.stash.feature.library.mixbuilder.DesktopMixWorkerScheduler
import com.stash.feature.library.mixbuilder.MixWorkerScheduler
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
abstract class DesktopLibraryModule {
    @Binds
    @Singleton
    abstract fun bindPlaylistImageHelper(helper: DesktopPlaylistImageHelper): PlaylistImageHelper

    @Binds
    @Singleton
    abstract fun bindLibraryPreferencesStore(store: DesktopLibraryPreferencesStore): LibraryPreferencesStore

    @Binds
    @Singleton
    abstract fun bindMixWorkerScheduler(scheduler: DesktopMixWorkerScheduler): MixWorkerScheduler

    @Binds
    @Singleton
    abstract fun bindAllSongsScanner(scanner: DesktopAllSongsScanner): AllSongsScanner
}
