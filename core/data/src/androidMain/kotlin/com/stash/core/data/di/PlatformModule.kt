package com.stash.core.data.di

import com.stash.core.common.platform.AppPaths
import com.stash.core.common.platform.BackgroundScheduler
import com.stash.core.common.platform.Connectivity
import com.stash.core.common.platform.PlatformInfo
import com.stash.core.data.platform.AndroidAppPaths
import com.stash.core.data.platform.AndroidBackgroundScheduler
import com.stash.core.data.platform.AndroidConnectivity
import com.stash.core.data.platform.AndroidPlatformInfo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds
    @Singleton
    abstract fun bindAppPaths(impl: AndroidAppPaths): AppPaths

    @Binds
    @Singleton
    abstract fun bindConnectivity(impl: AndroidConnectivity): Connectivity

    @Binds
    @Singleton
    abstract fun bindPlatformInfo(impl: AndroidPlatformInfo): PlatformInfo

    @Binds
    @Singleton
    abstract fun bindBackgroundScheduler(impl: AndroidBackgroundScheduler): BackgroundScheduler
}
