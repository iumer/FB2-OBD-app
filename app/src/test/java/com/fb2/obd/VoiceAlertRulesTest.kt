package com.fb2.obd

import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.obd.VoiceAlertRules
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
                coolantC = 92.0,
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

    @Test
    fun coolantHotElevated_speaksCoolantHot() {
        val alerts = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 2000.0, coolantC = 106.0, batteryVolts = 14.2),
        )
        assertTrue(alerts.any { it.phrase.contains("hot", ignoreCase = true) })
    }
}
