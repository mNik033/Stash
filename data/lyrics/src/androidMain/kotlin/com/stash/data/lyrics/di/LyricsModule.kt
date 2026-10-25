package com.stash.data.lyrics.di

import android.content.Context
import com.stash.core.common.Clock
import com.stash.core.common.SystemClock
import com.stash.data.lyrics.sidecar.AndroidLyricsSidecarWriter
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
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient


/**
 * Hilt wiring for the `:data:lyrics` module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LyricsModule {

    @Binds
    @Singleton
    abstract fun bindInnerTubeLyricsGateway(
        impl: InnerTubeLyricsGatewayImpl,
    ): InnerTubeLyricsGateway

    @Binds
    @Singleton
    abstract fun bindLyricsSidecarWriter(
        impl: AndroidLyricsSidecarWriter,
    ): LyricsSidecarWriter

    companion object {

        /**
         * Ordered source chain consumed by `LyricsRepository`. The order
         * defines fallback priority: Apple TTML (word-synced), LRCLIB (synced
         * LRC), KuGou, InnerTube (plain text), then lrc.red and Binimum.
         *
         * lrc.red and Binimum come LAST on purpose: they are on-demand-only
         * extras (see `LyricsSource.onDemandOnly`) and must never delay lyrics
         * an existing source would return. When an earlier source answers
         * without word timing, the repository gives them one background
         * follow-up try instead.
         */
        @Provides
        @Singleton
        fun provideLyricsSources(
            appleTtml: AppleTtmlLyricsSource,
            lrclib: LrclibLyricsSource,
            kugou: KugouLyricsSource,
            ytmusic: YtMusicLyricsSource,
            okHttpClient: OkHttpClient,
            @ApplicationContext context: Context,
            isrcResolver: IsrcResolver,
            breaker: IsrcLyricsHostBreaker,
        ): List<@JvmSuppressWildcards LyricsSource> {
            val versionName = versionName(context)
            return listOf(
                appleTtml,
                lrclib,
                kugou,
                ytmusic,
                IsrcTtmlLyricsSource(
                    okHttpClient, versionName, IsrcTtmlLyricsSource.LRC_RED_ID,
                    "lrc.red (word-synced)", IsrcTtmlLyricsSource.LRC_RED_URL, isrcResolver, breaker,
                ),
                IsrcTtmlLyricsSource(
                    okHttpClient, versionName, IsrcTtmlLyricsSource.BINIMUM_ID,
                    "Binimum (word-synced)", IsrcTtmlLyricsSource.BINIMUM_URL, isrcResolver, breaker,
                ),
            )
        }

        /** title/artist -> ISRC for the lrc.red/Binimum sources; same DNS, User-Agent and breaker as they use. */
        @Provides
        @Singleton
        fun provideIsrcResolver(
            okHttpClient: OkHttpClient,
            @ApplicationContext context: Context,
            breaker: IsrcLyricsHostBreaker,
        ): IsrcResolver = IsrcResolver(okHttpClient, versionName(context), breaker)

        private fun versionName(context: Context): String =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"

        /** Word-synced Apple Music TTML via lyrics.paxsenix.org; first so it wins when it has syllable timing. */
        @Provides
        @Singleton
        fun provideAppleTtmlLyricsSource(
            okHttpClient: OkHttpClient,
            @ApplicationContext context: Context,
        ): AppleTtmlLyricsSource {
            val versionName = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "unknown"
            return AppleTtmlLyricsSource(okHttpClient, versionName)
        }

        /** KuGou: synced LRC for most of what LRCLIB misses; between LRCLIB and the plain-text fallback. */
        @Provides
        @Singleton
        fun provideKugouLyricsSource(okHttpClient: OkHttpClient): KugouLyricsSource = KugouLyricsSource(okHttpClient)

        @Provides
        @Singleton
        fun provideClock(): Clock = SystemClock()

        /**
         * Base URL for the public LRCLIB API. Defined here (rather than
         * baked into [LrclibLyricsSource] as a constructor default) so
         * Hilt can satisfy the SingletonComponent String binding without
         * tripping the "@Inject constructor String" missing-binding
         * error that surfaces when the lyrics graph is materialised by
         * the app's `:app:hiltJavaCompileDebug` task.
         *
         * Tests construct [LrclibLyricsSource] directly with a
         * MockWebServer URL — the constructor default is preserved as a
         * fallback so the test wiring doesn't change.
         */
        @Provides
        @Singleton
        @LrclibBaseUrl
        fun provideLrclibBaseUrl(): String = LrclibLyricsSource.DEFAULT_BASE_URL
    }
}
