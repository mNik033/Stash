package com.stash.data.ytmusic.di

import com.stash.core.auth.TokenManager
import com.stash.core.auth.youtube.YouTubeCookieHelper
import com.stash.data.ytmusic.InnerTubeClient
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.potoken.PoTokenMinter
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
object DesktopYTMusicDataModule {

    @Provides
    @Singleton
    fun provideInnerTubeClient(
        okHttpClient: OkHttpClient,
        tokenManager: TokenManager,
        cookieHelper: YouTubeCookieHelper,
        poTokenMinter: PoTokenMinter,
    ): InnerTubeClient = InnerTubeClient(okHttpClient, tokenManager, cookieHelper, poTokenMinter)

    @Provides
    @Singleton
    fun providePoTokenMinter(): PoTokenMinter = PoTokenMinter.None

    @Provides
    @Singleton
    fun provideYTMusicApiClient(
        innerTubeClient: InnerTubeClient,
    ): YTMusicApiClient = YTMusicApiClient(innerTubeClient)
}
