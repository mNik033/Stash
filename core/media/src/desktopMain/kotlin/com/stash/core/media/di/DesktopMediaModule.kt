package com.stash.core.media.di

import com.stash.core.media.DesktopPlayerRepository
import com.stash.core.media.PlayerRepository
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
interface DesktopMediaModule {
    @Binds
    @Singleton
    fun bindPlayerRepository(impl: DesktopPlayerRepository): PlayerRepository
}
