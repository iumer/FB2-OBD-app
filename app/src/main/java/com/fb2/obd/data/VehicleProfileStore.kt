package com.fb2.obd.data

import android.content.Context
import com.fb2.obd.obd.VehicleProfile

/**
 * Persists the user-selected [VehicleProfile] across process death.
 * SharedPreferences — same pattern as log-upload / AI key stores.
 */
class VehicleProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): VehicleProfile =
        VehicleProfile.fromId(prefs.getString(KEY_PROFILE, VehicleProfile.DEFAULT.id))

    fun save(profile: VehicleProfile) {
        prefs.edit().putString(KEY_PROFILE, profile.id).apply()
    }

    companion object {
        private const val PREFS = "fb2_vehicle_profile"
        private const val KEY_PROFILE = "profile_id"
    }
}
