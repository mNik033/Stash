package com.stash.desktop.di

import androidx.lifecycle.SavedStateHandle
import com.stash.core.auth.TokenManager
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.sync.DownloadQueueTrigger
import com.stash.data.download.files.LibrarySizeHolder
import com.stash.core.data.db.dao.ArtistImageDao
import com.stash.core.data.db.dao.ScannedSongDao
import com.stash.core.data.navigation.LibraryDeepLinkController
import com.stash.core.data.prefs.LibraryScopePreference
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.data.share.SharedMixRepository
import com.stash.core.data.share.SharePreference
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
import com.stash.data.download.DownloadProgressTracker
import com.stash.feature.sync.DownloadManagementViewModel
import com.stash.feature.sync.FailedDownloadsViewModel
import com.stash.feature.sync.FailedMatchesViewModel
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
    fun provideDesktopHomeViewModel(
        musicRepository: MusicRepository,
        playerRepository: PlayerRepository,
        streamingPreference: StreamingPreference,
        ytMusicApiClient: YTMusicApiClient,
    ): com.stash.desktop.ui.home.DesktopHomeViewModel = com.stash.desktop.ui.home.DesktopHomeViewModel(
        musicRepository = musicRepository,
        playerRepository = playerRepository,
        streamingPreference = streamingPreference,
        ytMusicApiClient = ytMusicApiClient,
    )

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
    fun provideSyncViewModel(
        tokenManager: TokenManager,
        streamingPreference: StreamingPreference,
        database: StashDatabase,
        musicRepository: MusicRepository,
        librarySizeHolder: LibrarySizeHolder,
        downloadQueueTrigger: DownloadQueueTrigger,
        blocklistGuard: BlocklistGuard,
        syncStateManager: com.stash.core.data.sync.SyncStateManager,
    ): SyncViewModel = SyncViewModel(
        tokenManager = tokenManager,
        streamingPreference = streamingPreference,
        playlistDao = database.playlistDao(),
        downloadQueueDao = database.downloadQueueDao(),
        syncHistoryDao = database.syncHistoryDao(),
        syncUndoDao = database.syncUndoDao(),
        musicRepository = musicRepository,
        librarySizeHolder = librarySizeHolder,
        downloadQueueTrigger = downloadQueueTrigger,
        blocklistGuard = blocklistGuard,
        syncStateManager = syncStateManager,
    )

    @Provides
    @Singleton
    fun provideDownloadManagementViewModel(
        database: StashDatabase,
        progressTracker: DownloadProgressTracker,
        trigger: DownloadQueueTrigger,
    ): DownloadManagementViewModel = DownloadManagementViewModel(
        downloadQueueDao = database.downloadQueueDao(),
        downloadProgressTracker = progressTracker,
        downloadQueueTrigger = trigger,
    )

    @Provides
    @Singleton
    fun provideFailedDownloadsViewModel(
        database: StashDatabase,
        trigger: DownloadQueueTrigger,
        blocklistGuard: BlocklistGuard,
    ): FailedDownloadsViewModel = FailedDownloadsViewModel(
        downloadQueueDao = database.downloadQueueDao(),
        trackDao = database.trackDao(),
        downloadQueueTrigger = trigger,
        blocklistGuard = blocklistGuard,
    )

    @Provides
    @Singleton
    fun provideFailedMatchesViewModel(
        musicRepository: MusicRepository,
        database: StashDatabase,
    ): FailedMatchesViewModel = FailedMatchesViewModel(
        musicRepository = musicRepository,
        trackDao = database.trackDao(),
    )

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

    @Provides
    @Singleton
    fun providePlaylistDetailViewModelFactory(
        musicRepository: MusicRepository,
        playerRepository: PlayerRepository,
        playlistImageHelper: PlaylistImageHelper,
        streamingPreference: StreamingPreference,
        connectivityMonitor: com.stash.core.media.streaming.ConnectivityMonitor,
        recipeDao: com.stash.core.data.db.dao.StashMixRecipeDao,
        discoveryQueueDao: com.stash.core.data.db.dao.DiscoveryQueueDao,
        sharedMixRepository: SharedMixRepository,
        downloadQueueDao: com.stash.core.data.db.dao.DownloadQueueDao,
        orderDao: com.stash.core.data.db.dao.PlaylistOrderDao,
    ): DesktopPlaylistDetailViewModelFactory = DesktopPlaylistDetailViewModelFactory(
        musicRepository = musicRepository,
        playerRepository = playerRepository,
        playlistImageHelper = playlistImageHelper,
        streamingPreference = streamingPreference,
        connectivityMonitor = connectivityMonitor,
        recipeDao = recipeDao,
        discoveryQueueDao = discoveryQueueDao,
        sharedMixRepository = sharedMixRepository,
        downloadQueueDao = downloadQueueDao,
        orderDao = orderDao,
    )

    @Provides
    @Singleton
    fun provideAlbumDetailViewModelFactory(
        musicRepository: MusicRepository,
        playerRepository: PlayerRepository,
        connectivityMonitor: com.stash.core.media.streaming.ConnectivityMonitor,
    ): DesktopAlbumDetailViewModelFactory = DesktopAlbumDetailViewModelFactory(
        musicRepository = musicRepository,
        playerRepository = playerRepository,
        connectivityMonitor = connectivityMonitor,
    )

    @Provides
    @Singleton
    fun provideArtistDetailViewModelFactory(
        musicRepository: MusicRepository,
        playerRepository: PlayerRepository,
        artistImageDao: ArtistImageDao,
        connectivityMonitor: com.stash.core.media.streaming.ConnectivityMonitor,
    ): DesktopArtistDetailViewModelFactory = DesktopArtistDetailViewModelFactory(
        musicRepository = musicRepository,
        playerRepository = playerRepository,
        artistImageDao = artistImageDao,
        connectivityMonitor = connectivityMonitor,
    )

    @Provides
    @Singleton
    fun provideShareMixViewModelFactory(
        repository: SharedMixRepository,
        sharePreference: SharePreference,
    ): DesktopShareMixViewModelFactory = DesktopShareMixViewModelFactory(
        repository = repository,
        sharePreference = sharePreference,
    )
}

