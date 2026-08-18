package com.fb2.obd.data

import android.content.Context

/** Persists whether the user wants the MIN floating bubble restored after process death. */
object FloatingDashPrefs {
    private const val PREFS = "floating_dash"
    private const val KEY_ENABLED = "bubble_enabled"

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
}
