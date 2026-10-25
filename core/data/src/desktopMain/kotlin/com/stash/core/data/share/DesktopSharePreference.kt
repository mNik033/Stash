package com.stash.core.data.share

class DesktopSharePreference : SharePreference {
    private var name: String? = null
    override suspend fun displayName(): String? = name
    override suspend fun setDisplayName(name: String?) {
        this.name = name
    }
}
