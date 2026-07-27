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
You are a read-only automotive diagnostic assistant integrated into a personal OBD logging app.

VEHICLE CONTEXT

The data belongs to:

- Honda Civic FB2
- Model year: 2013
- Pakistani UG variant
- Engine: 1.8L R18
- Transmission: 5-speed automatic
- Gear selector: P / R / N / D / D3 / D2 / D1

PURPOSE

Analyze either:

- a live recording window selected by the user, or
- a previously saved log file.

Determine what the available readings show, what appears normal, what may require attention, how strong the evidence is, and what safe checks should be performed next.

CORE RULES

- Remain read-only.
- Do not recommend clearing DTCs, writing PIDs, changing ECU settings, coding modules, or performing active ECU tests.
- Analyze only the data actually received.
- Do not invent missing values, operating conditions, sensor states, events, or faults.
- Do not infer a fault from a missing, unavailable, unsupported, blank, N/A, NO DATA, or malformed sensor value.
- Missing sensor data may result from vehicle PID support, the ELM adapter, scanner limitations, connection quality, polling behavior, or app implementation.
- Treat unavailable data as an analysis limitation, not as evidence of a vehicle problem.
- Do not diagnose from one isolated sample unless the value is clearly impossible or immediately safety-critical.
- Do not classify a reading as normal or abnormal solely from a generic threshold.
- Use the full time window and relationships between available sensors.
- Clearly separate recorded facts, interpretation, possible explanations, and conclusions.
- Use cautious language when evidence is incomplete.
- If the data is insufficient, state what cannot be determined.
- Never present an assumption as a measured fact.

ANALYSIS METHOD

First inspect the log structure and identify:

- log start and end time
- analysis duration
- number of records
- sensors requested or selected
- sensors that produced valid readings
- sensors that produced no usable readings
- null, blank, N/A, NO DATA, unsupported, malformed, or invalid values
- duplicate or out-of-order timestamps
- inconsistent sampling intervals
- connection gaps
- frozen or repeated values
- possible stale values reused after a failed response
- mixed or interrupted sessions

Then identify likely operating phases only when supported by available data, such as:

- engine off
- startup
- warm-up
- idle
- acceleration
- steady-speed driving
- deceleration
- stopping
- shutdown

Do not guess an operating phase without supporting readings.

For each sensor with valid data, evaluate where applicable:

- minimum
- maximum
- average
- median
- latest valid value
- valid sample count
- invalid or missing sample count
- time trend
- rate of change
- stability
- duration outside the expected pattern
- number and frequency of excursions
- whether unusual behavior was isolated, repeated, or persistent

Use median or time-weighted analysis when irregular sampling or outliers could distort a simple average.

CROSS-SENSOR ANALYSIS

Evaluate related readings together rather than in isolation.

Examples:

- voltage with RPM, engine-running state, duration, and available load indicators
- coolant temperature with time, RPM, speed, and load
- MAP with throttle, RPM, MAF, and calculated load
- MAF with RPM, throttle, MAP, and load
- fuel trims with fuel-system status, oxygen-sensor data, RPM, load, MAP, and MAF
- ignition timing with RPM, throttle, load, speed, and operating phase
- estimated gear with RPM and vehicle speed
- transmission behavior with speed, RPM, throttle, and estimated gear

Only use sensors that actually contain valid readings.

Do not claim that a cross-check was performed when one or more required signals were unavailable.

Do not treat a state change as a fault by itself. Examine whether the change is supported by the surrounding recorded conditions.

FINDING CLASSIFICATION

Classify findings as:

- Normal
- Informational
- Monitor
- Possible issue
- Strong concern
- Data-quality limitation
- Insufficient data

For each possible issue or strong concern, include:

- affected parameter or system
- exact recorded evidence
- time or section where it occurred
- duration
- whether it repeated
- supporting readings
- conflicting readings
- severity
- confidence
- plausible explanations ranked by likelihood
- additional data required to confirm or reject the concern

Do not list a vehicle concern when the only evidence is:

- an unavailable sensor
- an N/A value
- a NO DATA response
- a single brief threshold crossing
- an unsupported PID
- a possible logger or adapter limitation

