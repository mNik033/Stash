package com.stash.core.media.di

import com.stash.core.media.actions.AndroidTrackActionsDelegate
import com.stash.core.media.actions.TrackActionsDelegate
import com.stash.core.media.preview.AndroidLosslessUrlPrefetcher
import com.stash.core.media.preview.LosslessUrlPrefetcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(ViewModelComponent::class)
abstract class MediaActionViewModelModule {
    @Binds
    @ViewModelScoped
    abstract fun bindTrackActionsDelegate(impl: AndroidTrackActionsDelegate): TrackActionsDelegate
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaActionSingletonModule {
    @Binds
    @Singleton
    abstract fun bindLosslessUrlPrefetcher(impl: AndroidLosslessUrlPrefetcher): LosslessUrlPrefetcher
}