class DesktopShareMixViewModelFactory(
    private val repository: SharedMixRepository,
    private val sharePreference: SharePreference,
) {
    fun create(playlistId: Long): com.stash.feature.library.share.ShareMixViewModel {
        return com.stash.feature.library.share.ShareMixViewModel(repository, sharePreference)
    }
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

class DesktopPlaylistDetailViewModelFactory(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
    private val playlistImageHelper: PlaylistImageHelper,
    private val streamingPreference: StreamingPreference,
    private val connectivityMonitor: com.stash.core.media.streaming.ConnectivityMonitor,
    private val recipeDao: com.stash.core.data.db.dao.StashMixRecipeDao,
    private val discoveryQueueDao: com.stash.core.data.db.dao.DiscoveryQueueDao,
    private val sharedMixRepository: SharedMixRepository,
    private val downloadQueueDao: com.stash.core.data.db.dao.DownloadQueueDao,
    private val orderDao: com.stash.core.data.db.dao.PlaylistOrderDao,
) {
    fun create(playlistId: Long): com.stash.feature.library.PlaylistDetailViewModel {
        val handle = SavedStateHandle(mapOf("playlistId" to playlistId))
        return com.stash.feature.library.PlaylistDetailViewModel(
            savedStateHandle = handle,
            musicRepository = musicRepository,
            playerRepository = playerRepository,
            playlistImageHelper = playlistImageHelper,
            streamingPreference = streamingPreference,
            connectivityMonitor = connectivityMonitor,
            recipeDao = recipeDao,
            discoveryQueueDao = discoveryQueueDao,
            sharedMixRepository = sharedMixRepository,
            downloadQueueDao = downloadQueueDao,
            orderDao = orderDao,
        )
    }
}

class DesktopAlbumDetailViewModelFactory(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
    private val connectivityMonitor: com.stash.core.media.streaming.ConnectivityMonitor,
) {
    fun create(albumName: String, artistName: String): com.stash.feature.library.AlbumDetailViewModel {
        val handle = SavedStateHandle(mapOf("albumName" to albumName, "artistName" to artistName))
        return com.stash.feature.library.AlbumDetailViewModel(
            savedStateHandle = handle,
            musicRepository = musicRepository,
            playerRepository = playerRepository,
            connectivityMonitor = connectivityMonitor,
        )
    }
}

class DesktopArtistDetailViewModelFactory(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
    private val artistImageDao: ArtistImageDao,
    private val connectivityMonitor: com.stash.core.media.streaming.ConnectivityMonitor,
) {
    fun create(artistName: String): com.stash.feature.library.ArtistDetailViewModel {
        val handle = SavedStateHandle(mapOf("artistName" to artistName))
        return com.stash.feature.library.ArtistDetailViewModel(
            savedStateHandle = handle,
            musicRepository = musicRepository,
            playerRepository = playerRepository,
            artistImageDao = artistImageDao,
            connectivityMonitor = connectivityMonitor,
        )
    }
}

