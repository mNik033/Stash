package com.stash.core.media.output

import kotlinx.coroutines.flow.StateFlow

expect class HiResOutputController {
    val signalPath: StateFlow<SignalVerdict?>
}
