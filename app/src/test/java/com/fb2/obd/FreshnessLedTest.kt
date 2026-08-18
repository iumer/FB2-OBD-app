package com.fb2.obd

import com.fb2.obd.obd.FreshnessLed
import com.fb2.obd.obd.SnapshotFreshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshnessLedTest {

    @Test
    fun missingTimestamp_isDim() {
        assertEquals(FreshnessLed.DIM, FreshnessLed.alpha(null, nowMs = 1_000L, blinkOn = true), 0.001f)
        assertFalse(FreshnessLed.isLive(null, 1_000L))
    }

    @Test
    fun staleTimestamp_isDimEvenWhenBlinkOn() {
        val last = 0L
        val now = SnapshotFreshness.LED_ACTIVE_MS + 1L
        assertEquals(FreshnessLed.DIM, FreshnessLed.alpha(last, now, blinkOn = true), 0.001f)
        assertFalse(FreshnessLed.isLive(last, now))
    }

    @Test
    fun freshlyFetched_blinksBrightThenPulseOff() {
        val last = 1_000L
        val now = 1_200L
        assertTrue(FreshnessLed.isLive(last, now))
        assertEquals(FreshnessLed.BRIGHT, FreshnessLed.alpha(last, now, blinkOn = true), 0.001f)
        assertEquals(FreshnessLed.PULSE_OFF, FreshnessLed.alpha(last, now, blinkOn = false), 0.001f)
    }
}
