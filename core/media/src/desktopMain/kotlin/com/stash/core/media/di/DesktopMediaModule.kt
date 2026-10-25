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

    companion object {
        @Provides
        @Singleton
        fun provideSleepTimerController(playerRepository: PlayerRepository): SleepTimerController =
            SleepTimerController(playerRepository)
    }
}
