package com.fb2.obd.obd

/**
 * Pure decision logic for spoken alarms. Returns phrases that should be spoken
 * when a metric newly enters an alarm band (or re-enters after cooldown, handled
 * by [com.fb2.obd.data.VoiceAlerter]).
 */
object VoiceAlertRules {

    data class Alert(val key: String, val phrase: String, val priority: Int)

    /**
     * Evaluate live snapshot (and optional ATF) against thresholds.
     * Only CRITICAL (and coolant/ATF ELEVATED “hot”) produce alerts.
     */
    fun evaluate(
        snapshot: VehicleSnapshot,
        thresholds: HealthThresholds = HealthThresholds.DEFAULT,
        atfC: Double? = null,
        tcSlipRpm: Double? = null,
    ): List<Alert> {
        val running = (snapshot.rpm ?: 0.0) > 0.0
        val out = mutableListOf<Alert>()

        fun add(key: String, phrase: String, priority: Int, status: MetricStatus) {
            when (status.health) {
                Health.CRITICAL -> out += Alert(key, phrase, priority)
                Health.ELEVATED -> {
                    // Speak orange “hot” for temps; also warn on weak battery
                    // (often looks red-ish on HU before it hits CRITICAL).
                    when (key) {
                        "coolant", "atf" ->
                            out += Alert("${key}_hot", phrase.replace("critical", "hot"), priority - 1)
                        "battery" ->
                            out += Alert("battery_low", "Battery low", priority - 2)
                    }
                }
                else -> {}
            }
        }

        // Spec: voice alert only above coolantVoiceAbove (default 110°C), not at first red.
        val c1 = snapshot.coolantC
        if (c1 != null && c1 > thresholds.coolantVoiceAbove) {
            out += Alert("coolant", "Coolant critical", 100)
        }
        val c2v = snapshot.coolant2C
        if (c2v != null && c2v > thresholds.coolantVoiceAbove) {
            out += Alert("coolant2", "Coolant two critical", 95)
        }

        add(
            "battery",
            "Battery critical",
            90,
            HealthEvaluator.battery(snapshot.batteryVolts, running, thresholds),
        )

        add("stft", "Short term fuel trim critical", 70, HealthEvaluator.fuelTrim(snapshot.stftPct, thresholds))
        add("ltft", "Long term fuel trim critical", 70, HealthEvaluator.fuelTrim(snapshot.ltftPct, thresholds))
        add("intake", "Intake temperature critical", 65, HealthEvaluator.intakeAir(snapshot.intakeC, thresholds))
        add(
            "maf",
            "Mass air flow critical",
            55,
            HealthEvaluator.maf(
                snapshot.mafGps,
                snapshot.rpm,
                snapshot.speedKmh,
                snapshot.throttlePct,
                thresholds,
            ),
        )
        add("timing", "Ignition timing critical", 50, HealthEvaluator.timing(snapshot.timingAdvance, thresholds))
        add("rpm", "Engine RPM critical", 80, HealthEvaluator.rpm(snapshot.rpm, thresholds))
        add("atf", "Transmission fluid critical", 85, HealthEvaluator.atfTemp(atfC, thresholds))
        add("slip", "Torque converter slip critical", 75, HealthEvaluator.tcSlip(tcSlipRpm, thresholds))

        return out.sortedByDescending { it.priority }
    }
}
