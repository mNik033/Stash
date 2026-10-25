package com.stash.desktop.di

import androidx.lifecycle.SavedStateHandle
import com.stash.core.auth.TokenManager
import com.stash.core.data.db.dao.ArtistImageDao
import com.stash.core.data.db.dao.ScannedSongDao
import com.stash.core.data.navigation.LibraryDeepLinkController
import com.stash.core.data.prefs.LibraryScopePreference
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.data.share.SharedMixRepository
import com.stash.core.data.sync.FlacUpgradeEnqueuer
import com.stash.core.media.PlayerRepository
import com.stash.core.media.actions.TrackActionsDelegate
import com.stash.core.media.preview.LosslessUrlPrefetcher
import com.stash.data.download.files.LocalImportCoordinator
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.feature.library.LibraryPreferencesStore
import com.stash.feature.library.LibraryViewModel
import com.stash.feature.library.PlaylistImageHelper
import com.stash.feature.library.allsongs.AllSongsScanner
import com.stash.feature.library.allsongs.AllSongsViewModel
import com.stash.feature.library.allsongs.ScannedCoverStore
import com.stash.feature.search.PreviewPrefetcher
import com.stash.feature.search.RecentSearchesStore
import com.stash.feature.search.SearchViewModel
import com.stash.feature.sync.SyncViewModel
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object DesktopViewModelModule {

    @Provides
    @Singleton
    fun provideSavedStateHandle(): SavedStateHandle = SavedStateHandle()

    @Provides
    @Singleton
    fun provideLibraryViewModel(
        musicRepository: MusicRepository,
        playerRepository: PlayerRepository,
        tokenManager: TokenManager,
        playlistImageHelper: PlaylistImageHelper,
        localImportCoordinator: LocalImportCoordinator,
        streamingPreference: StreamingPreference,
        ytMusicApiClient: YTMusicApiClient,
        flacUpgradeEnqueuer: FlacUpgradeEnqueuer,
        libraryPreferencesStore: LibraryPreferencesStore,
        libraryDeepLinkController: LibraryDeepLinkController,
        artistImageDao: ArtistImageDao,
        sharedMixRepository: SharedMixRepository,
        libraryScopePreference: LibraryScopePreference,
    ): LibraryViewModel = LibraryViewModel(
        musicRepository = musicRepository,
        playerRepository = playerRepository,
        tokenManager = tokenManager,
        playlistImageHelper = playlistImageHelper,
        localImportCoordinator = localImportCoordinator,
        streamingPreference = streamingPreference,
        ytMusicApiClient = ytMusicApiClient,
        flacUpgradeEnqueuer = flacUpgradeEnqueuer,
        libraryPreferencesStore = libraryPreferencesStore,
        libraryDeepLinkController = libraryDeepLinkController,
        artistImageDao = artistImageDao,
        sharedMixRepository = sharedMixRepository,
        libraryScopePreference = libraryScopePreference,
    )

    @Provides
    @Singleton
    fun provideAllSongsViewModel(
        dao: ScannedSongDao,
        scanner: AllSongsScanner,
        player: PlayerRepository,
        covers: ScannedCoverStore,
    ): AllSongsViewModel = AllSongsViewModel(
        dao = dao,
        scanner = scanner,
        player = player,
        covers = covers,
    )

    @Provides
    @Singleton
    fun provideSearchViewModel(
        api: YTMusicApiClient,
        prefetcher: PreviewPrefetcher,
        delegate: TrackActionsDelegate,
        losslessPrefetcher: LosslessUrlPrefetcher,
        playerRepository: PlayerRepository,
        streamingPreference: StreamingPreference,
        recentSearchesStore: RecentSearchesStore,
        savedStateHandle: SavedStateHandle,
    ): SearchViewModel = SearchViewModel(
        api = api,
        prefetcher = prefetcher,
        delegate = delegate,
        losslessPrefetcher = losslessPrefetcher,
        playerRepository = playerRepository,
        streamingPreference = streamingPreference,
        recentSearchesStore = recentSearchesStore,
        savedStateHandle = savedStateHandle,
    )

    @Provides
    @Singleton
    fun provideSyncViewModel(): SyncViewModel = SyncViewModel()

    @Provides
    @Singleton
    fun provideArtistProfileViewModelFactory(
        artistCache: com.stash.core.data.cache.ArtistCache,
        albumCache: com.stash.core.data.cache.AlbumCache,
        prefetcher: PreviewPrefetcher,
        playerRepository: PlayerRepository,
        musicRepository: MusicRepository,
        streamingPreference: StreamingPreference,
        delegateProvider: javax.inject.Provider<TrackActionsDelegate>,
        losslessPrefetcher: LosslessUrlPrefetcher,
    ): DesktopArtistProfileViewModelFactory = DesktopArtistProfileViewModelFactory(
        artistCache = artistCache,
        albumCache = albumCache,
        prefetcher = prefetcher,
        playerRepository = playerRepository,
        musicRepository = musicRepository,
        streamingPreference = streamingPreference,
        delegateProvider = delegateProvider,
        losslessPrefetcher = losslessPrefetcher,
    )

    @Provides
    @Singleton
    fun provideAlbumDiscoveryViewModelFactory(
        albumCache: com.stash.core.data.cache.AlbumCache,
        ytMusicApiClient: YTMusicApiClient,
        prefetcher: PreviewPrefetcher,
        playerRepository: PlayerRepository,
        musicRepository: MusicRepository,
        streamingPreference: StreamingPreference,
        delegateProvider: javax.inject.Provider<TrackActionsDelegate>,
        losslessPrefetcher: LosslessUrlPrefetcher,
    ): DesktopAlbumDiscoveryViewModelFactory = DesktopAlbumDiscoveryViewModelFactory(
        albumCache = albumCache,
        ytMusicApiClient = ytMusicApiClient,
        prefetcher = prefetcher,
        playerRepository = playerRepository,
        musicRepository = musicRepository,
        streamingPreference = streamingPreference,
        delegateProvider = delegateProvider,
        losslessPrefetcher = losslessPrefetcher,
    )
}

