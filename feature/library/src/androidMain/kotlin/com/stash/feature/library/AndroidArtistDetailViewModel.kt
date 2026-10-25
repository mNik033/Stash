package com.stash.feature.library

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.db.dao.ArtistImageDao
import com.stash.core.data.repository.MusicRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.media.streaming.ConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    musicRepository: MusicRepository,
    playerRepository: PlayerRepository,
    artistImageDao: ArtistImageDao,
    connectivityMonitor: ConnectivityMonitor,
) : ArtistDetailViewModel(
    savedStateHandle = savedStateHandle,
    musicRepository = musicRepository,
    playerRepository = playerRepository,
    artistImageDao = artistImageDao,
    connectivityMonitor = connectivityMonitor,
)
