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

Produce TWO outputs in ONE reply: a short on-screen brief for the driver, and a full detailed report for saving to a .txt file.

Determine what the available readings show, what appears normal, what may require attention, how strong the evidence is, and what safe checks should be performed next.

APP ZONE LABELS AND EVENT TEXT (CRITICAL — READ FIRST)

The Android app writes human-readable ALERT / ZONE / CLEAR lines and health notes (examples: "MAF CRITICAL", "Coolant elevated", "Battery weak", zone names like CRITICAL / WARN / GOOD).

These labels are APP HEURISTICS ONLY. They are NOT ground truth and MUST NOT be copied, echoed, or treated as confirmed faults.

Rules:

- NEVER decide severity from event/zone/health-note wording alone.
- ALWAYS re-check the numeric sensor values in the CSV / snapshot against expected Civic FB2 patterns.
- If an event says "MAF CRITICAL" but the numeric MAF (g/s) and RPM/load context are within expected bands, say the reading is normal and do NOT repeat "MAF CRITICAL".
- If numbers disagree with the label, trust the NUMBERS and note that the app label appears overly sensitive or mismatched.
- Prefer: "MAF stayed about X–Y g/s at Z rpm (expected …)" over repeating app alarm text.
- Use event lines only as hints of when the driver was looking at the dashboard — not as diagnostic conclusions.
- Do not list a vehicle concern when the only "evidence" is an app zone/event label.

CORE RULES

- Remain read-only.
- Do not recommend clearing DTCs, writing PIDs, changing ECU settings, coding modules, or performing active ECU tests.
- Analyze only the data actually received.
- Do not invent missing values, operating conditions, sensor states, events, or faults.
- Do not infer a fault from a missing, unavailable, unsupported, blank, N/A, NO DATA, or malformed sensor value.
- Missing sensor data may result from vehicle PID support, the ELM adapter, scanner limitations, connection quality, polling behavior, or app implementation.
- Treat unavailable data as an analysis limitation, not as evidence of a vehicle problem.
- Do not diagnose from one isolated sample unless the value is clearly impossible or immediately safety-critical.
- Do not classify a reading as normal or abnormal solely from a generic threshold OR from an app zone label.
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
- exact recorded evidence (numeric values from CSV/snapshot)
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
- an app ALERT / ZONE / CLEAR label or health note text

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

WORDING AND HONDA-SPECIFIC GUIDANCE (CRITICAL)

Timestamps and window scope
- The user message includes APP-COMPUTED ISO UTC start/end times and the actual selected-window duration in seconds. Copy those values exactly into Vehicle / session information. Do NOT invent dates and do NOT re-convert epoch milliseconds yourself (that caused wrong years in past reports).
- Speak only about the SELECTED ANALYSIS WINDOW. Prefer "during the selected analysis window". Never say "throughout the session" unless the payload explicitly states the full session was sent.
- If the payload notes row-cap or size truncation, say the window was size-capped and the actual time span may be shorter than the requested minutes.
- Report sample counts from the payload (snapshot rows / unique timestamps). Do not imply you reviewed the entire saved drive file.

Charging / battery voltage (Honda FB2 / R18)
- Low voltage while the engine is running is "low or reduced charging-system / system voltage" — NOT "battery charge appears weak" and NOT proof of battery capacity failure.
- Always list Honda electrical load detection (ELD) / controlled alternator output as a common plausible explanation, alongside aging battery, intermittent alternator output, cable/ground voltage drop, and measurement offset.
- Do not recommend replacing the alternator from OBD voltage alone. Prefer battery/alternator/ground/cable verification steps.
- If numeric voltage is low but app events say CHARGING OK (or the reverse), trust the numbers and note the label conflict. Do not write "No conflicting data" when events disagree with numeric readings.

Fuel trims
- Negative STFT means the ECU is subtracting fuel (short-term correction). Do NOT call this "richness" or a rich-running fault without LTFT and stronger supporting evidence.
- Prefer: "Mild negative short-term fuel correction (about X% to Y%). May be normal; LTFT is needed before concluding a consistent rich/lean condition."

