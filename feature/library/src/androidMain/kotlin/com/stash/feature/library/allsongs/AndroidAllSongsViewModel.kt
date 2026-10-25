package com.stash.feature.library.allsongs

import com.stash.core.data.db.dao.ScannedSongDao
import com.stash.core.media.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidAllSongsViewModel @Inject constructor(
    dao: ScannedSongDao,
    scanner: AllSongsScanner,
    player: PlayerRepository,
    covers: ScannedCoverStore,
) : AllSongsViewModel(
    dao = dao,
    scanner = scanner,
    player = player,
    covers = covers,
)
