package com.fb2.obd

import com.fb2.obd.obd.SnapshotFreshness
import com.fb2.obd.obd.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeepSearchHitApplyTest {
    @Test
    fun apply_coolantMafAmbient() {
        val base = VehicleSnapshot.EMPTY
        val cool = DashboardViewModel.applyDeepSearchHit(base, "Coolant 1", "0105", 91.0, 1000L)
        assertEquals(91.0, cool.coolantC!!, 0.01)
        assertEquals(1000L, cool.freshAtMs[SnapshotFreshness.KEY_COOLANT])

        val maf = DashboardViewModel.applyDeepSearchHit(base, "MAF", "0110", 6.2, 2000L)
        assertEquals(6.2, maf.mafGps!!, 0.01)
        assertEquals(2000L, maf.freshAtMs[SnapshotFreshness.KEY_MAF])

        val ambient = DashboardViewModel.applyDeepSearchHit(base, "Ambient", "0146", 27.0, 3000L)
        assertEquals(27.0, ambient.ambientC!!, 0.01)
        assertNotNull(ambient.freshAtMs[SnapshotFreshness.KEY_AMBIENT])
    }
}
