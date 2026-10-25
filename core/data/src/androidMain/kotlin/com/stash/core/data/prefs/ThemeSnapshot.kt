package com.stash.core.data.prefs

import android.content.Context
import com.stash.core.model.UiScale

/**
 * The last theme and UI scale the app was laid out with, in plain SharedPreferences so launch can
 * read them synchronously. The DataStore in [ThemePreferencesManager] stays the source of truth;
 * this is a mirror that `MainActivity` keeps in step with it.
 *
 * The UI scale needs it most: it is applied in `attachBaseContext`, before the first frame and
 * before Hilt has injected anything, where waiting on a DataStore read isn't possible.
 */
object ThemeSnapshot {
    /** The SharedPreferences file. `MainActivity` keeps its theme-mode and AMOLED keys here too. */
    const val PREFS_NAME = "theme_snapshot"

    private const val KEY_UI_SCALE = "ui_scale"

    /** The UI scale chosen last, as the user chose it (before any per-window cap), snapped to the grid. */
    fun readUiScale(context: Context): Float =
        UiScale.snap(prefs(context).getFloat(KEY_UI_SCALE, UiScale.DEFAULT))

    /**
     * Records the chosen UI scale for the next activity to apply. `apply()`, not `commit()`: the
     * in-memory copy every reader in this process shares is updated before this returns, so an
     * activity recreated right after still reads the new value; only the disk write is deferred.
     */
    fun writeUiScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_UI_SCALE, UiScale.snap(scale)).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
