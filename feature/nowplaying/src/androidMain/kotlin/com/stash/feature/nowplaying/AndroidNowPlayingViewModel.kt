package com.stash.feature.nowplaying

import android.content.Context
import coil3.SingletonImageLoader
import com.stash.core.data.backcover.AlbumBackCovers
import com.stash.core.data.lastfm.TrackAlbums
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
import com.stash.feature.nowplaying.ui.backCoverRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    backCovers: AlbumBackCovers,
    trackAlbums: TrackAlbums,
    @ApplicationContext private val appContext: Context,
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
    backCovers = backCovers,
    trackAlbums = trackAlbums,
) {
    override fun fetchBackImage(url: String) {
        SingletonImageLoader.get(appContext).enqueue(backCoverRequest(appContext, url))
    }
}
