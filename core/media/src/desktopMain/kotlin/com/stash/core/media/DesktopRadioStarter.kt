package com.stash.core.media

import com.stash.core.data.radio.RadioSeed
import com.stash.core.model.RadioStartResult
import com.stash.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopRadioStarter @Inject constructor(
    private val playerRepository: PlayerRepository,
) : RadioStarter {

    private val _tuning = MutableStateFlow(false)
    override val tuning: StateFlow<Boolean> = _tuning.asStateFlow()

    override suspend fun fromSong(track: Track): String? =
        start(RadioSeed.Song(title = track.title, artist = track.artist, ytVideoId = track.youtubeId))

    override suspend fun fromArtist(artistName: String): String? {
        val name = artistName.trim()
        if (name.isEmpty()) return null
        return start(RadioSeed.Artist(name))
    }

    private suspend fun start(seed: RadioSeed): String? {
        if (!_tuning.compareAndSet(expect = false, update = true)) return null
        return try {
            when (playerRepository.startRadio(seed)) {
                RadioStartResult.Started -> null
                RadioStartResult.PlayerNotReady -> "Player not ready"
                RadioStartResult.NoStation -> "Couldn't start radio station"
                RadioStartResult.DownloadMode -> "Can't start radio in Download mode"
            }
        } finally {
            _tuning.value = false
        }
    }
}

