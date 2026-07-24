package com.fb2.obd

import com.fb2.obd.car.CarDashBuilder
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDashBuilderTest {

    @Test
    fun buildsHeroAndBaseTiles() {
        val state = CarDashBuilder.build(
            snapshot = VehicleSnapshot(
                rpm = 2100.0,
                speedKmh = 72.0,
                coolantC = 92.0,
                batteryVolts = 14.2,
                gear = 3,
                gearSource = GearSource.ESTIMATED,
                gearConfidencePct = 95,
            ),
            thresholds = HealthThresholds.DEFAULT,
            extraPidIds = emptyList(),
            extraValues = emptyMap(),
            deepFoundValues = emptyMap(),
            catalog = StandardPidCatalog.all,
            connection = ConnectionState.CONNECTED,
            sourceIsLive = false,
            sourceName = "Demo",
            logging = false,
            showEstimatedGear = true,
        )
        assertEquals("2100", state.rpm)
        assertEquals("72", state.speedKmh)
        assertEquals("3", state.gear)
        assertTrue(state.tiles.any { it.label == "Coolant 1" && it.value == "92" })
        assertEquals("CONNECT", state.connectLabel) // demo is not live ELM
        assertTrue(state.statusLine.contains("DEMO"))
    }
}
