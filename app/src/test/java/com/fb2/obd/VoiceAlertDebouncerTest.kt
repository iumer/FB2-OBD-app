package com.fb2.obd

import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.obd.VoiceAlertDebouncer
import com.fb2.obd.obd.VoiceAlertRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAlertDebouncerTest {

    private val hold = 2_500L

    @Test
    fun briefSpike_doesNotConfirm() {
        val debouncer = VoiceAlertDebouncer(holdMs = hold)
        val bad = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 2000.0, coolantC = 90.0, batteryVolts = 14.2, timingAdvance = -20.0),
        )
        assertTrue("rules should flag timing", bad.any { it.key == "timing" })

        val t0 = 1_000_000L
        assertTrue(debouncer.confirm(bad, t0).isEmpty())
        // 1s later still bad — under hold
        assertTrue(debouncer.confirm(bad, t0 + 1_000).isEmpty())
        // recovers
        val good = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 2000.0, coolantC = 90.0, batteryVolts = 14.2, timingAdvance = 15.0),
        )
        assertTrue(debouncer.confirm(good, t0 + 1_200).isEmpty())
        // spike again — timer must restart, still not confirmed
        assertTrue(debouncer.confirm(bad, t0 + 1_300).isEmpty())
        assertTrue(debouncer.confirm(bad, t0 + 1_300 + hold - 1).isEmpty())
    }

    @Test
    fun sustainedFault_confirmsAfterHold() {
        val debouncer = VoiceAlertDebouncer(holdMs = hold)
        val bad = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 800.0, coolantC = 90.0, batteryVolts = 12.0),
        )
        val t0 = 5_000_000L
        assertTrue(debouncer.confirm(bad, t0).isEmpty())
        assertTrue(debouncer.confirm(bad, t0 + hold - 1).isEmpty())
        val confirmed = debouncer.confirm(bad, t0 + hold)
        assertEquals(1, confirmed.count { it.key == "battery" })
        assertEquals("Battery critical", confirmed.first { it.key == "battery" }.phrase)
    }

    @Test
    fun recoveryResetsHoldClock() {
        val debouncer = VoiceAlertDebouncer(holdMs = hold)
        val bad = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 800.0, coolantC = 90.0, batteryVolts = 12.0),
        )
        val good = VoiceAlertRules.evaluate(
            VehicleSnapshot(rpm = 800.0, coolantC = 90.0, batteryVolts = 14.2),
        )
        val t0 = 9_000_000L
        debouncer.confirm(bad, t0)
        debouncer.confirm(bad, t0 + 2_000)
        debouncer.confirm(good, t0 + 2_100) // recover — clears pending
        debouncer.confirm(bad, t0 + 2_200) // new hold starts
        assertTrue(debouncer.confirm(bad, t0 + 2_200 + hold - 1).isEmpty())
        assertTrue(debouncer.confirm(bad, t0 + 2_200 + hold).any { it.key == "battery" })
    }
}
