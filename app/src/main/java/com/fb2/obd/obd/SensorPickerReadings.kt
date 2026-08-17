package com.fb2.obd.obd

/**
 * Torque-style sensor picker row state: green + live value when the ECU/ELM
 * actually answered, otherwise waiting / no-data. Pure Kotlin so JVM tests
 * can lock the colour rules without Compose.
 */
enum class SensorReadKind {
    LIVE,
    WAITING,
    NONE,
}

data class SensorPickerReading(
    val kind: SensorReadKind,
    /** Engineering value + unit, e.g. `711 rpm`. Null when not live. */
    val latest: String? = null,
) {
    val subtitle: String = when (kind) {
        SensorReadKind.LIVE -> "Latest value: ${latest ?: "—"}"
        SensorReadKind.WAITING -> "Waiting for data"
        SensorReadKind.NONE -> "No data received"
    }

    val isReadable: Boolean get() = kind == SensorReadKind.LIVE
}

object SensorPickerReadings {

    fun categoryLabel(cat: PidCategory): String = when (cat) {
        PidCategory.ENGINE -> "Engine"
        PidCategory.FUEL -> "Fuel"
        PidCategory.TEMPS -> "Temperatures"
        PidCategory.AIR -> "Air / Intake"
        PidCategory.ELECTRICAL -> "Electrical"
        PidCategory.EMISSIONS -> "Emissions"
        PidCategory.TRANSMISSION -> "Transmission"
        PidCategory.ABS -> "ABS / Brakes"
        PidCategory.EPS -> "Steering (EPS)"
        PidCategory.SRS -> "SRS / Airbags"
        PidCategory.BODY -> "Body"
        PidCategory.CLIMATE -> "HVAC / Climate"
        PidCategory.TPMS -> "Tire pressure"
        PidCategory.OTHER -> "Other"
    }

    fun resolve(
        pid: PidDefinition,
        snapshot: VehicleSnapshot,
        probeById: Map<String, PidProbeResult>,
        extraValues: Map<String, String> = emptyMap(),
    ): SensorPickerReading {
        // Live Dash / ATRV wins over the ECU support bitmask. FB2 omits 0142
        // (battery) from Mode 01 PID 00 yet ATRV still returns volts — picker
        // used to paint that as "No data received".
        liveText(pid, snapshot)?.let { text ->
            return SensorPickerReading(SensorReadKind.LIVE, text)
        }

        extraValues[pid.id]?.let { raw ->
            val cleaned = raw.trim()
            if (cleaned.isNotEmpty() && !isNoDataText(cleaned)) {
                return SensorPickerReading(SensorReadKind.LIVE, cleaned)
            }
        }

        val mode01 = pid.mode01Number
        if (mode01 != null && mode01 in snapshot.unsupportedPids) {
            return SensorPickerReading(SensorReadKind.NONE)
        }

        val probed = probeById[pid.id] ?: probeById.entries.firstOrNull { (k, _) ->
            k.equals(pid.id, true) || k.equals(pid.request, true)
        }?.value
        if (probed != null) {
            if (probed.supported) {
                val text = LiveSnapshotOverlay.formatDisplay(probed)
                return if (isNoDataText(text)) {
                    SensorPickerReading(SensorReadKind.NONE)
                } else {
                    SensorPickerReading(SensorReadKind.LIVE, text)
                }
            }
            return SensorPickerReading(SensorReadKind.NONE)
        }

        return SensorPickerReading(SensorReadKind.WAITING)
    }

    fun matchesQuery(pid: PidDefinition, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return pid.label.contains(q, true) ||
            pid.request.contains(q, true) ||
            pid.id.contains(q, true) ||
            pid.unit.contains(q, true) ||
            categoryLabel(pid.category).contains(q, true)
    }

    fun parseSaeSupport(base: Int, raw: String): Set<Int> {
        val req = "01%02X".format(base)
        val bytes = ObdResponseParser.rawDataBytes(req, 4, raw) ?: return emptySet()
        return SupportedPids.fromBitmask(base, bytes)
    }

    /** True when [pidNumber] falls in a Mode 01 support block we actually decoded. */
    fun pidInCoveredSupportBlock(pidNumber: Int, coveredBases: Set<Int>): Boolean {
        val base = (pidNumber - 1) and 0xE0
        return base in coveredBases
    }

    val SAE_SUPPORT_BASES: List<Int> = listOf(0x00, 0x20, 0x40, 0x60, 0x80, 0xA0)

    private fun liveText(pid: PidDefinition, snapshot: VehicleSnapshot): String? {
        if (pid.request.equals("0103", true)) {
            return snapshot.fuelSystemStatus?.takeIf { it.isNotBlank() }
        }
        val sample = LiveSnapshotOverlay.liveSample(pid, snapshot) ?: return null
        return "%.2f %s".format(sample, pid.unit).trim()
    }

    private fun isNoDataText(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t == "—" || t == "--" || t == "n/s") return true
        val up = t.uppercase()
        return up.startsWith("N/S") || up.startsWith("NO DATA") || up == "UNSUPPORTED"
    }
}
