package com.fb2.obd

import com.fb2.obd.obd.FuelSystemDecoder
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.obd.VoiceAlertRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAlertRulesTest {

    @Test
    fun coolantOverheat_speaksCoolantCritical() {
        val alerts = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 2000.0, coolantC = 104.0, batteryVolts = 14.2),
        )
        assertTrue(alerts.any { it.phrase.equals("Coolant critical", ignoreCase = true) })
    }

    @Test
    fun coolantRedButBelowVoice_noAlert() {
        // Orange/red tile can start at >103°C; voice only at/above 104°C.
        val alerts = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 2000.0, coolantC = 103.0, batteryVolts = 14.2),
        )
        assertFalse(alerts.any { it.key.startsWith("coolant") })
    }

    @Test
    fun batteryCriticalAboveIdle_speaksBatteryCritical() {
        // Voice only at/below 11.8V — not for ALT WEAK tile colour at ~12.5.
        val alerts = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 2000.0, coolantC = 90.0, batteryVolts = 11.8),
        )
        assertTrue(alerts.any { it.key == "battery" && it.phrase.equals("Battery critical", ignoreCase = true) })
    }

    @Test
    fun batteryAltWeakColour_noVoiceUntil118() {
        val alerts = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 2000.0, coolantC = 90.0, batteryVolts = 12.0),
        )
        assertFalse(alerts.any { it.key == "battery" })
    }

    @Test
    fun batteryLowAtIdle_noVoice_eldSoft() {
        // ELD often sits ~12.x–13.x at idle — UI colour only, no cabin voice.
        val alerts = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 800.0, coolantC = 90.0, batteryVolts = 12.0),
        )
        assertFalse(alerts.any { it.key.startsWith("battery") })
    }

    @Test
    fun batteryElevated_noVoice() {
        // Orange WEAK CHARGE is Dash colour only — no "Battery low" voice spam.
        val alerts = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 2000.0, coolantC = 90.0, batteryVolts = 12.9),
        )
        assertFalse(alerts.any { it.key == "battery_low" })
        assertFalse(alerts.any { it.key == "battery" })
    }

    @Test
    fun fuelTrimAndTiming_noVoice() {
        val alerts = VoiceAlertRules.evaluate(
            VehicleSnapshot(
                rpm = 2000.0,
                coolantC = 90.0,
                batteryVolts = 14.2,
                stftPct = 22.0,
                timingAdvance = -10.0,
            ),
        )
        assertFalse(alerts.any { it.key == "stft" || it.key == "timing" })
    }

    @Test
    fun normalValues_noAlerts() {
        val alerts = VoiceAlertRules.evaluate(
            VehicleSnapshot(
                rpm = 2000.0,
                coolantC = 85.0,
                batteryVolts = 14.2,
                stftPct = 1.0,
                engineLoadPct = 30.0,
                intakeC = 32.0,
                timingAdvance = 15.0,
            ),
            HealthThresholds.DEFAULT,
        )
        assertTrue(alerts.isEmpty())
    }
}

class FuelSystemDecoderTest {
    @Test
    fun closedAndOpenBits() {
        assertEquals("CLOSED LOOP", FuelSystemDecoder.decodeBank(0x02))
        assertEquals("OPEN LOOP", FuelSystemDecoder.decodeBank(0x01))
        assertEquals("OPEN (DRIVE)", FuelSystemDecoder.decodeBank(0x04))
        assertEquals("OPEN (FAULT)", FuelSystemDecoder.decodeBank(0x08))
        assertEquals("CLOSED (FAULT)", FuelSystemDecoder.decodeBank(0x10))
    }
}
