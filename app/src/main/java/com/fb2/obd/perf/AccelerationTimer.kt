package com.fb2.obd.perf

/** Results of a single acceleration run (nulls until each milestone is reached). */
data class AccelResult(
    val zeroTo100Kmh: Double? = null,
    val zeroTo60Mph: Double? = null,
    val zeroTo160Kmh: Double? = null,
    val sixtyTo100Kmh: Double? = null,
    val quarterMileSec: Double? = null,
    val quarterMileTrapKmh: Double? = null,
)

/**
 * Times acceleration runs from vehicle speed samples. Arms itself when the car is
 * stationary and starts timing on launch; records milestones (0–100 km/h,
 * 0–60 mph, 0–160 km/h, 60–100 km/h, and the quarter-mile time + trap speed).
 *
 * Pure Kotlin, unit testable — feed it (timestamp, speed) samples.
 */
class AccelerationTimer {

    var current: AccelResult = AccelResult()
        private set

    /** Best (fastest) 0–100 km/h run seen so far, kept across runs. */
    var best: AccelResult = AccelResult()
        private set

    private var armed = true
    private var running = false
    private var launchMs = 0L
    private var sixtyKmhMs: Long? = null
    private var distanceM = 0.0
    private var lastMs = 0L
    private var lastSpeed = 0.0

    private companion object {
        const val LAUNCH_KMH = 3.0
        const val STOP_KMH = 1.0
        const val QUARTER_MILE_M = 402.336
        const val MPH60_KMH = 96.5606
    }

    fun reset() {
        current = AccelResult()
        armed = true
        running = false
        sixtyKmhMs = null
        distanceM = 0.0
    }

    /** Feed a speed sample. [speedKmh] is vehicle speed; [tMs] a monotonic-ish clock. */
    fun onSample(tMs: Long, speedKmh: Double) {
        if (!running) {
            if (speedKmh <= STOP_KMH) {
                armed = true
            } else if (armed && speedKmh >= LAUNCH_KMH) {
                // Launch detected.
                running = true
                armed = false
                launchMs = tMs
                lastMs = tMs
                lastSpeed = speedKmh
                distanceM = 0.0
                sixtyKmhMs = null
                current = AccelResult()
            }
            return
        }

        // Running: integrate distance (trapezoid) and record milestones.
        val dtSec = (tMs - lastMs) / 1000.0
        if (dtSec > 0) {
            val avgMs = ((speedKmh + lastSpeed) / 2.0) / 3.6
            distanceM += avgMs * dtSec
        }
        val elapsed = (tMs - launchMs) / 1000.0

        if (current.zeroTo60Mph == null && speedKmh >= MPH60_KMH) {
            current = current.copy(zeroTo60Mph = elapsed)
        }
        if (sixtyKmhMs == null && speedKmh >= 60.0) {
            sixtyKmhMs = tMs
        }
        if (current.zeroTo100Kmh == null && speedKmh >= 100.0) {
            current = current.copy(zeroTo100Kmh = elapsed)
            val s = sixtyKmhMs
            if (s != null) current = current.copy(sixtyTo100Kmh = (tMs - s) / 1000.0)
            if (best.zeroTo100Kmh == null || elapsed < best.zeroTo100Kmh!!) {
                best = best.copy(zeroTo100Kmh = elapsed)
            }
        }
        if (current.zeroTo160Kmh == null && speedKmh >= 160.0) {
            current = current.copy(zeroTo160Kmh = elapsed)
        }
        if (current.quarterMileSec == null && distanceM >= QUARTER_MILE_M) {
            current = current.copy(quarterMileSec = elapsed, quarterMileTrapKmh = speedKmh)
        }

        // Coming to a stop ends the run (keep the results).
        if (speedKmh <= STOP_KMH) {
            running = false
            armed = true
        }

        lastMs = tMs
        lastSpeed = speedKmh
    }
}
