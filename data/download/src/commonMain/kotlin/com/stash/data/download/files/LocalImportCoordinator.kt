package com.stash.data.download.files

import kotlinx.coroutines.flow.StateFlow

/**
 * State of the "Import from device" flow. Observable via
 * [LocalImportCoordinator.state]. Stays on [Done] or [Error] until
 * [LocalImportCoordinator.dismiss] is called.
 */
sealed interface LocalImportState {
    data object Idle : LocalImportState
    data class Running(val current: Int, val total: Int) : LocalImportState
    data class Done(val imported: Int, val failed: Int) : LocalImportState
    data class Error(val message: String) : LocalImportState
}

/**
 * Common interface for importing user-picked audio files into the Stash library.
 */
interface LocalImportCoordinator {
    val state: StateFlow<LocalImportState>

    fun start(uris: List<String>)
    fun cancel()
    fun dismiss()
}
