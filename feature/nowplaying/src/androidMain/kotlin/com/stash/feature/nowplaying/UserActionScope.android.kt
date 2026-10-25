package com.stash.feature.nowplaying

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UserActionScopeModule {
    @Binds
    abstract fun bindUserActionScope(impl: AppUserActionScope): UserActionScope
}
