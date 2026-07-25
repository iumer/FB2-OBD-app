package com.fb2.obd

import com.fb2.obd.car.CarDashBuilder
import com.fb2.obd.car.FloatingDashMetrics
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingDashMetricsTest {

    @Test
    fun wheel_prefersRadialOrder() {
        val state = CarDashBuilder.build(
            snapshot = VehicleSnapshot(
                rpm = 1800.0,
                speedKmh = 55.0,
                coolantC = 88.0,
                batteryVolts = 14.1,
                mafGps = 8.0,
                mapKpa = 40.0,
                intakeC = 32.0,
                gear = 3,
                gearSource = GearSource.ESTIMATED,
            ),
            thresholds = HealthThresholds.DEFAULT,
            extraPidIds = emptyList(),
            extraValues = emptyMap(),
            deepFoundValues = emptyMap(),
            catalog = StandardPidCatalog.all,
            connection = ConnectionState.CONNECTED,
            sourceIsLive = true,
            sourceName = "ELM",
            logging = false,
            showEstimatedGear = true,
        )
        val metrics = FloatingDashMetrics.from(state)
        assertEquals("Coolant 1", metrics[0].label)
        // Collapsed floating bubble always prefers coolant.
        val collapsed = FloatingDashMetrics.collapsedMetric(metrics)
        assertEquals("Coolant 1", collapsed.label)
        assertEquals("88", collapsed.value)
        // First radial page leads with coolant, then RPM (keeps redline colour).
        val page0 = FloatingDashMetrics.page(metrics, 0)
        assertEquals(5, page0.size)
        assertEquals(listOf("Coolant 1", "RPM", "MAP", "Battery", "Intake"), page0.map { it.label })
        assertTrue(page0.any { it.label == "RPM" && it.health != null })
        assertTrue(metrics.any { it.label == "Speed" })
        assertTrue(metrics.any { it.label == "MAF" })
        assertTrue(metrics.size >= 10)
    }

    @Test
    fun rpmHigh_marksCriticalHealthOnBubble() {
        val state = CarDashBuilder.build(
            snapshot = VehicleSnapshot(
                rpm = 7200.0,
                speedKmh = 120.0,
                coolantC = 90.0,
                batteryVolts = 14.2,
            ),
            thresholds = HealthThresholds.DEFAULT,
            extraPidIds = emptyList(),
            extraValues = emptyMap(),
            deepFoundValues = emptyMap(),
            catalog = StandardPidCatalog.all,
            connection = ConnectionState.CONNECTED,
            sourceIsLive = true,
            sourceName = "ELM",
            logging = false,
            showEstimatedGear = true,
        )
        assertEquals("CRITICAL", state.rpmHealth)
        val rpm = FloatingDashMetrics.from(state).first { it.label == "RPM" }
        assertEquals("CRITICAL", rpm.health)
    }

    @Test
    fun page_scrollsGroupsOfFive() {
        val metrics = (1..12).map {
            FloatingDashMetrics.Metric("M$it", "$it", "", null, null)
        }
        assertEquals(3, FloatingDashMetrics.pageCount(metrics))
        assertEquals(listOf("M1", "M2", "M3", "M4", "M5"), FloatingDashMetrics.page(metrics, 0).map { it.label })
        assertEquals(listOf("M6", "M7", "M8", "M9", "M10"), FloatingDashMetrics.page(metrics, 1).map { it.label })
        assertEquals(listOf("M11", "M12"), FloatingDashMetrics.page(metrics, 2).map { it.label })
        // Out-of-range clamps to last page
        assertEquals(listOf("M11", "M12"), FloatingDashMetrics.page(metrics, 99).map { it.label })
    }

    @Test
    fun worstHealth_prefersCritical() {
        assertEquals(
            "CRITICAL",
            FloatingDashMetrics.worstHealth(listOf("GOOD", "WARN", "CRITICAL", "COLD")),
        )
        assertEquals("WARN", FloatingDashMetrics.worstHealth(listOf("GOOD", "WARN")))
    }
}
