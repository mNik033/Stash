package com.stash.feature.library.share

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.repository.MusicRepository
import com.stash.core.data.share.ShareApiClient
import com.stash.core.data.social.LikeCoordinator
import com.stash.core.media.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidSharedTrackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    musicRepository: MusicRepository,
    playerRepository: PlayerRepository,
    likeCoordinator: LikeCoordinator,
    shareApi: ShareApiClient,
) : SharedTrackViewModel(
    savedStateHandle = savedStateHandle,
    musicRepository = musicRepository,
    playerRepository = playerRepository,
    likeCoordinator = likeCoordinator,
    shareApi = shareApi,
)
