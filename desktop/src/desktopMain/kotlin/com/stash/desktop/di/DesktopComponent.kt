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
import com.stash.data.download.DesktopDownloadQueueProcessor
import com.stash.data.download.di.DesktopDownloadModule
import com.stash.data.lyrics.di.DesktopLyricsModule
import com.stash.data.spotify.di.DesktopSpotifyDataModule
import com.stash.data.ytmusic.di.DesktopYTMusicDataModule
import com.stash.desktop.platform.MacOsMediaControls
import com.stash.desktop.platform.MpvPlaybackEngine
import com.stash.feature.home.DesktopHomeModule
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
        DesktopHomeModule::class,
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
    fun macOsMediaControls(): MacOsMediaControls
    fun tokenManager(): TokenManager
    fun okHttpClient(): OkHttpClient

    fun homeViewModel(): com.stash.feature.home.HomeViewModel
    fun homeShelvesViewModel(): com.stash.feature.home.shelves.HomeShelvesViewModel
    fun desktopHomeViewModel(): com.stash.desktop.ui.home.DesktopHomeViewModel
    fun libraryViewModel(): LibraryViewModel
    fun allSongsViewModel(): AllSongsViewModel
    fun searchViewModel(): SearchViewModel
    fun syncViewModel(): SyncViewModel
    fun downloadManagementViewModel(): com.stash.feature.sync.DownloadManagementViewModel
    fun failedDownloadsViewModel(): com.stash.feature.sync.FailedDownloadsViewModel
    fun failedMatchesViewModel(): com.stash.feature.sync.FailedMatchesViewModel
    fun artistProfileViewModelFactory(): DesktopArtistProfileViewModelFactory
    fun albumDiscoveryViewModelFactory(): DesktopAlbumDiscoveryViewModelFactory
    fun playlistDetailViewModelFactory(): DesktopPlaylistDetailViewModelFactory
    fun shareMixViewModelFactory(): DesktopShareMixViewModelFactory
    fun albumDetailViewModelFactory(): DesktopAlbumDetailViewModelFactory
    fun artistDetailViewModelFactory(): DesktopArtistDetailViewModelFactory
    fun lyricsRepository(): com.stash.data.lyrics.LyricsRepository
    fun likeCoordinator(): com.stash.core.data.social.LikeCoordinator
    fun downloadQueueProcessor(): DesktopDownloadQueueProcessor
    fun streamingPreference(): com.stash.core.data.prefs.StreamingPreference
    fun qualityPreference(): com.stash.core.data.prefs.QualityPreference
    fun qbdlxCredentialStore(): com.stash.data.download.lossless.qbdlx.QbdlxCredentialStore
    fun qobuzAccountConnector(): com.stash.data.download.lossless.qbdlx.QobuzAccountConnector
    fun losslessSourcePreferences(): com.stash.data.download.lossless.LosslessSourcePreferences
    fun losslessConfigFetcher(): com.stash.data.download.lossless.relay.LosslessConfigFetcher
    fun losslessAvailability(): com.stash.data.download.lossless.LosslessAvailability
    fun homeDiscoveryRepository(): com.stash.core.data.discovery.HomeDiscoveryRepository
    fun mixWorkerScheduler(): com.stash.feature.library.mixbuilder.MixWorkerScheduler

    fun playlistBrowseViewModel(genre: String): com.stash.feature.home.PlaylistBrowseViewModel =
        com.stash.feature.home.PlaylistBrowseViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(mapOf("genre" to genre)),
            repository = homeDiscoveryRepository(),
        )

    fun mixBuilderViewModel(recipeId: Long?): com.stash.feature.library.mixbuilder.MixBuilderViewModel =
        com.stash.feature.library.mixbuilder.MixBuilderViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(if (recipeId != null) mapOf("recipeId" to recipeId) else emptyMap()),
            recipeDao = database().stashMixRecipeDao(),
            musicRepository = musicRepository(),
            mixWorkerScheduler = mixWorkerScheduler(),
        )

    @Component.Factory
    interface Factory {
        fun create(): DesktopComponent
    }
}
