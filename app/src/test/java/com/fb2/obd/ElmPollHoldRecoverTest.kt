package com.fb2.obd

import com.fb2.obd.data.Elm327BluetoothSource
import com.fb2.obd.obd.PollHold
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.obd.mergeLastGood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.1.27 regression: withDashKeptAlive ended with HEROES_ONLY → NONE, which armed
 * recoverAfterResume and soft-recovered the bus every ~12s (readiness poll). Mode 01
 * heroes died; only ATRV/battery stayed live.
 */
class ElmPollHoldRecoverTest {

    @Test
    fun recoverOnlyAfterFullPauseEnds() {
        assertTrue(
            Elm327BluetoothSource.shouldRecoverAfterResume(PollHold.FULL_PAUSE, PollHold.NONE),
        )
        assertFalse(
            Elm327BluetoothSource.shouldRecoverAfterResume(PollHold.HEROES_ONLY, PollHold.NONE),
        )
        assertFalse(
            Elm327BluetoothSource.shouldRecoverAfterResume(PollHold.NONE, PollHold.HEROES_ONLY),
        )
    }

    @Test
    fun mergeLastGood_keepsHeroesWhenIncomingIsAtrvPlusSecondary() {
        val prev = VehicleSnapshot(
            rpm = 850.0,
            speedKmh = 0.0,
            coolantC = 91.0,
            mafGps = 3.2,
        )
        val incoming = VehicleSnapshot(
            batteryVolts = 14.1,
            stftPct = 2.5,
        )
        val merged = prev.mergeLastGood(incoming)
        assertEquals(850.0, merged.rpm!!, 0.001)
        assertEquals(91.0, merged.coolantC!!, 0.001)
        assertEquals(14.1, merged.batteryVolts!!, 0.001)
        assertEquals(2.5, merged.stftPct!!, 0.001)
    }

    @Test
    fun mergeLastGood_atrvOnlyUpdatesBatteryWithoutWipingHeroes() {
        val prev = VehicleSnapshot(rpm = 900.0, coolantC = 88.0)
        val merged = prev.mergeLastGood(VehicleSnapshot(batteryVolts = 14.0))
        assertEquals(900.0, merged.rpm!!, 0.001)
        assertEquals(88.0, merged.coolantC!!, 0.001)
        assertEquals(14.0, merged.batteryVolts!!, 0.001)
    }
}
