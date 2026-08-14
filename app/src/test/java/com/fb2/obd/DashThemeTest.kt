package com.fb2.obd

import com.fb2.obd.obd.DashTheme
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.dash.DashThemeMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashThemeTest {

    @Test
    fun fromId_defaultsAndParses() {
        assertEquals(DashTheme.CLASSIC, DashTheme.fromId(null))
        assertEquals(DashTheme.CLASSIC, DashTheme.fromId("nope"))
        assertEquals(DashTheme.OPT_A, DashTheme.fromId("opt_a"))
        assertEquals(DashTheme.OPT_B, DashTheme.fromId("OPT_B"))
        assertEquals(DashTheme.OPT_C, DashTheme.fromId("opt_c"))
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
        val metrics = DashThemeMetrics.sideMetrics(snap, thresholds = HealthThresholds.DEFAULT)
        assertTrue(metrics.none { it.label.equals("RPM", true) })
        assertTrue(metrics.none { it.label.equals("Speed", true) })
        assertTrue(metrics.any { it.label == "Coolant 1" })
        assertTrue(metrics.any { it.label == "Battery" })
        assertTrue(metrics.any { it.freshAtMs != null || it.label == "DTCs" || it.label == "Health" })
        val (left, right) = DashThemeMetrics.splitWheels(metrics)
        assertEquals(metrics.size, left.size + right.size)
        assertTrue(left.isNotEmpty())
        assertTrue(right.isNotEmpty())
    }

    @Test
    fun fuelLoop_abbreviatesClosedOpen() {
        assertEquals("CLOSED", DashThemeMetrics.abbreviateFuelLoop("CLOSED LOOP"))
        assertEquals("OPEN", DashThemeMetrics.abbreviateFuelLoop("OPEN LOOP"))
        assertEquals("--", DashThemeMetrics.abbreviateFuelLoop(null))
    }

    @Test
    fun sideMetrics_appliesDeepFoundWhenBlank() {
        val snap = VehicleSnapshot(rpm = 1000.0, coolantC = null)
        val metrics = DashThemeMetrics.sideMetrics(
            snap,
            deepFoundValues = mapOf("Coolant 1" to "91 °C"),
        )
        val cool = metrics.first { it.label == "Coolant 1" }
        assertEquals("91", cool.value)
        assertEquals("°C", cool.unit)
    }
}
