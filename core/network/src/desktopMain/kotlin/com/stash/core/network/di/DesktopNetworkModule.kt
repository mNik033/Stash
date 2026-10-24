package com.stash.core.network.di

import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
interface DesktopNetworkModule {

    @Multibinds
    fun appInterceptors(): Set<@JvmSuppressWildcards Interceptor>

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(
            appInterceptors: Set<@JvmSuppressWildcards Interceptor>,
        ): OkHttpClient = StashNetworkDefaults.createOkHttpClient(
            isDebug = false,
            appInterceptors = appInterceptors,
        )
    }
}
