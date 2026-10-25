package com.stash.core.media.di

import com.stash.core.media.actions.DesktopTrackActionsDelegate
import com.stash.core.media.actions.TrackActionsDelegate
import com.stash.core.media.preview.DesktopLosslessUrlPrefetcher
import com.stash.core.media.preview.LosslessUrlPrefetcher
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
abstract class DesktopMediaModule {
    @Binds
    @Singleton
    abstract fun bindTrackActionsDelegate(impl: DesktopTrackActionsDelegate): TrackActionsDelegate

    @Binds
    @Singleton
    abstract fun bindLosslessUrlPrefetcher(impl: DesktopLosslessUrlPrefetcher): LosslessUrlPrefetcher
}
