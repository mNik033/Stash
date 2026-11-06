package com.stash.feature.nowplaying

import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.media.PlayerRepository
import com.stash.data.download.lossless.LosslessAvailability
import com.stash.data.download.lossless.LosslessSourcePreferences
import com.stash.data.download.prefs.StreamingQualityPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidStreamingQualityViewModel @Inject constructor(
    losslessPrefs: LosslessSourcePreferences,
    qualityPrefs: StreamingQualityPreferences,
    losslessAvailability: LosslessAvailability,
    streamingPreference: StreamingPreference,
    playerRepository: PlayerRepository,
    userActionScope: UserActionScope,
) : StreamingQualityViewModel(
    losslessPrefs = losslessPrefs,
    qualityPrefs = qualityPrefs,
    losslessAvailability = losslessAvailability,
    streamingPreference = streamingPreference,
    playerRepository = playerRepository,
    userActionScope = userActionScope,
)

