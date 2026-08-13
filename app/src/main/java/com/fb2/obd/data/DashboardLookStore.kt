package com.fb2.obd.data

import android.content.Context
import com.fb2.obd.obd.DashboardLook

/** Persists the selected [DashboardLook] across process death. */
class DashboardLookStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): DashboardLook =
        DashboardLook.fromId(prefs.getString(KEY_LOOK, DashboardLook.DEFAULT.id))

    fun save(look: DashboardLook) {
        prefs.edit().putString(KEY_LOOK, look.id).apply()
    }

    companion object {
        private const val PREFS = "fb2_dashboard_look"
        private const val KEY_LOOK = "look_id"
    }
}
