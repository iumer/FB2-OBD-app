package com.fb2.obd.obd

/**
 * Computes engine + transmission health scores (0–100) from live snapshot and
 * optional Honda TCM fields. Missing TCM / core engine sensors are reported as
 * **insufficient data** — never as a false "100% healthy".
 */
object HealthScoreCalculator {

    fun compute(
        snapshot: VehicleSnapshot,
        storedDtcCount: Int = 0,
        atfC: Double? = null,
        tcSlipRpm: Double? = null,
        /** How many TCM / Mode 22 transmission PIDs answered on the last probe. */
        tcmSupportedCount: Int? = null,
    ): HealthScore {
        var engine = 100
        val eNotes = mutableListOf<String>()
        var trans = 100
        val tNotes = mutableListOf<String>()

        fun deductE(pts: Int, note: String) { engine = (engine - pts).coerceAtLeast(0); eNotes += note }
        fun deductT(pts: Int, note: String) { trans = (trans - pts).coerceAtLeast(0); tNotes += note }

        val hasRpm = snapshot.rpm != null
        val hasCoolant = snapshot.coolantC != null
        val hasBattery = snapshot.batteryVolts != null
        val engineDataOk = hasRpm && (hasCoolant || hasBattery)

        if (!hasRpm) eNotes += "RPM not available — engine score incomplete"
        if (!hasCoolant) eNotes += "Coolant not available"
        if (!hasBattery) eNotes += "Battery voltage not available"
        if (snapshot.ltftPct == null) eNotes += "LTFT n/s on this ECU (common on FB2)"

        when (HealthEvaluator.coolant(snapshot.coolantC)) {
            Health.WARN -> deductE(8, "Coolant elevated")
            Health.CRITICAL -> deductE(25, "Coolant critical")
            Health.UNKNOWN -> {}
            else -> {}
        }
        val running = (snapshot.rpm ?: 0.0) > 0
        when (HealthEvaluator.battery(snapshot.batteryVolts, running)) {
            Health.WARN -> deductE(6, "Charging voltage low/high")
            Health.CRITICAL -> deductE(20, "Battery/charging critical")
            Health.UNKNOWN -> {}
            else -> {}
        }
        when (HealthEvaluator.fuelTrim(snapshot.stftPct)) {
            Health.WARN -> deductE(5, "STFT elevated")
            Health.CRITICAL -> deductE(15, "STFT out of range")
            Health.UNKNOWN -> {}
            else -> {}
        }
        when (HealthEvaluator.fuelTrim(snapshot.ltftPct)) {
            Health.WARN -> deductE(5, "LTFT elevated")
            Health.CRITICAL -> deductE(15, "LTFT out of range")
            Health.UNKNOWN -> {}
            else -> {}
        }
        if (storedDtcCount > 0) {
            deductE((storedDtcCount * 8).coerceAtMost(30), "$storedDtcCount stored DTC(s)")
            deductT((storedDtcCount * 4).coerceAtMost(20), "DTCs present")
        }

        val hasAtf = atfC != null
        val hasSlip = tcSlipRpm != null
        val tcmAnswered = (tcmSupportedCount ?: 0) > 0 || hasAtf || hasSlip
        // If caller probed TCM and got zero hits, or never got ATF/slip, score is unknown.
        val transmissionDataOk = tcmAnswered

        when (HealthEvaluator.atfTemp(atfC)) {
            Health.WARN -> deductT(10, "ATF temp warm/cold")
            Health.CRITICAL -> deductT(30, "ATF temp critical")
            Health.UNKNOWN -> {}
            else -> {}
        }
        if (tcSlipRpm != null && tcSlipRpm > 250) {
            deductT(12, "High torque-converter slip")
        }

        if (!transmissionDataOk) {
            tNotes += "No TCM sensors answered (ATF / gear / slip n/s) — cannot score transmission yet"
            tNotes += "Run Transmission or Honda probe; Mode 22 IDs may need remapping for this FB2"
        }

        if (engineDataOk && eNotes.none { it.contains("elevated", true) || it.contains("critical", true) || it.contains("DTC", true) || it.contains("out of range", true) || it.contains("low/high", true) }) {
            // Keep informational notes; add healthy only if we didn't already add only soft notes
            if (eNotes.none { it.contains("incomplete", true) }) {
                eNotes += "Core engine parameters look OK from available sensors"
            }
        } else if (!engineDataOk) {
            eNotes += "Insufficient live sensors for a reliable engine score"
        }

        if (transmissionDataOk && tNotes.none { it.contains("elevated", true) || it.contains("critical", true) || it.contains("High torque", true) || it.contains("DTC", true) }) {
            tNotes += "Available transmission sensors look OK"
        }

        return HealthScore(
            enginePct = if (engineDataOk) engine else null,
            transmissionPct = if (transmissionDataOk) trans else null,
            engineNotes = eNotes.distinct(),
            transmissionNotes = tNotes.distinct(),
            engineDataOk = engineDataOk,
            transmissionDataOk = transmissionDataOk,
        )
    }
}
