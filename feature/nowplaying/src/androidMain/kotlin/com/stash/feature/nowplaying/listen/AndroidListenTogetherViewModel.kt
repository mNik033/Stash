package com.stash.feature.nowplaying.listen

import com.stash.core.data.share.SharePreference
import com.stash.core.media.PlayerRepository
import com.stash.core.media.listen.ListenTogetherController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidListenTogetherViewModel @Inject constructor(
    controller: ListenTogetherController,
    sharePreference: SharePreference,
    playerRepository: PlayerRepository,
) : ListenTogetherViewModel(
    controller = controller,
    sharePreference = sharePreference,
    playerRepository = playerRepository,
)
