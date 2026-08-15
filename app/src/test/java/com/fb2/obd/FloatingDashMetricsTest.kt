package com.fb2.obd

import com.fb2.obd.car.CarDashBuilder
import com.fb2.obd.car.FloatingDashMetrics
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.HealthEvaluator
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
        // Default collapsed primary is Coolant; pin overrides.
        assertEquals("Coolant 1", FloatingDashMetrics.collapsedMetric(metrics).label)
        assertEquals("RPM", FloatingDashMetrics.collapsedMetric(metrics, "RPM").label)
        assertEquals("MAF", FloatingDashMetrics.collapsedMetric(metrics, "MAF").label)
        assertEquals("88", FloatingDashMetrics.collapsedMetric(metrics).value)
        assertEquals(0, FloatingDashMetrics.pageIndexOf(metrics, "Coolant 1"))
        assertEquals(
            FloatingDashMetrics.pageIndexOf(metrics, "MAF"),
            metrics.indexOfFirst { it.label == "MAF" } / FloatingDashMetrics.PAGE_SIZE,
        )
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

    @Test
    fun offlineError_bubbleShowsOffDashNotStaleCoolant() {
        // Sticky last-good must not survive on the CarPlay bubble after ELM ERROR.
        val state = CarDashBuilder.build(
            snapshot = VehicleSnapshot.EMPTY,
            thresholds = HealthThresholds.DEFAULT,
            extraPidIds = emptyList(),
            extraValues = emptyMap(),
            deepFoundValues = emptyMap(),
            catalog = StandardPidCatalog.all,
            connection = ConnectionState.ERROR,
            sourceIsLive = false,
            sourceName = "ELM327",
            logging = false,
            showEstimatedGear = true,
        )
        assertEquals(false, state.showingLiveValues)
        assertEquals("Disconnected", state.statusLine)
        val metrics = FloatingDashMetrics.from(state)
        assertEquals(1, metrics.size)
        assertEquals("OFF", metrics[0].label)
        assertEquals("--", metrics[0].value)
        assertEquals("--", FloatingDashMetrics.collapsedMetric(metrics, "Coolant 1").value)
    }

    @Test
    fun coolantBands_matchFb2DriveSpec() {
        val t = HealthThresholds.DEFAULT
        assertEquals(95.0, t.coolantGoodMax, 0.001)
        assertEquals(100.0, t.coolantWarnMax, 0.001)
        assertEquals(103.0, t.coolantElevatedMax, 0.001)
        assertEquals(104.0, t.coolantVoiceAbove, 0.001)
        // Bubble ring health follows these bands (91°C green in user photo).
        assertEquals("GOOD", HealthEvaluator.coolant(91.0, t).health.name)
        assertEquals("WARN", HealthEvaluator.coolant(96.0, t).health.name)
        assertEquals("ELEVATED", HealthEvaluator.coolant(101.0, t).health.name)
        assertEquals("CRITICAL", HealthEvaluator.coolant(104.0, t).health.name)
    }
}
