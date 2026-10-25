package com.stash.feature.library

import com.stash.core.auth.TokenManager
import com.stash.core.data.db.dao.ArtistImageDao
import com.stash.core.data.navigation.LibraryDeepLinkController
import com.stash.core.data.prefs.LibraryScopePreference
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.data.share.SharedMixRepository
import com.stash.core.data.sync.FlacUpgradeEnqueuer
import com.stash.core.data.weblink.mirror.MirrorEngine
import com.stash.core.media.PlayerRepository
import com.stash.core.media.streaming.ConnectivityMonitor
import com.stash.data.download.files.LocalImportCoordinator
import com.stash.data.ytmusic.YTMusicApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidLibraryViewModel @Inject constructor(
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
    mirror: MirrorEngine? = null,
    connectivityMonitor: ConnectivityMonitor? = null,
    libraryScopePreference: LibraryScopePreference? = null,
) : LibraryViewModel(
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
    mirror = mirror,
    connectivityMonitor = connectivityMonitor,
    libraryScopePreference = libraryScopePreference,
)
