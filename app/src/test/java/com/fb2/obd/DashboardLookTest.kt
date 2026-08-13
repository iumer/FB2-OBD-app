package com.fb2.obd

import com.fb2.obd.obd.DashboardLook
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.dash.DashLookMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLookTest {

    @Test
    fun fromId_defaultsAndParses() {
        assertEquals(DashboardLook.CLASSIC, DashboardLook.fromId(null))
        assertEquals(DashboardLook.CLASSIC, DashboardLook.fromId("nope"))
        assertEquals(DashboardLook.RED_ORBIT, DashboardLook.fromId("red_orbit"))
        assertEquals(DashboardLook.TWIN_GAUGE, DashboardLook.fromId("TWIN_GAUGE"))
        assertEquals(DashboardLook.PULSE_DECK, DashboardLook.fromId("pulse_deck"))
    }

    @Test
    fun sideMetrics_splitExcludesHeroAndSplitsWheels() {
        val snap = VehicleSnapshot(
            rpm = 2200.0,
            speedKmh = 60.0,
            gear = 3,
            coolantC = 88.0,
            batteryVolts = 14.1,
            intakeC = 35.0,
            engineLoadPct = 30.0,
            throttlePct = 12.0,
            mapKpa = 40.0,
            mafGps = 8.0,
        )
        val metrics = DashLookMetrics.sideMetrics(snap, thresholds = HealthThresholds.DEFAULT)
        assertTrue(metrics.none { it.label.equals("RPM", true) })
        assertTrue(metrics.none { it.label.equals("Speed", true) })
        assertTrue(metrics.any { it.label == "Coolant" })
        assertTrue(metrics.any { it.label == "Battery" })
        val (left, right) = DashLookMetrics.splitWheels(metrics)
        assertEquals(metrics.size, left.size + right.size)
        assertTrue(left.isNotEmpty())
        assertTrue(right.isNotEmpty())
    }
}
