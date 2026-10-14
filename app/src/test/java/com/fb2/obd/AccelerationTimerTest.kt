package com.fb2.obd

import com.fb2.obd.perf.AccelPhase
import com.fb2.obd.perf.AccelerationTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccelerationTimerTest {

    /** Proper drag-strip sequence: move, stop, then accelerate. */
    private fun runFromStop(timer: AccelerationTimer, kmhPerSec: Double, untilMs: Int) {
        // Roll a bit then stop so the timer arms (post-reset rule).
        timer.onSample(0, 0.0)
        timer.onSample(200, 10.0) // motion since reset
        timer.onSample(500, 0.0) // fresh stop -> ARMED
        assertEquals(AccelPhase.ARMED, timer.phase)

        var t = 1000
        while (t <= 1000 + untilMs) {
            val speed = kmhPerSec * ((t - 1000) / 1000.0)
            timer.onSample(t.toLong(), speed)
            t += 100
        }
    }

    @Test
    fun times0to100() {
        val timer = AccelerationTimer()
        runFromStop(timer, kmhPerSec = 12.5, untilMs = 9000) // ~8 s to 100 km/h

        val z = timer.current.zeroTo100Kmh
        assertNotNull(z)
        assertTrue("0-100 was $z", z!! in 7.0..8.2)
        assertNotNull(timer.current.zeroTo60Mph)
        assertEquals(z, timer.best.zeroTo100Kmh)
    }

    @Test
    fun noLaunch_whenNeverMoves() {
        val timer = AccelerationTimer()
        repeat(10) { timer.onSample(it * 100L, 0.0) }
        assertNull(timer.current.zeroTo100Kmh)
        assertEquals(AccelPhase.NEED_STOP, timer.phase)
    }

    @Test
    fun reset_thenDriveAround_doesNotTimeUntilFreshStopAndLaunch() {
        val timer = AccelerationTimer()
        // Cleared while already stopped, then drive to the open road (~60s), never a timed run.
        timer.reset()
        timer.onSample(0, 0.0)
        // Leave the lights
        for (t in 100..60_000 step 500) {
            timer.onSample(t.toLong(), 40.0)
        }
        assertNull("must not start timing on first roll-away after reset", timer.current.zeroTo100Kmh)
        assertEquals(AccelPhase.NEED_STOP, timer.phase)

        // Stop at the open road
        timer.onSample(60_500, 0.0)
        assertEquals(AccelPhase.ARMED, timer.phase)

        // Proper launch to 100 in ~8s
        for (t in 61_000..69_000 step 100) {
            val speed = 12.5 * ((t - 61_000) / 1000.0)
            timer.onSample(t.toLong(), speed)
        }
        val z = timer.current.zeroTo100Kmh
        assertNotNull(z)
        assertTrue("expected ~8s, got $z", z!! in 7.0..8.5)
    }

    @Test
    fun reset_clearsCurrentRun() {
        val timer = AccelerationTimer()
        runFromStop(timer, kmhPerSec = 20.0, untilMs = 6000)
        assertNotNull(timer.current.zeroTo100Kmh)
        timer.reset()
        assertNull(timer.current.zeroTo100Kmh)
        assertEquals(AccelPhase.NEED_STOP, timer.phase)
    }
}
