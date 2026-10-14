package com.fb2.obd.data

import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.VehicleSnapshot
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * In-memory logging for diagnostics:
 * - debug log (raw ELM TX/RX/INFO) — separate screen / share,
 * - event log (important state transitions — always on),
 * - main Dash snapshot time series + dash tile extras (LOG toggle).
 *
 * Value-log session export is intentionally lean (events + main Dash only)
 * so short drives stay shareable via WhatsApp / email.
 */
object ObdLogger {

    enum class Dir { TX, RX, INFO }

    data class DebugLine(val timestampMs: Long, val dir: Dir, val text: String)
    data class EventRow(val timestampMs: Long, val category: String, val message: String)
    data class ValueRow(val timestampMs: Long, val snapshot: VehicleSnapshot)
    data class TabValueRow(
        val timestampMs: Long,
        val tab: String,
        val key: String,
        val value: String,
    )
    data class ProbeRow(
        val timestampMs: Long,
        val page: String,
        val pidId: String,
        val label: String,
        val request: String,
        val supported: Boolean,
        val value: String,
        val raw: String?,
    )

    private const val MAX_DEBUG = 8000
    private const val MAX_EVENTS = 4000
    private const val MAX_VALUES = 8000
    private const val MAX_TAB = 24_000
    private const val MAX_PROBES = 12_000

    private val debug = ArrayDeque<DebugLine>()
    private val events = ArrayDeque<EventRow>()
    private val values = ArrayDeque<ValueRow>()
    private val tabValues = ArrayDeque<TabValueRow>()
    private val probes = ArrayDeque<ProbeRow>()
    private val lock = Any()

    @Volatile
    var valueLoggingEnabled: Boolean = false

    /** Only include debug lines at/after this stamp in session export (0 = all). */
    @Volatile
    var sessionStartMs: Long = 0L

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun logDebug(dir: Dir, text: String, nowMs: Long = System.currentTimeMillis()) {
        val clean = text.replace("\r", "\\r").replace("\n", "\\n").trim()
        if (clean.isEmpty() && dir != Dir.INFO) return
        synchronized(lock) {
            debug.addLast(DebugLine(nowMs, dir, clean))
            while (debug.size > MAX_DEBUG) debug.removeFirst()
        }
    }

    /**
     * Important diagnostic event (zone change, gear, ELM connect, DTC, …).
     * Always recorded — not gated on [valueLoggingEnabled].
     */
    fun logEvent(category: String, message: String, nowMs: Long = System.currentTimeMillis()) {
        val cat = category.trim().ifBlank { "EVENT" }
        val msg = message.replace("\r", " ").replace("\n", " ").trim()
        if (msg.isEmpty()) return
        synchronized(lock) {
            events.addLast(EventRow(nowMs, cat, msg))
            while (events.size > MAX_EVENTS) events.removeFirst()
            debug.addLast(DebugLine(nowMs, Dir.INFO, "EVENT [$cat] $msg"))
            while (debug.size > MAX_DEBUG) debug.removeFirst()
        }
    }

    fun logSnapshot(snapshot: VehicleSnapshot, nowMs: Long = System.currentTimeMillis()) {
        if (!valueLoggingEnabled) return
        synchronized(lock) {
            values.addLast(ValueRow(nowMs, snapshot))
            while (values.size > MAX_VALUES) values.removeFirst()
        }
    }

    /** Log a whole tab of key→value pairs (n/s included). */
    fun logTabMap(tab: String, map: Map<String, String>, nowMs: Long = System.currentTimeMillis()) {
        if (!valueLoggingEnabled || map.isEmpty()) return
        synchronized(lock) {
            map.forEach { (k, v) ->
                tabValues.addLast(TabValueRow(nowMs, tab, k, v))
                while (tabValues.size > MAX_TAB) tabValues.removeFirst()
            }
        }
    }

    fun logTabKv(tab: String, key: String, value: String, nowMs: Long = System.currentTimeMillis()) {
        if (!valueLoggingEnabled) return
        synchronized(lock) {
            tabValues.addLast(TabValueRow(nowMs, tab, key, value))
            while (tabValues.size > MAX_TAB) tabValues.removeFirst()
        }
    }

    fun logProbe(page: String, results: List<PidProbeResult>, nowMs: Long = System.currentTimeMillis()) {
        if (results.isEmpty()) {
            logDebug(Dir.INFO, "PROBE [$page] (no results)")
            return
        }
        val ok = results.count { it.supported }
        logDebug(Dir.INFO, "PROBE [$page] begin ${results.size} PIDs ($ok answered)")
        synchronized(lock) {
            results.forEach { r ->
                val valueText = when {
                    !r.supported -> "n/s"
                    r.sample != null -> "%.4f %s".format(r.sample, r.pid.unit).trim()
                    else -> "ok"
                }
                probes.addLast(
                    ProbeRow(
                        timestampMs = nowMs,
                        page = page,
                        pidId = r.pid.id,
                        label = r.pid.label,
                        request = r.pid.request,
                        supported = r.supported,
                        value = valueText,
                        raw = r.raw?.take(80),
                    ),
                )
                while (probes.size > MAX_PROBES) probes.removeFirst()
                // Do not copy probe pages into value-log tab_values — LOG is Dash-only.
                val line = "PROBE [$page] ${r.pid.request} ${r.pid.label} = $valueText" +
                    (r.raw?.let { " | raw=${it.take(60)}" } ?: "")
                debug.addLast(
                    DebugLine(nowMs, Dir.INFO, line.replace("\r", "\\r").replace("\n", "\\n")),
                )
                while (debug.size > MAX_DEBUG) debug.removeFirst()
            }
        }
        logDebug(Dir.INFO, "PROBE [$page] end")
    }

