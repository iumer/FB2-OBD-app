package com.fb2.obd.data

import android.content.Context
import com.fb2.obd.obd.DashTheme

/** Persists Dash theme + estimated-gear + demo-allow toggles across process death. */
class DashThemeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): DashTheme =
        DashTheme.fromId(prefs.getString(KEY_THEME, DashTheme.DEFAULT.id))

    fun save(theme: DashTheme) {
        prefs.edit().putString(KEY_THEME, theme.id).apply()
    }

    /** User override when present; otherwise [profileDefault]. */
    fun loadShowEstimatedGear(profileDefault: Boolean): Boolean =
        if (prefs.contains(KEY_SHOW_EST_GEAR)) {
            prefs.getBoolean(KEY_SHOW_EST_GEAR, profileDefault)
        } else {
            profileDefault
        }

    fun saveShowEstimatedGear(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_EST_GEAR, enabled).apply()
    }

    /**
     * When true (default), the app may run [com.fb2.obd.data.DemoObdSource] with no ELM.
     * When false, Dash stays disconnected / `--` until a real adapter links or Demo is
     * turned back on (Settings or the Connect sheet).
     */
    fun loadAllowDemo(default: Boolean = true): Boolean =
        prefs.getBoolean(KEY_ALLOW_DEMO, default)

    fun saveAllowDemo(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_DEMO, enabled).apply()
    }

    companion object {
        private const val PREFS = "fb2_dash_theme"
        private const val KEY_THEME = "theme_id"
        private const val KEY_SHOW_EST_GEAR = "show_estimated_gear"
        private const val KEY_ALLOW_DEMO = "allow_demo"
    }
}
