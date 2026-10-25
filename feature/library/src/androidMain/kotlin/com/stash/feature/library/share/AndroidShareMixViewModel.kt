package com.stash.feature.library.share

import com.stash.core.data.share.SharePreference
import com.stash.core.data.share.SharedMixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidShareMixViewModel @Inject constructor(
    repository: SharedMixRepository,
    sharePreference: SharePreference,
) : ShareMixViewModel(
    repository = repository,
    sharePreference = sharePreference,
)
