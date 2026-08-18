package com.fb2.obd.obd

/**
 * OEM-style alert policy for the FB2 Civic: longer holds for noisy sensors,
 * short holds for overheat, and voice reserved for high-severity faults.
 *
 * Dash tile colours still update from [HealthEvaluator]; this only gates sound.
 */
object AlertPolicy {

    /** Severity order for hysteresis (UNKNOWN ignored). */
    private val SEVERITY = listOf(
        Health.COLD,
        Health.GOOD,
        Health.WARN,
        Health.ELEVATED,
        Health.CRITICAL,
    )

    fun severityIndex(h: Health): Int = SEVERITY.indexOf(h).let { if (it < 0) 1 else it }

    /**
     * Hold time before a voice/beep may fire for [key].
     * Coolant stays fast; battery / trims need sustained evidence (Honda ELD / STFT chatter).
     */
    fun voiceHoldMs(key: String): Long = when {
        key.startsWith("coolant") -> 4_000L
        key.startsWith("atf") -> 6_000L
        key == "rpm" -> 3_000L
        key == "battery" || key == "battery_low" -> 25_000L
        key == "stft" || key == "ltft" -> 20_000L
        key == "intake" -> 12_000L
        key == "timing" || key == "maf" || key == "slip" -> 15_000L
        else -> 8_000L
    }

    /** Keys that may produce beep+voice. Others are UI-colour only. */
    fun mayVoice(key: String): Boolean = when (key) {
        "coolant", "coolant2", "coolant_hot", "atf", "atf_hot",
        "battery", // critical charging fault only (not battery_low)
        "rpm",
        -> true
        // STFT/LTFT/timing/MAF/MAP/fuel-loop: colour on Dash, no cabin spam.
        else -> false
    }

    /**
     * Apply hysteresis so leaving a worse band needs a clearer recovery.
     * [proposed] is the instantaneous band; [previous] is the latched band.
     *
     * Worsening is accepted immediately. Recovery requires reaching GOOD, or
     * dropping at least two severity steps (so CRITICAL does not flicker to
     * ELEVATED on a one-sample bounce).
     */
    fun latchHealth(
        previous: Health?,
        proposed: Health,
        recoverExtraSteps: Int = 1,
    ): Health {
        if (proposed == Health.UNKNOWN) return previous ?: proposed
        if (previous == null || previous == Health.UNKNOWN) return proposed
        val p = severityIndex(previous)
        val n = severityIndex(proposed)
        if (n >= p) return proposed
        if (proposed == Health.GOOD || proposed == Health.COLD) return proposed
        return if (n <= p - 1 - recoverExtraSteps) proposed else previous
    }
}
