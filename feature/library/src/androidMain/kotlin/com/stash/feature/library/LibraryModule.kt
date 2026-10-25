package com.stash.feature.library

import com.stash.feature.library.allsongs.AllSongsScanner
import com.stash.feature.library.allsongs.AndroidAllSongsScanner
import com.stash.feature.library.mixbuilder.AndroidMixWorkerScheduler
import com.stash.feature.library.mixbuilder.MixWorkerScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {
    @Binds
    @Singleton
    abstract fun bindPlaylistImageHelper(helper: AndroidPlaylistImageHelper): PlaylistImageHelper

    @Binds
    @Singleton
    abstract fun bindLibraryPreferencesStore(store: AndroidLibraryPreferencesStore): LibraryPreferencesStore

    @Binds
    @Singleton
    abstract fun bindMixWorkerScheduler(scheduler: AndroidMixWorkerScheduler): MixWorkerScheduler

    @Binds
    @Singleton
    abstract fun bindAllSongsScanner(scanner: AndroidAllSongsScanner): AllSongsScanner
}
