package com.stash.feature.nowplaying

import com.stash.core.data.lossless.LosslessUpgrader
import com.stash.core.data.prefs.LyricsPreference
import com.stash.core.data.prefs.NowPlayingPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.data.social.LikeCoordinator
import com.stash.core.media.PlayerRepository
import com.stash.core.media.SleepTimerController
import com.stash.data.lyrics.LyricsRepository
import com.stash.data.lyrics.sidecar.LyricsSidecarWriter
import com.stash.data.ytmusic.YTMusicApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidNowPlayingViewModel @Inject constructor(
    playerRepository: PlayerRepository,
    sleepTimerController: SleepTimerController,
    musicRepository: MusicRepository,
    likeCoordinator: LikeCoordinator,
    losslessUpgrader: LosslessUpgrader,
    lyricsRepository: LyricsRepository,
    lyricsPreference: LyricsPreference,
    nowPlayingPreference: NowPlayingPreference,
    lyricsSidecarWriter: LyricsSidecarWriter,
    ytMusicApiClient: YTMusicApiClient,
    appForeground: AppForeground,
    userActionScope: UserActionScope,
    lyricsOutputOffsets: LyricsOutputOffsets,
) : NowPlayingViewModel(
    playerRepository = playerRepository,
    sleepTimerController = sleepTimerController,
    musicRepository = musicRepository,
    likeCoordinator = likeCoordinator,
    losslessUpgrader = losslessUpgrader,
    lyricsRepository = lyricsRepository,
    lyricsPreference = lyricsPreference,
    nowPlayingPreference = nowPlayingPreference,
    lyricsSidecarWriter = lyricsSidecarWriter,
    ytMusicApiClient = ytMusicApiClient,
    appForeground = appForeground,
    userActionScope = userActionScope,
    lyricsOutputOffsets = lyricsOutputOffsets,
)
