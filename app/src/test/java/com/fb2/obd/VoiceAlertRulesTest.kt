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
            VehicleSnapshot(rpm = 2000.0, coolantC = 112.0, batteryVolts = 14.2),
        )
        assertTrue(alerts.any { it.phrase.equals("Coolant critical", ignoreCase = true) })
    }

    @Test
    fun coolantRedButBelowVoice_noAlert() {
        // Red tile starts >103°C, but voice only above 110°C.
        val alerts = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 2000.0, coolantC = 106.0, batteryVolts = 14.2),
        )
        assertFalse(alerts.any { it.key.startsWith("coolant") })
    }

    @Test
    fun batteryLowWhileRunning_speaksBatteryCritical() {
        val alerts = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 800.0, coolantC = 90.0, batteryVolts = 12.0),
        )
        assertTrue(alerts.any { it.key == "battery" && it.phrase.contains("Battery", ignoreCase = true) })
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
