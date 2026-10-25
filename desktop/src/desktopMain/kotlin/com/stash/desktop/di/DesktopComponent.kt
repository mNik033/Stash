package com.stash.desktop.di

import com.stash.core.auth.TokenManager
import com.stash.core.common.platform.AppPaths
import com.stash.core.common.platform.Connectivity
import com.stash.core.common.platform.PlatformInfo
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.di.DesktopDatabaseModule
import com.stash.core.data.repository.MusicRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.media.di.DesktopMediaModule
import com.stash.core.media.engine.PlaybackEngine
import com.stash.core.network.di.DesktopNetworkModule
import com.stash.data.download.di.DesktopDownloadModule
import com.stash.data.lyrics.di.DesktopLyricsModule
import com.stash.data.spotify.di.DesktopSpotifyDataModule
import com.stash.data.ytmusic.di.DesktopYTMusicDataModule
import com.stash.desktop.platform.MpvPlaybackEngine
import com.stash.feature.library.DesktopLibraryModule
import com.stash.feature.library.LibraryViewModel
import com.stash.feature.library.allsongs.AllSongsViewModel
import com.stash.feature.search.DesktopSearchModule
import com.stash.feature.search.SearchViewModel
import com.stash.feature.sync.SyncViewModel
import dagger.Component
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        DesktopPlatformModule::class,
        DesktopDatabaseModule::class,
        DesktopNetworkModule::class,
        DesktopMediaModule::class,
        DesktopDownloadModule::class,
        DesktopLyricsModule::class,
        DesktopSpotifyDataModule::class,
        DesktopYTMusicDataModule::class,
        DesktopLibraryModule::class,
        DesktopSearchModule::class,
        DesktopViewModelModule::class,
    ]
)
interface DesktopComponent {
    fun appPaths(): AppPaths
    fun connectivity(): Connectivity
    fun platformInfo(): PlatformInfo
    fun database(): StashDatabase
    fun musicRepository(): MusicRepository
    fun playerRepository(): PlayerRepository
    fun playbackEngine(): PlaybackEngine
    fun mpvPlaybackEngine(): MpvPlaybackEngine
    fun tokenManager(): TokenManager
    fun okHttpClient(): OkHttpClient

    fun libraryViewModel(): LibraryViewModel
    fun allSongsViewModel(): AllSongsViewModel
    fun searchViewModel(): SearchViewModel
    fun syncViewModel(): SyncViewModel
    fun artistProfileViewModelFactory(): DesktopArtistProfileViewModelFactory
    fun albumDiscoveryViewModelFactory(): DesktopAlbumDiscoveryViewModelFactory
    fun lyricsRepository(): com.stash.data.lyrics.LyricsRepository

    @Component.Factory
    interface Factory {
        fun create(): DesktopComponent
    }
}
