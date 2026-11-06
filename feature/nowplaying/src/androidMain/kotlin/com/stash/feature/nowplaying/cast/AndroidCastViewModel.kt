package com.stash.feature.nowplaying.cast

import com.stash.core.media.cast.AndroidBrowserOutputs
import com.stash.core.media.cast.CastDevices
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidCastViewModel @Inject constructor(
    devices: CastDevices,
    browsers: AndroidBrowserOutputs,
) : CastViewModel(devices, browsers)
