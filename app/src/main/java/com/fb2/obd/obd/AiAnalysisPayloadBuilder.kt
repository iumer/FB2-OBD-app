package com.fb2.obd.obd

/**
 * Builds the OpenAI chat payload for FB2 Civic diagnostic analysis.
 * Pure / Android-free for unit tests.
 */
object AiAnalysisPayloadBuilder {

    const val DEFAULT_WINDOW_MINUTES = 5
    const val MIN_WINDOW_MINUTES = 1
    const val MAX_WINDOW_MINUTES = 15
    const val MAX_SNAPSHOT_ROWS = 150
    const val MAX_PAYLOAD_CHARS = 50_000

    val SYSTEM_PROMPT = """
You are a read-only automotive diagnostic assistant for a Honda Civic FB2 (9th gen).

Vehicle context (always assume this car):
- Model: Honda Civic 2013 1.8L R18, Pakistani full-option UG variant
- Transmission: automatic with D / D3 / D2 / D1
- Honda Electronic Load Detector (ELD) intentionally varies charging voltage at idle/light load — do not treat brief ~12.8–13.4 V at idle as an alternator failure by itself
- OPEN LOOP ↔ CLOSED LOOP transitions are often normal (cold start, accel, decel, fuel cut)

Rules:
- READ ONLY. Never recommend clearing DTCs, writing PIDs, or changing ECU settings from this app.
- Prefer evidence from the time window over a single sample.
- Distinguish normal Honda/FB2 behaviour from likely faults.
- If data is thin or the window is short, say confidence is limited.

Output format — respond ONLY as a plain-text diagnostic report suitable to save as a .txt file:
1) SUMMARY (2–4 sentences)
2) WHAT LOOKS OK
3) CONCERNS / LIKELY ISSUES (with evidence: values + time behaviour)
4) SUGGESTED NEXT CHECKS (safe, read-only)
5) NOTES FOR CONTINUING IN CHATGPT (what to paste / ask next)

Do not use markdown tables. Use plain headings and bullet lines.
""".trimIndent()

    data class TruncatedLog(
        val csvText: String,
        val rowCount: Int,
        val eventCount: Int,
        val limited: Boolean,
        val windowMinutesUsed: Int,
    )

    data class Payload(
        val systemPrompt: String = SYSTEM_PROMPT,
        val userMessage: String,
        val windowMinutes: Int,
        val sampleCount: Int,
        val limited: Boolean,
        val sourceLabel: String,
    )

    fun clampWindowMinutes(minutes: Int): Int =
        minutes.coerceIn(MIN_WINDOW_MINUTES, MAX_WINDOW_MINUTES)

    /**
     * Keep rows with [timestampMs] within the last [windowMinutes], newest first,
     * capped by [MAX_SNAPSHOT_ROWS] and [MAX_PAYLOAD_CHARS].
     */
    fun truncateByTime(
        snapshotLines: List<Pair<Long, String>>,
        eventLines: List<Pair<Long, String>>,
        windowMinutes: Int,
        nowMs: Long,
        maxRows: Int = MAX_SNAPSHOT_ROWS,
        maxChars: Int = MAX_PAYLOAD_CHARS,
    ): TruncatedLog {
        val mins = clampWindowMinutes(windowMinutes)
        val cutoff = nowMs - mins * 60_000L
        val snaps = snapshotLines.filter { it.first >= cutoff }.takeLast(maxRows)
        val evs = eventLines.filter { it.first >= cutoff }
        var limited = snapshotLines.size > snaps.size ||
            snapshotLines.any { it.first < cutoff } ||
            snaps.size >= maxRows

        fun pack(sn: List<Pair<Long, String>>, ev: List<Pair<Long, String>>): String {
            return buildString {
                appendLine("# events")
                appendLine("time_ms,category,message")
                ev.forEach { appendLine(it.second) }
                appendLine()
                appendLine("# dashboard_snapshots")
                appendLine(
                    "time_ms,rpm,speed_kmh,coolant1_c,coolant2_c,intake_c,ambient_c," +
                        "load_pct,throttle_pct,timing,maf_gps,map_kpa,stft_pct,ltft_pct,ecu_v,gear,fuel_loop",
                )
                sn.forEach { appendLine(it.second) }
            }
        }

        var csv = pack(snaps, evs)
        var usedSnaps = snaps
        var usedEvs = evs
        while (csv.length > maxChars && usedSnaps.size > 10) {
            limited = true
            usedSnaps = usedSnaps.takeLast((usedSnaps.size * 2) / 3)
            val newCutoff = usedSnaps.firstOrNull()?.first ?: cutoff
            usedEvs = usedEvs.filter { it.first >= newCutoff }
            csv = pack(usedSnaps, usedEvs)
        }
        if (csv.length > maxChars) {
            limited = true
            csv = csv.take(maxChars) + "\n# …truncated…\n"
        }
        return TruncatedLog(
            csvText = csv,
            rowCount = usedSnaps.size,
            eventCount = usedEvs.size,
            limited = limited,
            windowMinutesUsed = mins,
        )
    }

