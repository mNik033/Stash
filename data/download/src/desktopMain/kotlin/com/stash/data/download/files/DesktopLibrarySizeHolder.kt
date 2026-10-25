package com.stash.data.download.files

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopLibrarySizeHolder @Inject constructor() : LibrarySizeHolder {
    override val size: StateFlow<LibrarySizeBreakdown> =
        MutableStateFlow(LibrarySizeBreakdown(0L, 0L, 0))

    override fun refresh() {}
}
