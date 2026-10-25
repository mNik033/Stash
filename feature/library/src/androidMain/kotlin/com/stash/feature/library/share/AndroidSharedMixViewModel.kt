package com.stash.feature.library.share

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.share.SharedMixRepository
import com.stash.core.media.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidSharedMixViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: SharedMixRepository,
    playerRepository: PlayerRepository,
) : SharedMixViewModel(
    savedStateHandle = savedStateHandle,
    repository = repository,
    playerRepository = playerRepository,
)
