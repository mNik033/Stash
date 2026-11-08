package com.stash.feature.home

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AndroidHomeModule {
    @Binds
    @Singleton
    abstract fun bindHomeMixWorkerScheduler(impl: AndroidHomeMixWorkerScheduler): HomeMixWorkerScheduler
}

