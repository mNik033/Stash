package com.stash.feature.nowplaying.ui

import com.stash.core.media.output.HiResOutputController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidSignalPathViewModel @Inject constructor(
    controller: HiResOutputController,
) : SignalPathViewModel(controller)
