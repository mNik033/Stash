package com.stash.feature.home

import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
abstract class DesktopHomeModule {
    @Binds
    @Singleton
    abstract fun bindHomeMixWorkerScheduler(impl: DesktopHomeMixWorkerScheduler): HomeMixWorkerScheduler
}

