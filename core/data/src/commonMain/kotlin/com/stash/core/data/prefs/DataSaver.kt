package com.stash.core.data.prefs

/**
 * Data saver (Settings › Audio & Quality): on a metered network (mobile data, a metered hotspot)
 * Stash streams smaller, loads smaller pictures, prefetches nothing and leaves background art and
 * metadata work for Wi-Fi. Downloads are never affected (a download is the user's own choice), and
 * on unmetered Wi-Fi nothing changes.
 */
expect open class DataSaver {
    constructor(switchOn: () -> Boolean, metered: () -> Boolean)

    val isOn: Boolean
    fun appliesNow(): Boolean

    companion object {
        val Off: DataSaver
    }
}

