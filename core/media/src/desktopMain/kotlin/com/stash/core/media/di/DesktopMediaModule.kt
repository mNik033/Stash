package com.stash.core.media.di

import com.stash.core.media.DesktopPlayerRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.media.SleepTimerController
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
interface DesktopMediaModule {
    @Binds
    @Singleton
    fun bindPlayerRepository(impl: DesktopPlayerRepository): PlayerRepository

    @Binds
    @Singleton
    fun bindConnectivityMonitor(impl: com.stash.core.media.streaming.DesktopConnectivityMonitor): com.stash.core.media.streaming.ConnectivityMonitor

    companion object {
        @Provides
        @Singleton
        fun provideSleepTimerController(playerRepository: PlayerRepository): SleepTimerController =
            SleepTimerController(playerRepository)

        @Provides
        @Singleton
        fun provideCastDevices(): com.stash.core.media.cast.CastDevices =
            com.stash.core.media.cast.DesktopCastDevices

        @Provides
        @Singleton
        fun provideListenTogetherController(): com.stash.core.media.listen.ListenTogetherController =
            com.stash.core.media.listen.DesktopListenTogetherController()
    }
}
