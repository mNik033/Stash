package com.stash.feature.nowplaying.cast

import com.stash.core.media.cast.CastDevices
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidCastViewModel @Inject constructor(
    devices: CastDevices,
) : CastViewModel(devices)
