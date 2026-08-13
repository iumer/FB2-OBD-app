package com.fb2.obd.data

import android.content.Context
import com.fb2.obd.obd.DashTheme

/** Persists the selected [DashTheme] across process death. */
class DashThemeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): DashTheme =
        DashTheme.fromId(prefs.getString(KEY_THEME, DashTheme.DEFAULT.id))

    fun save(theme: DashTheme) {
        prefs.edit().putString(KEY_THEME, theme.id).apply()
    }

    companion object {
        private const val PREFS = "fb2_dash_theme"
        private const val KEY_THEME = "theme_id"
    }
}
