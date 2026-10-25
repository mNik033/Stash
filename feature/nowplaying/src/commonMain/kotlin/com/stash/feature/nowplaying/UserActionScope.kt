package com.stash.feature.nowplaying

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where Now Playing runs the writes the user asked for (Find in FLAC, delete/block, like,
 * download, save to playlist, flag), so they finish even when the user leaves the screen.
 *
 * Now Playing's ViewModel belongs to its back-stack entry: swiping the screen down cancelled its
 * viewModelScope, which aborted "Find in FLAC" mid-download (the resolve and download run inline)
 * and could cut a delete between the skip and the delete. No injectable app-wide scope existed
 * (StashApplication's is private), so this provides one, bound like [AppForeground].
 */
interface UserActionScope {
    val scope: CoroutineScope

    /**
     * Unit tests' default: a fresh scope on [Dispatchers.Main], which the tests replace with their
     * test dispatcher, so work still runs under advanceUntilIdle().
     */
    object OnMain : UserActionScope {
        override val scope: CoroutineScope get() = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}

/** One process-lifetime scope (Main, like viewModelScope, so the same code runs unchanged). */
@Singleton
class AppUserActionScope @Inject constructor() : UserActionScope {
    override val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
