package com.fb2.obd.obd

/**
 * Exponential moving average for noisy OBD samples.
 * Pure / Android-free for unit tests.
 */
class SignalSmoother(
    private val alpha: Double = 0.35,
) {
    private val values = mutableMapOf<String, Double>()

    fun push(key: String, sample: Double?): Double? {
        // Intentional null (stale-cleared Dash field) must clear EMA — otherwise
        // health/voice keep a hours-old Coolant/Battery after sanitize blanks UI.
        if (sample == null) {
            values.remove(key)
            return null
        }
        val prev = values[key]
        val next = if (prev == null) sample else prev + alpha * (sample - prev)
        values[key] = next
        return next
    }

    fun get(key: String): Double? = values[key]

    fun reset() = values.clear()
}

/**
 * Stateful diagnostic filter: smooths noisy signals and latches health bands
 * with hysteresis so Honda ELD / STFT chatter does not flicker the Dash.
 */
class DiagnosticBrain(
    private val smoother: SignalSmoother = SignalSmoother(),
) {
    private val latched = mutableMapOf<String, MetricStatus>()

    /**
     * Build a decision snapshot (smoothed) for health/voice while the UI can
     * still show the raw [snapshot] values.
     */
    fun decisionSnapshot(snapshot: VehicleSnapshot): VehicleSnapshot {
        return snapshot.copy(
            batteryVolts = smoother.push("batt", snapshot.batteryVolts),
            stftPct = smoother.push("stft", snapshot.stftPct),
            ltftPct = smoother.push("ltft", snapshot.ltftPct),
            mapKpa = smoother.push("map", snapshot.mapKpa),
            coolantC = smoother.push("cool1", snapshot.coolantC),
            coolant2C = smoother.push("cool2", snapshot.coolant2C),
            timingAdvance = smoother.push("timing", snapshot.timingAdvance),
            mafGps = smoother.push("maf", snapshot.mafGps),
            intakeC = smoother.push("intake", snapshot.intakeC),
        )
    }

    fun latch(key: String, status: MetricStatus): MetricStatus {
        val prev = latched[key]
        val nextHealth = AlertPolicy.latchHealth(prev?.health, status.health)
        val out = when {
            prev == null || nextHealth == status.health -> status
            else -> prev.copy(health = nextHealth) // hold previous worse band + label
        }
        latched[key] = out
        return out
    }

    fun reset() {
        smoother.reset()
        latched.clear()
    }
}