    fun logProbeNote(page: String, note: String) {
        logDebug(Dir.INFO, "PROBE [$page] $note")
    }

    fun debugLines(): List<DebugLine> = synchronized(lock) { debug.toList() }

    fun eventRows(): List<EventRow> = synchronized(lock) { events.toList() }

    fun valueRows(): List<ValueRow> = synchronized(lock) { values.toList() }

    fun tabValueRows(): List<TabValueRow> = synchronized(lock) { tabValues.toList() }

    fun probeRows(): List<ProbeRow> = synchronized(lock) { probes.toList() }

    fun clearDebug() = synchronized(lock) { debug.clear() }

    fun clearEvents() = synchronized(lock) { events.clear() }

    fun clearValues() = synchronized(lock) {
        values.clear()
        tabValues.clear()
        probes.clear()
        events.clear()
    }

    fun debugText(): String = synchronized(lock) {
        debug.joinToString("\n") { "${timeFmt.format(it.timestampMs)} ${it.dir} ${it.text}" }
    }

    /**
     * Lean session export for Share: events + main Dash snapshots + Dash extras.
     * Probes / ELM debug stay on the Debug log screen (not packed into value CSVs).
     */
    /**
     * @param vehicleProfileId e.g. `fb2` / `generic_obd2` so AI + humans know
     * which Settings profile produced the drive.
     * @param vehicleLabel human line written after `# vehicle=`
     */
    fun valuesCsv(
        isDemo: Boolean = false,
        vehicleProfileId: String? = null,
        vehicleLabel: String? = null,
    ): String = synchronized(lock) {
        val header = "time_ms,rpm,speed_kmh,coolant1_c,coolant2_c,intake_c,ambient_c," +
            "load_pct,throttle_pct,timing,maf_gps,map_kpa,stft_pct,ltft_pct,ecu_v,gear,fuel_loop"
        val rows = values.joinToString("\n") { row ->
            val s = row.snapshot
            listOf(
                row.timestampMs, s.rpm, s.speedKmh, s.coolantC, s.coolant2C, s.intakeC,
                s.ambientC, s.engineLoadPct, s.throttlePct, s.timingAdvance, s.mafGps,
                s.mapKpa, s.stftPct, s.ltftPct, s.batteryVolts, s.gear, s.fuelSystemStatus,
            ).joinToString(",") { it?.toString() ?: "" }
        }
        val snapCsv = if (rows.isEmpty()) header else "$header\n$rows"

        val eventHeader = "time_ms,category,message"
        val eventBody = events.joinToString("\n") { e ->
            listOf(e.timestampMs, csvEscape(e.category), csvEscape(e.message)).joinToString(",")
        }
        val eventCsv = if (eventBody.isEmpty()) eventHeader else "$eventHeader\n$eventBody"

        // Only Dash tile rows (extras / deep-found) — never Fuel/Trans/Perf dumps.
        val dashTabs = tabValues.filter { it.tab.equals("Dash", ignoreCase = true) }
        val tabHeader = "time_ms,tab,key,value"
        val tabBody = dashTabs.joinToString("\n") { t ->
            listOf(t.timestampMs, csvEscape(t.tab), csvEscape(t.key), csvEscape(t.value))
                .joinToString(",")
        }
        val tabCsv = if (tabBody.isEmpty()) tabHeader else "$tabHeader\n$tabBody"

        buildString {
            appendLine("# fb2_session_log")
            appendLine("# sections: events | dashboard_snapshots | dash_tiles")
            appendLine("# note: main Dash only (hero + tiles + any + extras). Fuel/Trans/etc. not included.")
            vehicleProfileId?.takeIf { it.isNotBlank() }?.let {
                appendLine("# vehicle_profile=$it")
            }
            vehicleLabel?.takeIf { it.isNotBlank() }?.let {
                appendLine("# vehicle=$it")
            }
            if (isDemo) {
                appendLine("# mode=demo")
                appendLine("# note: Readings are from DEMO (simulated), not a live ELM/vehicle connection.")
            }
            appendLine()
            appendLine("# events")
            appendLine(eventCsv)
            appendLine()
            appendLine("# dashboard_snapshots")
            appendLine(snapCsv)
            appendLine()
            appendLine("# dash_tiles")
            appendLine(tabCsv)
        }
    }

    private fun csvEscape(s: String): String {
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            return "\"" + s.replace("\"", "\"\"") + "\""
        }
        return s
    }
}
