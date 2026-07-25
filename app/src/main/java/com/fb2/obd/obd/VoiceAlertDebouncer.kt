package com.fb2.obd.obd

/**
 * Confirms voice alerts only after the same condition has been continuously
 * active for [holdMs]. Filters one-frame ELM spikes / false positives
 * (e.g. timing briefly out of band) without delaying the Dash tile colours.
 *
 * If the metric recovers before [holdMs], the timer resets — no alarm.
 */
class VoiceAlertDebouncer(
    var holdMs: Long = DEFAULT_HOLD_MS,
) {
    data class Pending(val phrase: String, val firstSeenMs: Long)

    private val pending = mutableMapOf<String, Pending>()

    /**
     * @return subset of [alerts] that have been active with the same phrase
     * for at least [holdMs].
     */
    fun confirm(alerts: List<VoiceAlertRules.Alert>, nowMs: Long): List<VoiceAlertRules.Alert> {
        val activeKeys = alerts.map { it.key }.toSet()
        pending.keys.toList().forEach { key ->
            if (key !in activeKeys) pending.remove(key)
        }

        val confirmed = mutableListOf<VoiceAlertRules.Alert>()
        for (alert in alerts) {
            val existing = pending[alert.key]
            if (existing == null || existing.phrase != alert.phrase) {
                // New condition (or phrase changed) — start hold clock.
                pending[alert.key] = Pending(alert.phrase, nowMs)
                continue
            }
            if (nowMs - existing.firstSeenMs >= holdMs) {
                confirmed += alert
            }
        }
        return confirmed
    }

    /** Test / diagnostics: how long [key] has been pending, or null. */
    fun pendingFor(key: String): Pending? = pending[key]

    fun reset() {
        pending.clear()
    }

    companion object {
        /** ~2–3 OBD poll cycles on typical HU polling — long enough to ignore spikes. */
        const val DEFAULT_HOLD_MS = 2_500L
    }
}
