package com.fb2.obd.obd

/**
 * Computes engine + transmission health scores (0–100) from live snapshot and
 * optional Honda TCM fields. Missing TCM / core engine sensors are reported as
 * **insufficient data** — never as a false "100% healthy".
 *
 * Notes use plain diagnostic language (e.g. “Possible alternator issue”) rather
 * than only “Voltage Low”.
 */
object HealthScoreCalculator {

    fun compute(
        snapshot: VehicleSnapshot,
        storedDtcCount: Int = 0,
        atfC: Double? = null,
        tcSlipRpm: Double? = null,
        /** How many TCM / Mode 22 transmission PIDs answered on the last probe. */
        tcmSupportedCount: Int? = null,
        thresholds: HealthThresholds = HealthThresholds.DEFAULT,
    ): HealthScore {
        var engine = 100
        val eNotes = mutableListOf<String>()
        var trans = 100
        val tNotes = mutableListOf<String>()

        fun deductE(pts: Int, note: String) {
            engine = (engine - pts).coerceAtLeast(0)
            eNotes += note
        }

        fun deductT(pts: Int, note: String) {
            trans = (trans - pts).coerceAtLeast(0)
            tNotes += note
        }

        val hasRpm = snapshot.rpm != null
        val hasCoolant = snapshot.coolantC != null
        val hasBattery = snapshot.batteryVolts != null
        val engineDataOk = hasRpm && (hasCoolant || hasBattery)

        if (!hasRpm) eNotes += "RPM not available — engine score incomplete"
        if (!hasCoolant) eNotes += "Coolant not available"
        if (!hasBattery) eNotes += "Battery voltage not available"
        if (snapshot.ltftPct == null) eNotes += "LTFT n/s on this ECU (common on FB2)"

        when (HealthEvaluator.coolant(snapshot.coolantC, thresholds).health) {
            Health.WARN -> deductE(6, "Coolant warm — watch temperature")
            Health.ELEVATED -> deductE(14, "Engine running hot. Reduce load.")
            Health.CRITICAL -> deductE(28, "Engine overheating. Reduce load / pull over safely.")
            Health.COLD -> eNotes += "Engine still cold — open loop enrichment is normal"
            else -> {}
        }

        val running = (snapshot.rpm ?: 0.0) > 0
        val battStatus = HealthEvaluator.battery(snapshot.batteryVolts, running, thresholds)
        when (battStatus.health) {
            Health.WARN -> deductE(6, "Battery ${battStatus.label} — check charging system")
            Health.ELEVATED -> deductE(10, "Battery ${battStatus.label} — check alternator / battery")
            Health.CRITICAL -> deductE(22, "Possible alternator issue. Check charging system.")
            else -> {}
        }

        fun trimNote(which: String, status: MetricStatus) {
            when (status.health) {
                Health.WARN -> deductE(4, "$which ${status.label.lowercase()} — monitor")
                Health.ELEVATED -> deductE(10, "$which ${status.label}. Possible vacuum leak or fuel issue.")
                Health.CRITICAL -> deductE(18, "$which severely out of range. Possible vacuum leak / injector / sensor fault.")
                else -> {}
            }
        }
        trimNote("STFT", HealthEvaluator.fuelTrim(snapshot.stftPct, thresholds))
        trimNote("LTFT", HealthEvaluator.fuelTrim(snapshot.ltftPct, thresholds))

        when (HealthEvaluator.fuelSystem(snapshot.fuelSystemStatus, snapshot.coolantC).health) {
            Health.WARN -> deductE(5, "Open loop while warm — check O2 sensors / ECT / enrichment")
            else -> {}
        }

        when (HealthEvaluator.intakeAir(snapshot.intakeC, thresholds).health) {
            Health.CRITICAL -> deductE(8, "Intake air very hot — heat soak or IAT sensor")
            Health.WARN -> deductE(3, "Intake air warm")
            else -> {}
        }
        when (HealthEvaluator.timing(snapshot.timingAdvance, thresholds).health) {
            Health.CRITICAL -> deductE(10, "Ignition retarded — knock / timing issue possible")
            Health.WARN -> deductE(3, "Ignition advance low")
            else -> {}
        }

        if (storedDtcCount > 0) {
            deductE((storedDtcCount * 8).coerceAtMost(30), "$storedDtcCount stored DTC(s) — open DIAG → Faults")
            deductT((storedDtcCount * 4).coerceAtMost(20), "DTCs present")
        }

        val hasAtf = atfC != null
        val hasSlip = tcSlipRpm != null
        val tcmAnswered = (tcmSupportedCount ?: 0) > 0 || hasAtf || hasSlip
        val transmissionDataOk = tcmAnswered

        when (HealthEvaluator.atfTemp(atfC, thresholds).health) {
            Health.COLD -> tNotes += "ATF still cold / warming"
            Health.WARN -> deductT(8, "ATF warm — avoid heavy towing until cooler")
            Health.ELEVATED -> deductT(16, "ATF hot. Ease load; check cooler / fluid level.")
            Health.CRITICAL -> deductT(30, "ATF overheating — risk of transmission damage.")
            else -> {}
        }
        when (HealthEvaluator.tcSlip(tcSlipRpm, thresholds).health) {
            Health.WARN -> deductT(6, "Torque converter slipping more than usual")
            Health.CRITICAL -> deductT(14, "High torque-converter slip")
            else -> {}
        }

        if (!transmissionDataOk) {
            tNotes += "No TCM sensors answered (ATF / gear / slip n/s) — cannot score transmission yet"
            tNotes += "Run Transmission or Honda probe; Mode 22 IDs may need remapping for this FB2"
        }

        val badEngine = eNotes.any {
            it.contains("overheat", true) || it.contains("alternator", true) ||
                it.contains("DTC", true) || it.contains("severely", true) ||
                it.contains("hot", true) || it.contains("vacuum", true) ||
                it.contains("Charging", true)
        }
        if (engineDataOk && !badEngine) {
            if (eNotes.none { it.contains("incomplete", true) }) {
                eNotes += "Core engine parameters look OK from available sensors"
            }
        } else if (!engineDataOk) {
            eNotes += "Insufficient live sensors for a reliable engine score"
        }

        if (transmissionDataOk && tNotes.none {
                it.contains("hot", true) || it.contains("overheat", true) ||
                    it.contains("slip", true) || it.contains("DTC", true)
            }
        ) {
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
