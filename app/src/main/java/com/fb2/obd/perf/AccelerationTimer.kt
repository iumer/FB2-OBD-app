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

enum class AccelPhase {
    /** After reset / between runs: must see motion then a full stop before a launch counts. */
    NEED_STOP,
    /** Stopped and ready — next launch starts the timer. */
    ARMED,
    /** Timing an active run. */
    RUNNING,
}

/**
 * Times acceleration runs from vehicle speed samples.
 *
 * After [reset], the timer does **not** start on the next roll-away. It waits until
 * the car has moved and then come to a complete stop ([NEED_STOP] → [ARMED]); only
 * then does 0→launch start a timed run. That prevents "drive to the road" from
 * counting as a 0–100.
 */
class AccelerationTimer {

    var current: AccelResult = AccelResult()
        private set

    /** Best (fastest) 0–100 km/h run seen so far, kept across runs. */
    var best: AccelResult = AccelResult()
        private set

    var phase: AccelPhase = AccelPhase.NEED_STOP
        private set

    private var sawMotionSinceReset = false
    private var launchMs = 0L
    private var sixtyKmhMs: Long? = null
    private var distanceM = 0.0
    private var lastMs = 0L
    private var lastSpeed = 0.0

    private companion object {
        /** Start timing as soon as speed leaves a true stop (~0→1 km/h). */
        const val LAUNCH_KMH = 1.0
        const val STOP_KMH = 0.5
        const val QUARTER_MILE_M = 402.336
        const val MPH60_KMH = 96.5606
        /** Abort ridiculous runs (e.g. casual driving after a false start). */
        const val MAX_RUN_SEC = 45.0
    }

    fun reset() {
        current = AccelResult()
        phase = AccelPhase.NEED_STOP
        sawMotionSinceReset = false
        sixtyKmhMs = null
        distanceM = 0.0
        lastMs = 0L
        lastSpeed = 0.0
    }

    /** Feed a speed sample. [speedKmh] is vehicle speed; [tMs] a monotonic-ish clock. */
    fun onSample(tMs: Long, speedKmh: Double) {
        when (phase) {
            AccelPhase.NEED_STOP -> {
                if (speedKmh > STOP_KMH) {
                    sawMotionSinceReset = true
                } else if (sawMotionSinceReset && speedKmh <= STOP_KMH) {
                    // Fresh stop after driving — now armed for the next launch.
                    phase = AccelPhase.ARMED
                }
            }

            AccelPhase.ARMED -> {
                if (speedKmh >= LAUNCH_KMH) {
                    phase = AccelPhase.RUNNING
                    launchMs = tMs
                    lastMs = tMs
                    lastSpeed = speedKmh
                    distanceM = 0.0
                    sixtyKmhMs = null
                    current = AccelResult()
                } else if (speedKmh > STOP_KMH) {
                    // Creep / rolling — stay armed only while essentially stopped.
                    // If they roll above stop without launching hard, keep waiting at stop.
                }
                // Stay ARMED while stopped.
            }

            AccelPhase.RUNNING -> {
                val dtSec = (tMs - lastMs) / 1000.0
                if (dtSec > 0) {
                    val avgMs = ((speedKmh + lastSpeed) / 2.0) / 3.6
                    distanceM += avgMs * dtSec
                }
                val elapsed = (tMs - launchMs) / 1000.0

                // Abort if this clearly isn't an acceleration test.
                if (elapsed > MAX_RUN_SEC && current.zeroTo100Kmh == null) {
                    current = AccelResult()
                    phase = AccelPhase.NEED_STOP
                    sawMotionSinceReset = speedKmh > STOP_KMH
                    lastMs = tMs
                    lastSpeed = speedKmh
                    return
                }

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

                if (speedKmh <= STOP_KMH) {
                    // End of run — require a fresh stop cycle before next attempt.
                    phase = AccelPhase.NEED_STOP
                    sawMotionSinceReset = false
                }

                lastMs = tMs
                lastSpeed = speedKmh
            }
        }
    }
}
