package com.stash.feature.library

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.media.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidLikedSongsDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    musicRepository: MusicRepository,
    playerRepository: PlayerRepository,
    streamingPreference: StreamingPreference,
) : LikedSongsDetailViewModel(
    savedStateHandle = savedStateHandle,
    musicRepository = musicRepository,
    playerRepository = playerRepository,
    streamingPreference = streamingPreference,
)
