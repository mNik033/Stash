package com.stash.desktop.di

import com.stash.core.auth.TokenManager
import com.stash.core.auth.TokenManagerImpl
import com.stash.core.auth.crypto.EncryptionManager
import com.stash.core.auth.store.TokenStore
import com.stash.core.common.platform.AppPaths
import com.stash.core.common.platform.BackgroundScheduler
import com.stash.core.common.platform.Connectivity
import com.stash.core.common.platform.PlatformInfo
import com.stash.core.media.engine.PlaybackEngine
import com.stash.desktop.platform.DesktopAppPaths
import com.stash.desktop.platform.DesktopConnectivity
import com.stash.desktop.platform.DesktopEncryptionManager
import com.stash.desktop.platform.DesktopPlatformInfo
import com.stash.desktop.platform.DesktopScheduler
import com.stash.desktop.platform.DesktopTokenStore
import com.stash.desktop.platform.MpvPlaybackEngine
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
abstract class DesktopPlatformModule {

    @Binds
    @Singleton
    abstract fun bindAppPaths(impl: DesktopAppPaths): AppPaths

    @Binds
    @Singleton
    abstract fun bindConnectivity(impl: DesktopConnectivity): Connectivity

    @Binds
    @Singleton
    abstract fun bindPlatformInfo(impl: DesktopPlatformInfo): PlatformInfo

    @Binds
    @Singleton
    abstract fun bindBackgroundScheduler(impl: DesktopScheduler): BackgroundScheduler

    @Binds
    @Singleton
    abstract fun bindEncryptionManager(impl: DesktopEncryptionManager): EncryptionManager

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: DesktopTokenStore): TokenStore

    @Binds
    @Singleton
    abstract fun bindPlaybackEngine(impl: MpvPlaybackEngine): PlaybackEngine

    @Binds
    @Singleton
    abstract fun bindTokenManager(impl: TokenManagerImpl): TokenManager
}
