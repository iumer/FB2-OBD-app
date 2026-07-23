package com.fb2.obd.obd

/**
 * Computes engine + transmission health scores (0–100) from live snapshot and
 * optional Honda TCM fields. Deducts for fuel trims, voltage, coolant, DTCs,
 * ATF temp, etc.
 */
object HealthScoreCalculator {

    fun compute(
        snapshot: VehicleSnapshot,
        storedDtcCount: Int = 0,
        atfC: Double? = null,
        tcSlipRpm: Double? = null,
    ): HealthScore {
        var engine = 100
        val eNotes = mutableListOf<String>()
        var trans = 100
        val tNotes = mutableListOf<String>()

        fun deductE(pts: Int, note: String) { engine = (engine - pts).coerceAtLeast(0); eNotes += note }
        fun deductT(pts: Int, note: String) { trans = (trans - pts).coerceAtLeast(0); tNotes += note }

        when (HealthEvaluator.coolant(snapshot.coolantC)) {
            Health.WARN -> deductE(8, "Coolant elevated")
            Health.CRITICAL -> deductE(25, "Coolant critical")
            else -> {}
        }
        val running = (snapshot.rpm ?: 0.0) > 0
        when (HealthEvaluator.battery(snapshot.batteryVolts, running)) {
            Health.WARN -> deductE(6, "Charging voltage low/high")
            Health.CRITICAL -> deductE(20, "Battery/charging critical")
            else -> {}
        }
        when (HealthEvaluator.fuelTrim(snapshot.stftPct)) {
            Health.WARN -> deductE(5, "STFT elevated")
            Health.CRITICAL -> deductE(15, "STFT out of range")
            else -> {}
        }
        when (HealthEvaluator.fuelTrim(snapshot.ltftPct)) {
            Health.WARN -> deductE(5, "LTFT elevated")
            Health.CRITICAL -> deductE(15, "LTFT out of range")
            else -> {}
        }
        if (storedDtcCount > 0) {
            deductE((storedDtcCount * 8).coerceAtMost(30), "$storedDtcCount stored DTC(s)")
            deductT((storedDtcCount * 4).coerceAtMost(20), "DTCs present")
        }

        when (HealthEvaluator.atfTemp(atfC)) {
            Health.WARN -> deductT(10, "ATF temp warm/cold")
            Health.CRITICAL -> deductT(30, "ATF temp critical")
            else -> {}
        }
        if (tcSlipRpm != null && tcSlipRpm > 250) {
            deductT(12, "High torque-converter slip")
        }

        if (eNotes.isEmpty()) eNotes += "Engine parameters look healthy"
        if (tNotes.isEmpty()) tNotes += "Transmission parameters look healthy"

        return HealthScore(engine, trans, eNotes, tNotes)
    }
}
