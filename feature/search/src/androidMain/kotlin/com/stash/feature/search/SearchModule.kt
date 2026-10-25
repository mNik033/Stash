package com.stash.feature.search

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {
    @Binds
    @Singleton
    abstract fun bindRecentSearchesStore(store: AndroidRecentSearchesStore): RecentSearchesStore

    @Binds
    abstract fun bindPreviewPrefetcher(impl: AndroidPreviewPrefetcher): PreviewPrefetcher
}
