package com.stash.core.data.prefs

import kotlinx.coroutines.flow.Flow

interface LibraryScopePreference {
    val inLibraryOnly: Flow<Boolean>
    suspend fun current(): Boolean
    suspend fun setInLibraryOnly(value: Boolean)
}
