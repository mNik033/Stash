package com.stash.core.data.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopLibraryScopePreference @Inject constructor() : LibraryScopePreference {
    private val _inLibraryOnly = MutableStateFlow(false)

    override val inLibraryOnly: Flow<Boolean> = _inLibraryOnly.asStateFlow()
    override suspend fun current(): Boolean = _inLibraryOnly.value
    override suspend fun setInLibraryOnly(value: Boolean) { _inLibraryOnly.value = value }
}
