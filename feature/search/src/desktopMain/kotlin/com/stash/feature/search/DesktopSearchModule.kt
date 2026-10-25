package com.stash.feature.search

import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
abstract class DesktopSearchModule {
    @Binds
    @Singleton
    abstract fun bindRecentSearchesStore(store: DesktopRecentSearchesStore): RecentSearchesStore

    @Binds
    @Singleton
    abstract fun bindPreviewPrefetcher(impl: DesktopPreviewPrefetcher): PreviewPrefetcher
}
