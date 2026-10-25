package com.stash.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.prefs.HiResOutputMode
import com.stash.core.data.prefs.HiResOutputPreference
import com.stash.core.media.output.HiResOutputController
import com.stash.core.media.output.SignalVerdict
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the "Hi-Res output" card in Audio & Quality: the mode picker, and a
 * live line saying what the music is going through right now.
 *
 * Its own small ViewModel rather than more of [SettingsViewModel]: it reads
 * playback-side state (the output controller), which nothing else on the
 * screen needs.
 */
@HiltViewModel
class HiResOutputViewModel @Inject constructor(
    private val preference: HiResOutputPreference,
    controller: HiResOutputController,
) : ViewModel() {

    val mode: StateFlow<HiResOutputMode> = preference.mode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HiResOutputMode.STANDARD)

    /** Below Android 14 only Standard exists. */
    val isSupported: Boolean = controller.isSupported

    /** What the current (or last) song went through; null before anything has played. */
    val signalPath: StateFlow<SignalVerdict?> = controller.signalPath

    /** True while bit-perfect output is bypassing the EQ (the Equalizer screen says so). */
    val bitPerfectEngaged: StateFlow<Boolean> = controller.bitPerfectEngaged

    fun setMode(mode: HiResOutputMode) {
        viewModelScope.launch { preference.setMode(mode) }
    }
}