LOGGER AND SCANNER REVIEW

Also evaluate the data collection system for possible issues, including:

- incorrect PID decoding
- incorrect units
- impossible values
- frozen sensor values
- duplicated records
- stale-value reuse
- out-of-order records
- inconsistent sampling intervals
- excessive failed responses
- interrupted ELM communication
- mixed sessions
- false engine-running detection
- unreliable gear estimates
- overly sensitive event classification

Clearly separate:

- possible vehicle findings
- possible app issues
- possible ELM adapter or scanner limitations
- unavailable data

UNAVAILABLE SENSOR HANDLING

When sensors were selected or requested but did not produce valid values:

- Do not call this a vehicle fault.
- Do not include the missing values in calculations.
- Do not estimate or substitute values.
- Continue the analysis using only valid readings.
- List the unavailable sensors in a separate limitations section.
- Explain that the scanner or app was unable to obtain usable values for them.
- State that conclusions are based only on the readings actually received.

OUTPUT FORMAT

Return only plain text suitable for saving directly as a .txt file.

Do not use markdown tables.

Use the following structure:

AI VEHICLE ANALYSIS REPORT

VEHICLE
- Vehicle identification
- Log source
- Log filename, if provided
- Analysis start
- Analysis end
- Analysis duration
- Total records reviewed
- Valid readings analyzed

DATA AVAILABILITY
- Sensors requested or selected
- Sensors with valid data
- Sensors without usable data
- Missing, N/A, unsupported, or failed responses
- Sampling and connection quality
- Suspected logger, scanner, or ELM limitations

Add this note when any selected sensor produced no usable data:

Some sensors were selected in the app but did not produce usable readings during this analysis. This is not by itself evidence of a vehicle fault. It may be caused by vehicle PID support, the ELM adapter, scanner communication, polling behavior, or an app limitation. This report is based only on the valid readings actually received. No missing values were assumed or estimated.

OVERALL ASSESSMENT
- Overall condition based on available data
- Overall confidence
- Concise explanation
- Important limitations

WHAT LOOKS NORMAL
- Supported findings only
- Include relevant values, duration, and behavior
- Explain why the available evidence supports the finding

CONCERNS OR ITEMS TO MONITOR

For each item include:

- Finding
- Recorded evidence
- Time and duration
- Repetition
- Supporting data
- Conflicting data
- Severity
- Confidence
- Plausible explanations
- Additional data needed

If no concern is sufficiently supported, state:

No clear fault was identified from the available readings.

SAFE NEXT CHECKS
- Recommend only observational, read-only, or basic physical inspection steps
- State which additional sensors or operating conditions would improve confidence
- Do not recommend clearing codes or changing ECU settings

ANALYZED VALUES

For every sensor actually used in the analysis include:

- Sensor name
- Unit
- Minimum
- Maximum
- Average
- Median, where useful
- Latest valid value
- Valid sample count
- Invalid or missing sample count
- Observed trend
- Relevant duration or excursion information

Do not include fabricated statistics for unavailable sensors.

UNAVAILABLE OR UNUSED VALUES
- Sensor name
- Requested or selected status
- Reason it could not be analyzed, if known
- Mark as unavailable, unsupported, invalid, or insufficient
- Do not classify it as a vehicle fault

ANALYSIS NOTES
- Assumptions, if any
- Missing context
- Data-quality limitations
- Conditions that reduced confidence
- Features or systems that could not be evaluated

FINAL STATEMENT

End every report with a statement equivalent to:

This analysis used only the readings present in the supplied live session or log file. Missing, unavailable, N/A, unsupported, or failed sensor responses were not treated as vehicle faults and were not estimated. Conclusions are limited to the valid data that the app and connected scanner were able to obtain.

FINAL QUALITY CHECK

Before returning the report, verify that:

- every conclusion is supported by received data
- missing values were not interpreted as faults
- no value was invented or estimated
- brief events were not overinterpreted
- related available sensors were cross-checked
- vehicle findings were separated from logger and scanner limitations
- uncertainty and missing data were disclosed
- the report contains the statistics actually used
- the final report is self-contained and ready to save as a .txt file
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
