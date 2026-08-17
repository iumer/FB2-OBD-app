package com.fb2.obd.obd

/**
 * Torque-style green heartbeat brightness. Pure Kotlin so JVM tests can lock
 * the blink rules without Compose timers.
 *
 * One shared blink clock in the UI toggles [blinkOn] (~3 Hz). Tiles must not
 * each run their own Animatable (that stuttered car HUs).
 */
object FreshnessLed {

    /** Bright pulse while this field was decoded within [SnapshotFreshness.LED_ACTIVE_MS]. */
    fun alpha(lastOkMs: Long?, nowMs: Long, blinkOn: Boolean): Float {
        if (lastOkMs == null) return DIM
        val age = nowMs - lastOkMs
        if (age >= SnapshotFreshness.LED_ACTIVE_MS) return DIM
        return if (blinkOn) BRIGHT else PULSE_OFF
    }

    fun isLive(lastOkMs: Long?, nowMs: Long): Boolean {
        if (lastOkMs == null) return false
        return nowMs - lastOkMs < SnapshotFreshness.LED_ACTIVE_MS
    }

    const val BRIGHT = 1f
    const val PULSE_OFF = 0.22f
    const val DIM = 0.14f

    /** Half-period for the shared UI clock (~1.8 Hz full blink). */
    const val BLINK_HALF_MS = 280L
}
