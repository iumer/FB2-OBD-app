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
        scanning: Boolean = false,
    ): SensorPickerReading {
        // Torque-style: a PID that already answered stays readable. Honda / clone
        // Mode 01 support bitmasks often omit MAP (010B) even while 010B is live.
        liveText(pid, snapshot)?.let { text ->
            return SensorPickerReading(SensorReadKind.LIVE, text)
        }

        extraValues[pid.id]?.let { raw ->
            val cleaned = raw.trim()
            if (cleaned.isNotEmpty() && !isNoDataText(cleaned)) {
                return SensorPickerReading(SensorReadKind.LIVE, cleaned)
            }
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

        val mode01 = pid.mode01Number
        if (mode01 != null && mode01 in snapshot.unsupportedPids) {
            // Connect-time bitmask often omits live PIDs (MAP, LTFT, …). While the
            // catalog scan is still running, stay "Waiting" — not "No data" — until
            // we actually probe. Torque probes anyway; it does not trust 0100 alone.
            return if (scanning) {
                SensorPickerReading(SensorReadKind.WAITING)
            } else {
                SensorPickerReading(SensorReadKind.NONE)
            }
        }

        return SensorPickerReading(SensorReadKind.WAITING)
    }

    /**
     * Once a row is LIVE, keep the last good value for this picker session.
     * Scan / TTL / bitmask / transient probe misses must not yank any PID off
     * Readable after the ECU answered (MAP, Coolant, Intake, …).
     */
    fun latch(previous: SensorPickerReading?, next: SensorPickerReading): SensorPickerReading {
        if (previous?.kind == SensorReadKind.LIVE && next.kind != SensorReadKind.LIVE) {
            return previous
        }
        return next
    }

    /**
     * Merge batch probe hits into the running picker scan. Never downgrade a PID
     * that already answered (including fuel-loop text rows with null sample).
     */
    fun mergeProbeResults(
        existing: Map<String, PidProbeResult>,
        expanded: Map<String, PidProbeResult>,
    ): Map<String, PidProbeResult> {
        if (expanded.isEmpty()) return existing
        val merged = existing.toMutableMap()
        expanded.forEach { (id, hit) ->
            val prev = merged[id]
            if (prev?.supported == true && !hit.supported) return@forEach
            merged[id] = hit
        }
        return merged
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

    /**
     * Apply one ELM response to every catalog row that shares the request
     * (e.g. 0124 lambda + 0124I current). Torque shows both from the same PID.
     */
    fun expandProbeHits(
        catalog: List<PidDefinition>,
        hits: List<PidProbeResult>,
    ): Map<String, PidProbeResult> {
        val out = LinkedHashMap<String, PidProbeResult>()
        hits.forEach { hit ->
            val siblings = catalog.filter { it.request.equals(hit.pid.request, true) }
            if (siblings.size <= 1) {
                out[hit.pid.id] = hit
                return@forEach
            }
            val bytes = hit.raw?.let { raw ->
                when {
                    hit.pid.request.startsWith("01") && hit.pid.request.length == 4 ->
                        ObdResponseParser.rawDataBytes(hit.pid.request, 8, raw)
                    else -> null
                }
            }
            siblings.forEach { pid ->
                val sample = if (hit.supported) bytes?.let(pid.decode) else null
                out[pid.id] = PidProbeResult(pid, hit.supported, sample, hit.raw)
            }
        }
        return out
    }

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
