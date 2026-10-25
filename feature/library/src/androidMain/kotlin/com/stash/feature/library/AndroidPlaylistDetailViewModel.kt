package com.stash.feature.library

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.db.dao.DiscoveryQueueDao
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.StashMixRecipeDao
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.data.share.SharedMixRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.media.streaming.ConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidPlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    musicRepository: MusicRepository,
    playerRepository: PlayerRepository,
    playlistImageHelper: PlaylistImageHelper,
    streamingPreference: StreamingPreference,
    connectivityMonitor: ConnectivityMonitor,
    recipeDao: StashMixRecipeDao,
    discoveryQueueDao: DiscoveryQueueDao,
    sharedMixRepository: SharedMixRepository,
    downloadQueueDao: DownloadQueueDao,
) : PlaylistDetailViewModel(
    savedStateHandle = savedStateHandle,
    musicRepository = musicRepository,
    playerRepository = playerRepository,
    playlistImageHelper = playlistImageHelper,
    streamingPreference = streamingPreference,
    connectivityMonitor = connectivityMonitor,
    recipeDao = recipeDao,
    discoveryQueueDao = discoveryQueueDao,
    sharedMixRepository = sharedMixRepository,
    downloadQueueDao = downloadQueueDao,
)
