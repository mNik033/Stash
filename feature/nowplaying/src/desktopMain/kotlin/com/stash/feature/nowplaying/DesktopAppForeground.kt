package com.stash.feature.nowplaying

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopAppForeground @Inject constructor() : AppForeground {
    override val foreground: Flow<Boolean> = flowOf(true)
}
