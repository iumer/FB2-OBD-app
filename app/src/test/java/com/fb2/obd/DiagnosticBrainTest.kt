package com.fb2.obd

import com.fb2.obd.obd.AlertPolicy
import com.fb2.obd.obd.DiagnosticBrain
import com.fb2.obd.obd.Health
import com.fb2.obd.obd.MetricStatus
import com.fb2.obd.obd.SignalSmoother
import com.fb2.obd.obd.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticBrainTest {

    @Test
    fun smoother_blendsTowardNewSamples() {
        val s = SignalSmoother(alpha = 0.5)
        assertEquals(14.0, s.push("batt", 14.0)!!, 0.001)
        val mid = s.push("batt", 12.0)!!
        assertEquals(13.0, mid, 0.001)
    }

    @Test
    fun decisionSnapshot_smoothsBatteryKeepsRpmRaw() {
        val brain = DiagnosticBrain(SignalSmoother(alpha = 0.5))
        val first = brain.decisionSnapshot(
            VehicleSnapshot(rpm = 2000.0, batteryVolts = 14.0, stftPct = 0.0),
        )
        assertEquals(14.0, first.batteryVolts!!, 0.001)
        assertEquals(2000.0, first.rpm!!, 0.001)
        val second = brain.decisionSnapshot(
            VehicleSnapshot(rpm = 2100.0, batteryVolts = 12.0, stftPct = 10.0),
        )
        assertEquals(13.0, second.batteryVolts!!, 0.001)
        assertEquals(5.0, second.stftPct!!, 0.001)
        assertEquals(2100.0, second.rpm!!, 0.001)
    }

    @Test
    fun latch_holdsWorseBandUntilClearRecovery() {
        val brain = DiagnosticBrain()
        val crit = brain.latch("battery", MetricStatus(Health.CRITICAL, "ALT WEAK"))
        assertEquals(Health.CRITICAL, crit.health)
        // One-step recovery to ELEVATED is rejected (hysteresis).
        val held = brain.latch("battery", MetricStatus(Health.ELEVATED, "WEAK CHARGE"))
        assertEquals(Health.CRITICAL, held.health)
        assertEquals("ALT WEAK", held.label)
        // Full recovery to GOOD is accepted.
        val good = brain.latch("battery", MetricStatus(Health.GOOD, "CHARGING OK"))
        assertEquals(Health.GOOD, good.health)
    }

    @Test
    fun alertPolicy_voiceHolds_areLongerForBatteryThanCoolant() {
        assertTrue(AlertPolicy.voiceHoldMs("battery") > AlertPolicy.voiceHoldMs("coolant"))
        assertTrue(AlertPolicy.mayVoice("coolant"))
        assertTrue(AlertPolicy.mayVoice("battery"))
        assertTrue(!AlertPolicy.mayVoice("stft"))
        assertTrue(!AlertPolicy.mayVoice("battery_low"))
    }
}
