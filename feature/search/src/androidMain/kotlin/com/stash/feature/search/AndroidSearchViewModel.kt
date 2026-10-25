package com.stash.feature.search

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.media.PlayerRepository
import com.stash.core.media.actions.TrackActionsDelegate
import com.stash.core.media.preview.LosslessUrlPrefetcher
import com.stash.data.ytmusic.YTMusicApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidSearchViewModel @Inject constructor(
    api: YTMusicApiClient,
    prefetcher: PreviewPrefetcher,
    delegate: TrackActionsDelegate,
    losslessPrefetcher: LosslessUrlPrefetcher,
    playerRepository: PlayerRepository,
    streamingPreference: StreamingPreference,
    recentSearchesStore: RecentSearchesStore,
    savedStateHandle: SavedStateHandle,
) : SearchViewModel(
    api = api,
    prefetcher = prefetcher,
    delegate = delegate,
    losslessPrefetcher = losslessPrefetcher,
    playerRepository = playerRepository,
    streamingPreference = streamingPreference,
    recentSearchesStore = recentSearchesStore,
    savedStateHandle = savedStateHandle,
)
