package com.fb2.obd.obd

/**
 * Overlay live dashboard snapshot values onto probe results so pages like
 * Cold start / Fuel don't show n/s for PIDs the main loop already decoded.
 */
object LiveSnapshotOverlay {

    fun apply(results: List<PidProbeResult>, snapshot: VehicleSnapshot): List<PidProbeResult> {
        return results.map { r ->
            if (r.supported && r.sample != null) return@map r
            val live = liveValue(r.pid, snapshot) ?: return@map r
            PidProbeResult(r.pid, supported = true, sample = live, raw = r.raw ?: "from-live-dashboard")
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
        r.sample != null -> "%.2f %s".format(r.sample, r.pid.unit).trim()
        else -> "ok"
    }
}
