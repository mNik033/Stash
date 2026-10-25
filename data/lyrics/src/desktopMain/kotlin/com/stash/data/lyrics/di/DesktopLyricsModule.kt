package com.stash.data.lyrics.di

import com.stash.core.common.Clock
import com.stash.core.common.SystemClock
import com.stash.data.lyrics.sidecar.DesktopLyricsSidecarWriter
import com.stash.data.lyrics.sidecar.LyricsSidecarWriter
import com.stash.data.lyrics.source.AppleTtmlLyricsSource
import com.stash.data.lyrics.source.InnerTubeLyricsGateway
import com.stash.data.lyrics.source.InnerTubeLyricsGatewayImpl
import com.stash.data.lyrics.source.IsrcLyricsHostBreaker
import com.stash.data.lyrics.source.IsrcResolver
import com.stash.data.lyrics.source.IsrcTtmlLyricsSource
import com.stash.data.lyrics.source.KugouLyricsSource
import com.stash.data.lyrics.source.LrclibLyricsSource
import com.stash.data.lyrics.source.LyricsSource
import com.stash.data.lyrics.source.YtMusicLyricsSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
abstract class DesktopLyricsModule {

    @Binds
    @Singleton
    abstract fun bindInnerTubeLyricsGateway(
        impl: InnerTubeLyricsGatewayImpl,
    ): InnerTubeLyricsGateway

    @Binds
    @Singleton
    abstract fun bindLyricsSidecarWriter(
        impl: DesktopLyricsSidecarWriter,
    ): LyricsSidecarWriter

    companion object {
        private const val DESKTOP_VERSION_NAME = "0.9.51"

        @Provides
        @Singleton
        fun provideLyricsSources(
            appleTtml: AppleTtmlLyricsSource,
            lrclib: LrclibLyricsSource,
            kugou: KugouLyricsSource,
            ytmusic: YtMusicLyricsSource,
            okHttpClient: OkHttpClient,
            isrcResolver: IsrcResolver,
            breaker: IsrcLyricsHostBreaker,
        ): List<@JvmSuppressWildcards LyricsSource> {
            return listOf(
                appleTtml,
                lrclib,
                kugou,
                ytmusic,
                IsrcTtmlLyricsSource(
                    okHttpClient, DESKTOP_VERSION_NAME, IsrcTtmlLyricsSource.LRC_RED_ID,
                    "lrc.red (word-synced)", IsrcTtmlLyricsSource.LRC_RED_URL, isrcResolver, breaker,
                ),
                IsrcTtmlLyricsSource(
                    okHttpClient, DESKTOP_VERSION_NAME, IsrcTtmlLyricsSource.BINIMUM_ID,
                    "Binimum (word-synced)", IsrcTtmlLyricsSource.BINIMUM_URL, isrcResolver, breaker,
                ),
            )
        }

        @Provides
        @Singleton
        fun provideIsrcResolver(
            okHttpClient: OkHttpClient,
            breaker: IsrcLyricsHostBreaker,
        ): IsrcResolver = IsrcResolver(okHttpClient, DESKTOP_VERSION_NAME, breaker)

        @Provides
        @Singleton
        fun provideAppleTtmlLyricsSource(
            okHttpClient: OkHttpClient,
        ): AppleTtmlLyricsSource {
            return AppleTtmlLyricsSource(okHttpClient, DESKTOP_VERSION_NAME)
        }

        @Provides
        @Singleton
        fun provideKugouLyricsSource(okHttpClient: OkHttpClient): KugouLyricsSource = KugouLyricsSource(okHttpClient)

        @Provides
        @Singleton
        fun provideClock(): Clock = SystemClock()

        @Provides
        @Singleton
        @LrclibBaseUrl
        fun provideLrclibBaseUrl(): String = LrclibLyricsSource.DEFAULT_BASE_URL
    }
}
