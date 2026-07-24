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
    fun wheel_startsWithHeroThenTiles() {
        val state = CarDashBuilder.build(
            snapshot = VehicleSnapshot(
                rpm = 1800.0,
                speedKmh = 55.0,
                coolantC = 88.0,
                batteryVolts = 14.1,
                mafGps = 8.0,
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
        assertEquals("RPM", metrics[0].label)
        assertEquals("Speed", metrics[1].label)
        assertEquals("Gear", metrics[2].label)
        assertTrue(metrics.any { it.label == "Battery" })
        assertTrue(metrics.any { it.label == "MAF" })
        assertTrue(metrics.size >= 10)
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
