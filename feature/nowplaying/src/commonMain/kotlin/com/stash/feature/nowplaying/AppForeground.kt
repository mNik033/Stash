package com.stash.feature.nowplaying

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Whether Stash is in the foreground (any activity started). Now Playing prefetches lyrics only
 * then, so music playing with the screen off doesn't look anything up.
 */
interface AppForeground {
    val foreground: Flow<Boolean>

    /** Never in the foreground: the ViewModel's default, so unit tests opt in to prefetching. */
    object Never : AppForeground {
        override val foreground: Flow<Boolean> = flowOf(false)
    }
}
