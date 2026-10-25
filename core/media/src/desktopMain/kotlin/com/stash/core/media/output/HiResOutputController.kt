package com.stash.core.media.output

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
actual class HiResOutputController @Inject constructor() {
    private val _signalPath = MutableStateFlow<SignalVerdict?>(null)
    actual val signalPath: StateFlow<SignalVerdict?> = _signalPath.asStateFlow()
}
