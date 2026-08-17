package com.fb2.obd

import com.fb2.obd.data.ObdLogger
import com.fb2.obd.obd.DiagnosticEventTracker
import com.fb2.obd.obd.FuelSystemDecoder
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.VehicleSnapshot
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticEventTrackerTest {

    @Before
    fun clear() {
        ObdLogger.clearValues()
        ObdLogger.clearDebug()
    }

    @Test
    fun fuelLoopAndCoolantZone_emitEvents() {
        val tracker = DiagnosticEventTracker()
        val cold = VehicleSnapshot(
            rpm = 800.0,
            speedKmh = 0.0,
            coolantC = 40.0,
            batteryVolts = 14.2,
            fuelSystemStatus = "OPEN LOOP",
        )
        tracker.onSnapshot(cold, HealthThresholds.DEFAULT)

        val warm = cold.copy(coolantC = 85.0, fuelSystemStatus = "CLOSED LOOP")
        tracker.onSnapshot(warm, HealthThresholds.DEFAULT)

        val csv = ObdLogger.valuesCsv()
        assertTrue(csv.contains("# events"))
        assertTrue(
            ObdLogger.eventRows().any { it.message.contains("Fuel system") && it.message.contains("CLOSED") },
        )
        assertTrue(
            ObdLogger.eventRows().any { it.category == "ZONE" && it.message.contains("coolant") },
        )
    }

    @Test
    fun fuelSystemDecoder_helloWorldClosedLoop() {
        // SAE bit 1 = closed loop using O2 feedback
        assertTrue(FuelSystemDecoder.decode(intArrayOf(0x02, 0x00)) == "CLOSED LOOP")
        assertTrue(FuelSystemDecoder.fromRawByte(2.0) == "CLOSED LOOP")
    }
}