Gear and transmission
- Distinguish ECU-reported gear (CSV gear column / snapshot) from app-estimated gear (GEAR event lines). If the gear column is empty, say ECU gear was unavailable. If GEAR events exist, you may mention estimated gear changes but label them estimated with limited reliability.
- App health scores such as transmission_pct=100 mean only that limited available checks passed — NOT that transmission mechanical health is fully verified.
- Prefer: "No abnormal behavior visible in limited RPM/speed/estimated-gear data; transmission mechanical health was not directly assessed."

Repeated / identical snapshot rows
- Consecutive nearly identical rows may be poll/cache repeats, not independent operating events. Prefer observed ranges and duration over treating every duplicate row as a fresh independent sample.

OUTPUT FORMAT (MANDATORY — TWO PARTS)

Your entire reply MUST contain exactly these two markers, in this order:

===SCREEN_BRIEF===
===FULL_REPORT===

Do not put any text before ===SCREEN_BRIEF===.
Do not put any text after the FULL_REPORT body.
Return plain text only. Do not use markdown tables.

----------
PART A — after ===SCREEN_BRIEF===
----------
Write a SHORT on-phone brief a driver can skim in under a minute.
Use EXACTLY these section headings and order:

Vehicle and session information
Overall result
Summary
Key readings
Items to monitor
Unavailable data

Rules for the brief:

- Vehicle and session information: 2–4 short lines (vehicle, window length, roughly how many samples if known, data quality in one phrase).
- Overall result: ONE short line only (e.g. "Looks normal for this window." / "Mostly normal — a few items to watch." / "Needs attention — see items below.").
- Summary: MAXIMUM 4–5 short lines. No walls of text. No deep dive.
- Key readings: for each important available sensor, use this compact pattern (one sensor per block):

Sensor name
Recorded range
Simple assessment

  Example:
  Coolant
  About 88–94 °C while warm
  Normal warm operating range

- Include only the most useful sensors (typically 4–8). Skip empty ones.
- Items to monitor: ONLY evidence-based findings from THIS numeric data. If nothing, write "None from this window."
- Unavailable data: ONE short paragraph only.
- Do NOT include "Full report saved to" in your brief — the app adds that line.
- Do NOT paste the full detailed analysis into the brief.
- Do NOT echo app zone labels (CRITICAL / WARN / "MAF CRITICAL") as findings.

----------
PART B — after ===FULL_REPORT===
----------
Write the COMPLETE detailed report for saving to a .txt file (for later use with other AI tools).

Start with this title line:

AI VEHICLE ANALYSIS REPORT

Then use EXACTLY these section headings and order:

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
- Whether any app zone/event labels disagreed with numeric evidence

FINAL STATEMENT

End every full report with a statement equivalent to:

This analysis used only the readings present in the supplied live session or log file. Missing, unavailable, N/A, unsupported, or failed sensor responses were not treated as vehicle faults and were not estimated. App ALERT/ZONE labels were treated as heuristics only; conclusions are limited to the valid numeric data that the app and connected scanner were able to obtain.

FINAL QUALITY CHECK

Before returning the reply, verify that:

