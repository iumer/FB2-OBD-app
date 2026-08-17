package com.fb2.obd.obd

/**
 * Confirms voice alerts only after the same condition has been continuously
 * active for a per-key hold ([AlertPolicy.voiceHoldMs]).
 *
 * Brief ELM spikes / Honda ELD dips clear the timer — no alarm.
 */
class VoiceAlertDebouncer {
    data class Pending(val phrase: String, val firstSeenMs: Long)

    private val pending = mutableMapOf<String, Pending>()

    fun confirm(alerts: List<VoiceAlertRules.Alert>, nowMs: Long): List<VoiceAlertRules.Alert> {
        val activeKeys = alerts.map { it.key }.toSet()
        pending.keys.toList().forEach { key ->
            if (key !in activeKeys) pending.remove(key)
        }

        val confirmed = mutableListOf<VoiceAlertRules.Alert>()
        for (alert in alerts) {
            val hold = AlertPolicy.voiceHoldMs(alert.key)
            val existing = pending[alert.key]
            if (existing == null || existing.phrase != alert.phrase) {
                pending[alert.key] = Pending(alert.phrase, nowMs)
                continue
            }
            if (nowMs - existing.firstSeenMs >= hold) {
                confirmed += alert
            }
        }
        return confirmed
    }

    fun pendingFor(key: String): Pending? = pending[key]

    fun reset() {
        pending.clear()
    }

    companion object {
        /** Fallback documentation value — real holds come from [AlertPolicy]. */
        const val DEFAULT_HOLD_MS = 8_000L
    }
}
