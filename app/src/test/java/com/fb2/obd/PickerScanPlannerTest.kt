package com.fb2.obd

import com.fb2.obd.obd.HondaPidCatalog
import com.fb2.obd.obd.PickerScanPlanner
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.VehicleProfile
import com.fb2.obd.obd.VehicleProfileConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerScanPlannerTest {

    @Test
    fun fb2Catalog_scanSkipsMode22AndAlreadyLive() {
        val catalog = VehicleProfileConfig.pidCatalog(VehicleProfile.FB2)
        val live = setOf("010C", "010D", "0105")
        val planned = PickerScanPlanner.requestsToProbe(catalog, live, advertised = setOf(0x0B, 0x10))
        assertTrue(planned.any { it.request.equals("010B", true) })
        assertTrue(planned.any { it.request.equals("0110", true) })
        assertFalse(planned.any { it.request.equals("010C", true) })
        assertFalse("Mode 22 must not run during picker scan", planned.any { it.request.startsWith("22") })
        assertTrue(HondaPidCatalog.allPids.any { it.request.startsWith("22") })
        assertFalse(planned.any { it.request.equals("0100", true) })
    }

    @Test
    fun liveRequest_skipsSiblingIds() {
        val catalog = StandardPidCatalog.all
        val planned = PickerScanPlanner.requestsToProbe(catalog, alreadyLiveIds = setOf("0124"))
        assertFalse(planned.any { it.request.equals("0124", true) })
        assertFalse(planned.any { it.id.equals("0124I", true) })
    }

    @Test
    fun yield_isLongerAfterMiss() {
        assertTrue(PickerScanPlanner.yieldMs(hit = false) > PickerScanPlanner.yieldMs(hit = true))
        assertTrue(PickerScanPlanner.yieldMs(hit = false) >= 700L)
    }

    @Test
    fun advertisedPids_comeFirst() {
        val catalog = StandardPidCatalog.all
        val planned = PickerScanPlanner.requestsToProbe(catalog, emptySet(), advertised = setOf(0x49, 0x4A))
        val idx49 = planned.indexOfFirst { it.request.equals("0149", true) }
        val idx4A = planned.indexOfFirst { it.request.equals("014A", true) }
        val idxUnused = planned.indexOfFirst { it.request.equals("015C", true) }
        assertTrue(idx49 >= 0 && idx4A >= 0)
        assertTrue(idx49 < idxUnused)
        assertTrue(idx4A < idxUnused)
    }
}