class DesktopArtistProfileViewModelFactory(
    private val artistCache: com.stash.core.data.cache.ArtistCache,
    private val albumCache: com.stash.core.data.cache.AlbumCache,
    private val prefetcher: PreviewPrefetcher,
    private val playerRepository: PlayerRepository,
    private val musicRepository: MusicRepository,
    private val streamingPreference: StreamingPreference,
    private val delegateProvider: javax.inject.Provider<TrackActionsDelegate>,
    private val losslessPrefetcher: LosslessUrlPrefetcher,
) {
    fun create(
        artistId: String,
        name: String = "",
        avatarUrl: String? = null,
        focusAlbum: String? = null,
    ): com.stash.feature.search.ArtistProfileViewModel {
        val map = mutableMapOf<String, Any>("artistId" to artistId, "name" to name)
        if (avatarUrl != null) map["avatarUrl"] = avatarUrl
        if (focusAlbum != null) map["focusAlbum"] = focusAlbum
        return com.stash.feature.search.ArtistProfileViewModel(
            savedStateHandle = SavedStateHandle(map),
            artistCache = artistCache,
            albumCache = albumCache,
            prefetcher = prefetcher,
            playerRepository = playerRepository,
            musicRepository = musicRepository,
            streamingPreference = streamingPreference,
            delegate = delegateProvider.get(),
            losslessPrefetcher = losslessPrefetcher,
        )
    }
}

class DesktopAlbumDiscoveryViewModelFactory(
    private val albumCache: com.stash.core.data.cache.AlbumCache,
    private val ytMusicApiClient: YTMusicApiClient,
    private val prefetcher: PreviewPrefetcher,
    private val playerRepository: PlayerRepository,
    private val musicRepository: MusicRepository,
    private val streamingPreference: StreamingPreference,
    private val delegateProvider: javax.inject.Provider<TrackActionsDelegate>,
    private val losslessPrefetcher: LosslessUrlPrefetcher,
) {
    fun create(
        browseId: String,
        title: String = "",
        artist: String = "",
        thumbnailUrl: String? = null,
        year: String? = null,
        source: com.stash.data.ytmusic.model.AlbumSource = com.stash.data.ytmusic.model.AlbumSource.YOUTUBE,
        qobuzTrackCount: Int? = null,
    ): com.stash.feature.search.AlbumDiscoveryViewModel {
        val map = mutableMapOf<String, Any>(
            "browseId" to browseId,
            "title" to title,
            "artist" to artist,
            "source" to source,
        )
        if (thumbnailUrl != null) map["thumbnailUrl"] = thumbnailUrl
        if (year != null) map["year"] = year
        if (qobuzTrackCount != null) map["qobuzTrackCount"] = qobuzTrackCount
        return com.stash.feature.search.AlbumDiscoveryViewModel(
            savedStateHandle = SavedStateHandle(map),
            albumCache = albumCache,
            ytMusicApiClient = ytMusicApiClient,
            prefetcher = prefetcher,
            playerRepository = playerRepository,
            musicRepository = musicRepository,
            streamingPreference = streamingPreference,
            delegate = delegateProvider.get(),
            losslessPrefetcher = losslessPrefetcher,
        )
    }
}

