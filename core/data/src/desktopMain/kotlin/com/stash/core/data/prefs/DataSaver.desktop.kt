package com.stash.core.data.prefs

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
actual open class DataSaver actual constructor(
    private val switchOn: () -> Boolean,
    private val metered: () -> Boolean,
) {
    @Inject
    constructor() : this(switchOn = { false }, metered = { false })

    actual val isOn: Boolean get() = switchOn()

    actual fun appliesNow(): Boolean = switchOn() && metered()

    actual companion object {
        actual val Off: DataSaver = DataSaver(switchOn = { false }, metered = { false })
    }
}

