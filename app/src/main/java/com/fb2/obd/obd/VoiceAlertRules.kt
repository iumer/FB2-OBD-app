package com.fb2.obd.obd

/**
 * Pure decision logic for spoken / beeped alarms.
 *
 * UI tile colours still use full [HealthEvaluator] bands. Voice is reserved for
 * genuine high-severity faults (coolant, charging CRITICAL, redline, ATF) —
 * slight rich/lean, MAP, timing chatter, and Honda ELD dips stay silent.
 */
object VoiceAlertRules {

    data class Alert(
        val key: String,
        val phrase: String,
        val priority: Int,
        /** Optional debug detail (value / reason) — not always spoken. */
        val detail: String = "",
    )

    fun evaluate(
        snapshot: VehicleSnapshot,
        thresholds: HealthThresholds = HealthThresholds.DEFAULT,
        atfC: Double? = null,
        tcSlipRpm: Double? = null,
    ): List<Alert> {
        val running = (snapshot.rpm ?: 0.0) > 0.0
        val out = mutableListOf<Alert>()

        fun addCritical(key: String, phrase: String, priority: Int, status: MetricStatus, detail: String = "") {
            if (!AlertPolicy.mayVoice(key)) return
            if (status.health == Health.CRITICAL) {
                out += Alert(key, phrase, priority, detail.ifBlank { status.label })
            }
        }

        // Coolant: voice only well into overheat (default >110°C).
        val c1 = snapshot.coolantC
        if (c1 != null && c1 > thresholds.coolantVoiceAbove) {
            out += Alert(
                "coolant",
                "Coolant critical",
                100,
                "%.0fC for %.0fs threshold".format(c1, thresholds.coolantVoiceAbove),
            )
        }
        val c2v = snapshot.coolant2C
        if (c2v != null && c2v > thresholds.coolantVoiceAbove) {
            out += Alert("coolant2", "Coolant two critical", 95, "%.0fC".format(c2v))
        }

        val batt = HealthEvaluator.battery(
            snapshot.batteryVolts,
            running,
            thresholds,
            rpm = snapshot.rpm,
        )
        addCritical(
            "battery",
            "Battery critical",
            90,
            batt,
            "V=${snapshot.batteryVolts} rpm=${snapshot.rpm} ${batt.label}",
        )

        // ATF hot (elevated) still announced — transmission damage risk.
        val atf = HealthEvaluator.atfTemp(atfC, thresholds)
        when (atf.health) {
            Health.CRITICAL -> out += Alert("atf", "Transmission fluid critical", 85, atf.label)
            Health.ELEVATED -> out += Alert("atf_hot", "Transmission fluid hot", 84, atf.label)
            else -> {}
        }

        addCritical("rpm", "Engine RPM critical", 80, HealthEvaluator.rpm(snapshot.rpm, thresholds))

        // Fuel trims / timing / MAF / intake: UI colours only (no cabin voice).
        // Fuel OPEN/CLOSED LOOP never voiced (normal transient).

        return out.filter { AlertPolicy.mayVoice(it.key) }
            .sortedByDescending { it.priority }
    }
}
