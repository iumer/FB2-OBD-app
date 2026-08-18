package com.fb2.obd

import com.fb2.obd.car.CarDashBuilder
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.obd.isEffectivelyBlank
import com.fb2.obd.obd.mergeLastGood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.1.28 connect crash/regression: Demo→ELM must not feed Demo snapshot into
 * [mergeLastGood] (partial-frame helper). Fresh live connect clears prev to EMPTY
 * so the first ELM frame is not merged with simulated RPM/Speed/freshAtMs.
 */
class ElmConnectTransitionTest {

    private val demoSnap = VehicleSnapshot(
        rpm = 1800.0,
        speedKmh = 55.0,
        coolantC = 88.0,
        mafGps = 8.0,
        freshAtMs = mapOf("rpm" to 1L, "speed" to 1L, "coolant" to 1L),
    )

    @Test
    fun freshLiveConnect_startsFromEmptyPrev_notDemo() {
        // Simulates useSource(isLive=true) clearing snapshot before first ELM frame.
        val prev = VehicleSnapshot.EMPTY
        val firstElm = VehicleSnapshot(batteryVolts = 14.1, unsupportedPids = setOf(0x46))
        val merged = prev.mergeLastGood(firstElm)
        assertEquals(14.1, merged.batteryVolts!!, 0.001)
        assertEquals(null, merged.rpm)
        assertEquals(null, merged.speedKmh)
        assertTrue(merged.isEffectivelyBlank()) // ATRV-only frame is still "blank" for heroes
    }

    @Test
    fun demoIntoMergeLastGood_wouldPoisonFirstElmFrame_ifPrevNotCleared() {
        val firstElm = VehicleSnapshot(batteryVolts = 14.1)
        val poisoned = demoSnap.mergeLastGood(firstElm)
        assertEquals(1800.0, poisoned.rpm!!, 0.001)
        assertEquals(55.0, poisoned.speedKmh!!, 0.001)
        assertEquals(14.1, poisoned.batteryVolts!!, 0.001)
    }

    @Test
    fun connectingWithEmptySnapshot_doesNotShowStickyLiveOnBubble() {
        val state = CarDashBuilder.build(
            snapshot = VehicleSnapshot.EMPTY,
            thresholds = HealthThresholds.DEFAULT,
            extraPidIds = emptyList(),
            extraValues = emptyMap(),
            deepFoundValues = emptyMap(),
            catalog = StandardPidCatalog.all,
            connection = ConnectionState.CONNECTING,
            sourceIsLive = true,
            sourceName = "ELM327",
            logging = false,
            showEstimatedGear = true,
        )
        assertFalse(state.showingLiveValues)
        assertEquals("RETRY", state.bubbleLinkTag)
    }

    @Test
    fun midSessionPartialFrame_stillMergesHeroes() {
        val prev = VehicleSnapshot(rpm = 850.0, speedKmh = 40.0, coolantC = 90.0)
        val atrvOnly = VehicleSnapshot(batteryVolts = 14.0)
        val merged = prev.mergeLastGood(atrvOnly)
        assertEquals(850.0, merged.rpm!!, 0.001)
        assertEquals(40.0, merged.speedKmh!!, 0.001)
        assertEquals(14.0, merged.batteryVolts!!, 0.001)
    }
}
