package com.fb2.obd

import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.obd.isEffectivelyBlank
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSnapshotBlankTest {

    @Test
    fun atrvOnly_isEffectivelyBlank_soSoftRecoverCannotWipeDash() {
        val atrvOnly = VehicleSnapshot(batteryVolts = 14.1)
        assertTrue(
            "ATRV-only frames must be blank for sticky Dash (soft-recover wipe bug)",
            atrvOnly.isEffectivelyBlank(),
        )
    }

    @Test
    fun heroesPresent_notBlank() {
        assertFalse(VehicleSnapshot(rpm = 900.0).isEffectivelyBlank())
        assertFalse(VehicleSnapshot(coolantC = 88.0).isEffectivelyBlank())
        assertFalse(VehicleSnapshot(mafGps = 4.2).isEffectivelyBlank())
        assertFalse(VehicleSnapshot(speedKmh = 60.0).isEffectivelyBlank())
    }

    @Test
    fun empty_isBlank() {
        assertTrue(VehicleSnapshot.EMPTY.isEffectivelyBlank())
    }
}
