package com.stash.feature.library

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.repository.MusicRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.media.streaming.ConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidAlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    musicRepository: MusicRepository,
    playerRepository: PlayerRepository,
    connectivityMonitor: ConnectivityMonitor,
) : AlbumDetailViewModel(
    savedStateHandle = savedStateHandle,
    musicRepository = musicRepository,
    playerRepository = playerRepository,
    connectivityMonitor = connectivityMonitor,
)
