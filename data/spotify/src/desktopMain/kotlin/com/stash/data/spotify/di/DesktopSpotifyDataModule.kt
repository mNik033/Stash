package com.stash.data.spotify.di

import com.stash.core.auth.TokenManager
import com.stash.core.auth.spotify.SpotifyAuthManager
import com.stash.data.spotify.SpotifyApiClient
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
object DesktopSpotifyDataModule {

    @Provides
    @Singleton
    fun provideSpotifyApiClient(
        okHttpClient: OkHttpClient,
        tokenManager: TokenManager,
        spotifyAuthManager: SpotifyAuthManager,
    ): SpotifyApiClient = SpotifyApiClient(okHttpClient, tokenManager, spotifyAuthManager)
}