- every conclusion is supported by received numeric data
- missing values were not interpreted as faults
- no value was invented or estimated
- brief events were not overinterpreted
- related available sensors were cross-checked
- vehicle findings were separated from logger and scanner limitations
- app zone/event labels were not echoed as facts
- uncertainty and missing data were disclosed
- the full report contains the statistics actually used
- both ===SCREEN_BRIEF=== and ===FULL_REPORT=== sections are present
""".trimIndent()

    data class TruncatedLog(
        val csvText: String,
        val rowCount: Int,
        val eventCount: Int,
        val limited: Boolean,
        val windowMinutesUsed: Int,
        val firstTimestampMs: Long? = null,
        val lastTimestampMs: Long? = null,
        val uniqueTimestampCount: Int = 0,
        /** Consecutive snapshot lines with identical sensor fields (time ignored). */
        val nearDuplicateRowCount: Int = 0,
    ) {
        /** Wall-clock span of selected snapshots in seconds (0 if unknown/single). */
        val actualDurationSeconds: Long
            get() {
                val a = firstTimestampMs ?: return 0L
                val b = lastTimestampMs ?: return 0L
                return ((b - a).coerceAtLeast(0L) / 1000L)
            }
    }

    data class Payload(
        val systemPrompt: String = SYSTEM_PROMPT,
        val userMessage: String,
        val windowMinutes: Int,
        val sampleCount: Int,
        val limited: Boolean,
        val sourceLabel: String,
        val firstTimestampMs: Long? = null,
        val lastTimestampMs: Long? = null,
        val actualDurationSeconds: Long = 0L,
        val uniqueTimestampCount: Int = 0,
    )

    /** Parsed dual-output model reply (screen brief + full report body). */
    data class ParsedModelResponse(
        val screenBrief: String,
        val fullReport: String,
        val hadMarkers: Boolean,
    )

    private const val MARKER_BRIEF = "===SCREEN_BRIEF==="
    private const val MARKER_FULL = "===FULL_REPORT==="

    /**
     * Split a model reply into on-screen brief vs full report for the saved .txt.
     * If markers are missing, fall back so the UI still shows something usable
     * and the file still gets the complete text.
     */
    fun parseModelResponse(raw: String): ParsedModelResponse {
        val text = raw.trim()
        val briefIdx = text.indexOf(MARKER_BRIEF)
        val fullIdx = text.indexOf(MARKER_FULL)
        if (briefIdx >= 0 && fullIdx > briefIdx) {
            val brief = text.substring(briefIdx + MARKER_BRIEF.length, fullIdx).trim()
            val full = text.substring(fullIdx + MARKER_FULL.length).trim()
            return ParsedModelResponse(
                screenBrief = brief.ifBlank { text },
                fullReport = full.ifBlank { text },
                hadMarkers = true,
            )
        }
        // Fallback: model ignored format — show a short tip + full text on screen;
        // still save the whole reply as the full report.
        return ParsedModelResponse(
            screenBrief = text,
            fullReport = text,
            hadMarkers = false,
        )
    }

    fun clampWindowMinutes(minutes: Int): Int =
        minutes.coerceIn(MIN_WINDOW_MINUTES, MAX_WINDOW_MINUTES)

    /** App-owned UTC timestamp for the model — do not let the model convert epochs. */
    fun formatIsoUtc(epochMs: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date(epochMs))
    }

    private fun snapshotSensorKey(line: String): String {
        val comma = line.indexOf(',')
        return if (comma >= 0) line.substring(comma + 1) else line
    }

    private fun nearDuplicateCount(snaps: List<Pair<Long, String>>): Int {
        if (snaps.size < 2) return 0
        var dups = 0
        var prev = snapshotSensorKey(snaps.first().second)
        for (i in 1 until snaps.size) {
            val key = snapshotSensorKey(snaps[i].second)
            if (key == prev) dups++ else prev = key
        }
        return dups
    }

    private fun toTruncated(
        csv: String,
        snaps: List<Pair<Long, String>>,
        evs: List<Pair<Long, String>>,
        limited: Boolean,
        mins: Int,
    ): TruncatedLog {
        val first = snaps.firstOrNull()?.first
        val last = snaps.lastOrNull()?.first
        return TruncatedLog(
            csvText = csv,
            rowCount = snaps.size,
            eventCount = evs.size,
            limited = limited,
            windowMinutesUsed = mins,
            firstTimestampMs = first,
            lastTimestampMs = last,
            uniqueTimestampCount = snaps.map { it.first }.toSet().size,
            nearDuplicateRowCount = nearDuplicateCount(snaps),
        )
    }

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
        return toTruncated(csv, usedSnaps, usedEvs, limited, mins)
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
            appendLine(
                "note: pct scores are limited available-check scores only — not a full mechanical health certificate",
            )
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
        isDemo: Boolean = false,
    ): Payload {
        val requested = clampWindowMinutes(windowMinutes)
        val limitedNote = if (log.limited) {
            "NOTE: Selected window was truncated for size/time (row or char cap). " +
                "Actual span may be shorter than the requested $requested minutes. " +
                "Confidence may be limited.\n"
        } else {
            ""
        }
        val thinNote = if (log.rowCount < 5) {
            "NOTE: Few snapshot rows (${log.rowCount}) in this window — report may be limited. Prefer driving with LOG on.\n"
        } else {
            ""
        }
        val demoNote = if (isDemo) {
            "NOTE: These readings are from DEMO mode (simulated), not a live ELM/vehicle connection. Treat as UI/test data only.\n"
        } else {
            ""
        }
        val startIso = log.firstTimestampMs?.let { formatIsoUtc(it) }
        val endIso = log.lastTimestampMs?.let { formatIsoUtc(it) }
        val user = buildString {
            appendLine("Analyze this Honda Civic FB2 session.")
            appendLine("Source: $sourceLabel")
            if (isDemo) appendLine("Mode: DEMO (simulated)")
            appendLine("Requested lookback window: $requested minutes")
            appendLine("Actual selected window duration (seconds): ${log.actualDurationSeconds}")
            if (startIso != null && endIso != null) {
                appendLine("Selected window start (UTC, app-computed): $startIso")
                appendLine("Selected window end (UTC, app-computed): $endIso")
                appendLine("Copy these UTC timestamps into the report. Do not invent or re-convert epoch times.")
            }
            appendLine("Snapshot rows in payload: ${log.rowCount}")
            appendLine("Unique snapshot timestamps: ${log.uniqueTimestampCount}")
            appendLine("Near-duplicate consecutive snapshot rows: ${log.nearDuplicateRowCount}")
            appendLine("Event rows in payload: ${log.eventCount}")
            append(demoNote)
            append(limitedNote)
            append(thinNote)
            appendLine()
            appendLine("Reminders:")
            appendLine("- Reply with ===SCREEN_BRIEF=== then ===FULL_REPORT=== exactly as specified.")
            appendLine("- Judge from numeric CSV/snapshot values; do not echo app ZONE/ALERT labels as facts.")
            appendLine("- Scope findings to the selected analysis window only.")
            appendLine("- Low running voltage → charging-system/system voltage (include Honda ELD); not “weak battery charge”.")
            appendLine("- Negative STFT → mild fuel correction, not proven “richness”.")
            if (isDemo) {
                appendLine("- State clearly in Vehicle and session information that this is DEMO / simulated data.")
            }
            appendLine()
            appendLine("=== LATEST SNAPSHOT ===")
            appendLine(snapshotText.trim())
            appendLine()
            appendLine("=== APP HEALTH NOTES (heuristics only — verify against numbers) ===")
            appendLine(healthText.trim())
            appendLine()
            appendLine("=== DTC LIST ===")
            appendLine(dtcText.trim().ifBlank { "(none reported)" })
            appendLine()
            appendLine("=== LOG WINDOW (CSV) ===")
            appendLine("# events section = app labels only; dashboard_snapshots = numeric evidence")
            append(log.csvText.trim())
            appendLine()
        }
        return Payload(
            userMessage = user,
            windowMinutes = log.windowMinutesUsed,
            sampleCount = log.rowCount,
            limited = log.limited || log.rowCount < 5,
            sourceLabel = sourceLabel,
            firstTimestampMs = log.firstTimestampMs,
            lastTimestampMs = log.lastTimestampMs,
            actualDurationSeconds = log.actualDurationSeconds,
            uniqueTimestampCount = log.uniqueTimestampCount,
        )
    }

    fun snapshotCsvLine(timestampMs: Long, s: VehicleSnapshot): String =
        listOf(
            timestampMs, s.rpm, s.speedKmh, s.coolantC, s.coolant2C, s.intakeC,
            s.ambientC, s.engineLoadPct, s.throttlePct, s.timingAdvance, s.mafGps,
            s.mapKpa, s.stftPct, s.ltftPct, s.batteryVolts, s.gear, s.fuelSystemStatus,
        ).joinToString(",") { it?.toString() ?: "" }
}
