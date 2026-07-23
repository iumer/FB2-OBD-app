package com.fb2.obd

import com.fb2.obd.perf.AccelerationTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccelerationTimerTest {

    private fun run(timer: AccelerationTimer, kmhPerSec: Double, untilMs: Int) {
        timer.onSample(0, 0.0) // stationary -> arm
        var t = 0
        while (t <= untilMs) {
            timer.onSample(t.toLong(), kmhPerSec * (t / 1000.0))
            t += 100
        }
    }

    @Test
    fun times0to100() {
        val timer = AccelerationTimer()
        run(timer, kmhPerSec = 12.5, untilMs = 9000) // ~8 s to 100 km/h

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
    }

    @Test
    fun reset_clearsCurrentRun() {
        val timer = AccelerationTimer()
        run(timer, kmhPerSec = 20.0, untilMs = 6000)
        assertNotNull(timer.current.zeroTo100Kmh)
        timer.reset()
        assertNull(timer.current.zeroTo100Kmh)
    }
}
