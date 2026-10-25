package com.stash.feature.search

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.cache.AlbumCache
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.media.actions.TrackActionsDelegate
import com.stash.core.media.preview.LosslessUrlPrefetcher
import com.stash.data.ytmusic.YTMusicApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidAlbumDiscoveryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    albumCache: AlbumCache,
    ytMusicApiClient: YTMusicApiClient,
    prefetcher: PreviewPrefetcher,
    playerRepository: PlayerRepository,
    musicRepository: MusicRepository,
    streamingPreference: StreamingPreference,
    delegate: TrackActionsDelegate,
    losslessPrefetcher: LosslessUrlPrefetcher,
) : AlbumDiscoveryViewModel(
    savedStateHandle = savedStateHandle,
    albumCache = albumCache,
    ytMusicApiClient = ytMusicApiClient,
    prefetcher = prefetcher,
    playerRepository = playerRepository,
    musicRepository = musicRepository,
    streamingPreference = streamingPreference,
    delegate = delegate,
    losslessPrefetcher = losslessPrefetcher,
)
