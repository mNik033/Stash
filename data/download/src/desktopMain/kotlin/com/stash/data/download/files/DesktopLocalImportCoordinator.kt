package com.stash.data.download.files

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopLocalImportCoordinator @Inject constructor() : LocalImportCoordinator {
    private val _state = MutableStateFlow<LocalImportState>(LocalImportState.Idle)
    override val state: StateFlow<LocalImportState> = _state.asStateFlow()

    override fun start(uris: List<String>) {
        // Desktop import will be wired in Phase 4
    }

    override fun cancel() {
        _state.value = LocalImportState.Idle
    }

    override fun dismiss() {
        _state.value = LocalImportState.Idle
    }
}