    /**
     * Parse a saved FB2 session CSV and keep the last [windowMinutes] of snapshot rows
     * (by time_ms), falling back to last [MAX_SNAPSHOT_ROWS] rows if timestamps are missing.
     */
    fun truncateSavedCsv(
        fullCsv: String,
        windowMinutes: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): TruncatedLog {
        val mins = clampWindowMinutes(windowMinutes)
        val snaps = mutableListOf<Pair<Long, String>>()
        val evs = mutableListOf<Pair<Long, String>>()
        var section = ""
        for (raw in fullCsv.lineSequence()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("# events") -> section = "events"
                line.startsWith("# dashboard_snapshots") -> section = "snaps"
                line.startsWith("# dash_tiles") -> section = "tiles"
                line.startsWith("#") || line.isBlank() -> Unit
                line.startsWith("time_ms") -> Unit
                section == "events" -> {
                    val t = line.substringBefore(',').toLongOrNull() ?: continue
                    evs += t to line
                }
                section == "snaps" -> {
                    val t = line.substringBefore(',').toLongOrNull() ?: continue
                    snaps += t to line
                }
            }
        }
        if (snaps.isEmpty() && evs.isEmpty()) {
            val clipped = if (fullCsv.length > MAX_PAYLOAD_CHARS) {
                fullCsv.take(MAX_PAYLOAD_CHARS) + "\n# …truncated…\n"
            } else {
                fullCsv
            }
            return TruncatedLog(clipped, 0, 0, fullCsv.length > MAX_PAYLOAD_CHARS, mins)
        }
        val endMs = snaps.lastOrNull()?.first ?: evs.lastOrNull()?.first ?: nowMs
        return truncateByTime(snaps, evs, mins, endMs)
    }

    fun formatSnapshot(s: VehicleSnapshot): String = buildString {
        appendLine("rpm=${s.rpm}")
        appendLine("speed_kmh=${s.speedKmh}")
        appendLine("coolant1_c=${s.coolantC}")
        appendLine("coolant2_c=${s.coolant2C}")
        appendLine("intake_c=${s.intakeC}")
        appendLine("ambient_c=${s.ambientC}")
        appendLine("load_pct=${s.engineLoadPct}")
        appendLine("throttle_pct=${s.throttlePct}")
        appendLine("timing=${s.timingAdvance}")
        appendLine("maf_gps=${s.mafGps}")
        appendLine("map_kpa=${s.mapKpa}")
        appendLine("stft_pct=${s.stftPct}")
        appendLine("ltft_pct=${s.ltftPct}")
        appendLine("battery_v=${s.batteryVolts}")
        appendLine("fuel_loop=${s.fuelSystemStatus}")
        appendLine("gear=${s.gear} source=${s.gearSource}")
    }

    fun formatHealth(h: HealthScore?): String {
        if (h == null) return "(no health score yet)"
        return buildString {
            appendLine("engine_pct=${h.enginePct} transmission_pct=${h.transmissionPct} vehicle_pct=${h.vehiclePct}")
            appendLine("engine_ok=${h.engineDataOk} transmission_ok=${h.transmissionDataOk}")
            appendLine("engine_notes:")
            h.engineNotes.forEach { appendLine("- $it") }
            appendLine("transmission_notes:")
            h.transmissionNotes.forEach { appendLine("- $it") }
        }
    }

    fun buildUserMessage(
        sourceLabel: String,
        windowMinutes: Int,
        snapshotText: String,
        healthText: String,
        dtcText: String,
        log: TruncatedLog,
    ): Payload {
        val limitedNote = if (log.limited) {
            "NOTE: Log window was truncated for size/time. Confidence may be limited.\n"
        } else {
            ""
        }
        val thinNote = if (log.rowCount < 5) {
            "NOTE: Few snapshot rows (${log.rowCount}) in this window — report may be limited. Prefer driving with LOG on.\n"
        } else {
            ""
        }
        val user = buildString {
            appendLine("Analyze this Honda Civic FB2 session.")
            appendLine("Source: $sourceLabel")
            appendLine("Requested window: ${clampWindowMinutes(windowMinutes)} minutes")
            appendLine("Samples in window: ${log.rowCount} snapshots, ${log.eventCount} events")
            append(limitedNote)
            append(thinNote)
            appendLine()
            appendLine("=== LATEST SNAPSHOT ===")
            appendLine(snapshotText.trim())
            appendLine()
            appendLine("=== APP HEALTH NOTES ===")
            appendLine(healthText.trim())
            appendLine()
            appendLine("=== DTC LIST ===")
            appendLine(dtcText.trim().ifBlank { "(none reported)" })
            appendLine()
            appendLine("=== LOG WINDOW (CSV) ===")
            append(log.csvText.trim())
            appendLine()
        }
        return Payload(
            userMessage = user,
            windowMinutes = log.windowMinutesUsed,
            sampleCount = log.rowCount,
            limited = log.limited || log.rowCount < 5,
            sourceLabel = sourceLabel,
        )
    }

    fun snapshotCsvLine(timestampMs: Long, s: VehicleSnapshot): String =
        listOf(
            timestampMs, s.rpm, s.speedKmh, s.coolantC, s.coolant2C, s.intakeC,
            s.ambientC, s.engineLoadPct, s.throttlePct, s.timingAdvance, s.mafGps,
            s.mapKpa, s.stftPct, s.ltftPct, s.batteryVolts, s.gear, s.fuelSystemStatus,
        ).joinToString(",") { it?.toString() ?: "" }
}
