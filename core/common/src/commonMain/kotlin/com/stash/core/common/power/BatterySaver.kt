package com.stash.core.common.power

import com.stash.core.common.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Android's Battery Saver, as Stash follows it (owner decision D19, 2026-11-01): no Stash switch
 * and no quality cap. While it is on, Stash trims only its OWN in-process work: continuous
 * animations stand still, the cold-start warm-ups are skipped, and speculative preview prefetch
 * stops. Periodic WorkManager jobs are left to Android, which already defers them under Saver.
 *
 * Bound once for the app (`app/.../di/BatterySaverModule`). Read [isOn]`.value` for a decision
 * made now (a cold start, a prefetch); collect it where the answer must follow the switch (the
 * UI's still loops, a refresh that waits for Saver to turn off).
 */
interface BatterySaver {
    /** True while Android's Battery Saver is on. Starts from the system's current state. */
    val isOn: StateFlow<Boolean>

    companion object {
        /** Never on: the default for hand-built classes in tests, previews and CompositionLocals. */
        val Off: BatterySaver = object : BatterySaver {
            override val isOn: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
        }
    }
}

/** Suspends until Battery Saver is off; returns at once when it already is. */
suspend fun BatterySaver.awaitOff() {
    isOn.first { on -> !on }
}

/** A [BatterySaver] set by hand, for tests (and previews): flip [isOn]`.value` to switch it. */
class FakeBatterySaver(on: Boolean = false) : BatterySaver {
    override val isOn: MutableStateFlow<Boolean> = MutableStateFlow(on)
}

/**
 * The system's Battery Saver: [read] re-read on every change notification.
 *
 * Any failure to read reports "off": a phone whose power service misbehaves keeps Stash's normal
 * behaviour rather than silently losing its animations and prefetch.
 *
 * @param read Reads the system's power-save state now. May throw; see [mapPowerSaveState].
 * @param subscribe Registers a callback run on every change; called once, from the constructor.
 */
open class SystemBatterySaver internal constructor(
    private val read: () -> Boolean,
    subscribe: (onChange: () -> Unit) -> Unit,
) : BatterySaver {

    private val state = MutableStateFlow(false)
    override val isOn: StateFlow<Boolean> = state.asStateFlow()

    init {
        // Subscribe first, then read: a change landing in between is then seen by the callback.
        try {
            subscribe { refresh() }
        } catch (e: Exception) {
            // No change events (an OEM refusing the registration): the state read below still holds
            // for this process, which is the decision a cold start needs.
            Log.w(TAG, "Battery Saver changes can't be watched; using the state at start", e)
        }
        refresh()
    }

    private fun refresh() {
        state.value = mapPowerSaveState(read)
    }

    companion object {
        private const val TAG = "BatterySaver"

        /**
         * The state Stash acts on for one read of the system: what [read] says, or off when it
         * throws (a dead binder, a missing service). Off is the safe answer: normal behaviour.
         */
        fun mapPowerSaveState(read: () -> Boolean): Boolean = try {
            read()
        } catch (e: Exception) {
            Log.w(TAG, "Battery Saver state unreadable; treating it as off", e)
            false
        }
    }
}
