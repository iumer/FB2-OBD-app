package com.fb2.obd.obd

/**
 * Overlay live dashboard snapshot values onto probe results so pages like
 * Cold start / Fuel don't show n/s for PIDs the main loop already decoded.
 */
object LiveSnapshotOverlay {

    fun apply(results: List<PidProbeResult>, snapshot: VehicleSnapshot): List<PidProbeResult> {
        return results.map { r ->
            if (r.supported && r.sample != null) return@map r
            if (r.pid.request.equals("0103", true) && !snapshot.fuelSystemStatus.isNullOrBlank()) {
                val status = snapshot.fuelSystemStatus
                // Encode CLOSED as 2.0 so formatDisplay can rebuild the label.
                val raw = when {
                    status.contains("CLOSED", true) -> 2.0
                    status.contains("OPEN", true) -> 1.0
                    else -> null
                }
                return@map PidProbeResult(r.pid, supported = true, sample = raw, raw = r.raw ?: "from-live-dashboard")
            }
            val live = liveValue(r.pid, snapshot) ?: return@map r
            PidProbeResult(r.pid, supported = true, sample = live, raw = r.raw ?: "from-live-dashboard")
        }
    }

    fun liveSample(pid: PidDefinition, snapshot: VehicleSnapshot): Double? = liveValue(pid, snapshot)

    fun formatLiveOrNs(pid: PidDefinition, snapshot: VehicleSnapshot, fallback: String? = null): String {
        if (pid.request.equals("0103", true)) {
            snapshot.fuelSystemStatus?.let { return it }
            fallback?.let { return it }
            return "—"
        }
        val v = liveValue(pid, snapshot)
        if (v != null) return "%.2f %s".format(v, pid.unit).trim()
        if (fallback != null) return fallback
        return if (pid.profile.startsWith("honda", ignoreCase = true) || pid.request.startsWith("22")) {
            "n/s — not on this ECU yet"
        } else {
            "—"
        }
    }

    private fun liveValue(pid: PidDefinition, s: VehicleSnapshot): Double? {
        // Match by Mode 01 request or common labels.
        return when (pid.request.uppercase()) {
            "010C" -> s.rpm
            "010D" -> s.speedKmh
            "0105" -> s.coolantC
            "0167" -> s.coolant2C
            "010F" -> s.intakeC
            "0146" -> s.ambientC
            "0104" -> s.engineLoadPct
            "0111" -> s.throttlePct
            "010E" -> s.timingAdvance
            "0110" -> s.mafGps
            "010B" -> s.mapKpa
            "0106" -> s.stftPct
            "0107" -> s.ltftPct
            "0142" -> s.batteryVolts
            "0103" -> null // text-only via fuelSystemStatus
            else -> when {
                pid.label.equals("RPM", true) -> s.rpm
                pid.label.equals("Speed", true) -> s.speedKmh
                pid.label.equals("Coolant temp", true) -> s.coolantC
                pid.label.equals("Coolant temp sensors", true) -> s.coolant2C
                pid.label.equals("Intake temp", true) -> s.intakeC
                pid.label.equals("Ambient air temp", true) -> s.ambientC
                pid.label.equals("Engine load", true) -> s.engineLoadPct
                pid.label.equals("Throttle", true) -> s.throttlePct
                pid.label.equals("Timing advance", true) -> s.timingAdvance
                pid.label.equals("MAF", true) -> s.mafGps
                pid.label.equals("MAP", true) -> s.mapKpa
                pid.label.startsWith("STFT Bank 1", true) -> s.stftPct
                pid.label.startsWith("LTFT Bank 1", true) -> s.ltftPct
                pid.label.contains("Control module voltage", true) -> s.batteryVolts
                else -> null
            }
        }
    }

    fun formatDisplay(r: PidProbeResult): String = when {
        !r.supported -> if (r.pid.profile.startsWith("honda", ignoreCase = true) || r.pid.request.startsWith("22")) {
            "n/s — not on this ECU yet"
        } else {
            "n/s"
        }
        r.pid.request.equals("0103", true) -> {
            val rawText = r.raw?.trim().orEmpty()
            when {
                rawText.contains("CLOSED", true) || rawText.contains("OPEN", true) -> rawText
                else -> FuelSystemDecoder.fromRawByte(r.sample) ?: "UNKNOWN"
            }
        }
        r.sample != null -> "%.2f %s".format(r.sample, r.pid.unit).trim()
        else -> "ok"
    }
}
