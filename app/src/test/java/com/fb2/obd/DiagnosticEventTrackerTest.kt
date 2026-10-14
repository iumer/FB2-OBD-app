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

    @Test
    fun batteryZone_doesNotFlapCriticalElevatedEverySample() {
        // Recreates Aug 13 session B: 6.5 battery ZONE flaps/min from ELD chatter.
        val tracker = DiagnosticEventTracker()
        val base = VehicleSnapshot(rpm = 1800.0, speedKmh = 90.0, batteryVolts = 12.0)
        tracker.onSnapshot(base, HealthThresholds.DEFAULT) // establish CRITICAL / ALT WEAK

        // One-step recovery to ELEVATED must not emit (hysteresis).
        tracker.onSnapshot(base.copy(batteryVolts = 12.6), HealthThresholds.DEFAULT)
        tracker.onSnapshot(base.copy(batteryVolts = 12.0), HealthThresholds.DEFAULT)
        tracker.onSnapshot(base.copy(batteryVolts = 12.6), HealthThresholds.DEFAULT)

        val battZones = ObdLogger.eventRows().filter {
            it.category == "ZONE" && it.message.startsWith("battery")
        }
        assertTrue(
            "expected no CRITICAL↔ELEVATED flap spam, got: $battZones",
            battZones.isEmpty(),
        )

        // Full recovery to GOOD is allowed.
        tracker.onSnapshot(base.copy(batteryVolts = 14.0), HealthThresholds.DEFAULT)
        assertTrue(
            ObdLogger.eventRows().any {
                it.category == "ZONE" && it.message.contains("battery") && it.message.contains("GOOD")
            },
        )
    }
}
