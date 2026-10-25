package com.stash.feature.search

import androidx.lifecycle.SavedStateHandle
import com.stash.core.data.cache.AlbumCache
import com.stash.core.data.cache.ArtistCache
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.media.actions.TrackActionsDelegate
import com.stash.core.media.preview.LosslessUrlPrefetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidArtistProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    artistCache: ArtistCache,
    albumCache: AlbumCache,
    prefetcher: PreviewPrefetcher,
    playerRepository: PlayerRepository,
    musicRepository: MusicRepository,
    streamingPreference: StreamingPreference,
    delegate: TrackActionsDelegate,
    losslessPrefetcher: LosslessUrlPrefetcher,
) : ArtistProfileViewModel(
    savedStateHandle = savedStateHandle,
    artistCache = artistCache,
    albumCache = albumCache,
    prefetcher = prefetcher,
    playerRepository = playerRepository,
    musicRepository = musicRepository,
    streamingPreference = streamingPreference,
    delegate = delegate,
    losslessPrefetcher = losslessPrefetcher,
)
