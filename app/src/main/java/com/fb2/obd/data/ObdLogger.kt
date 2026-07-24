package com.fb2.obd.data

import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.VehicleSnapshot
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * In-memory logging for diagnostics:
 * - debug log (raw ELM TX/RX/INFO),
 * - dashboard snapshot time series,
 * - per-tab key/value samples (Custom / Idle / Fuel / Trip / Trans / Perf / …),
 * - page probe results.
 *
 * Session export packs **everything** into one CSV-ish text file for Share.
 */
object ObdLogger {

    enum class Dir { TX, RX, INFO }

    data class DebugLine(val timestampMs: Long, val dir: Dir, val text: String)
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
    private const val MAX_VALUES = 8000
    private const val MAX_TAB = 24_000
    private const val MAX_PROBES = 12_000

    private val debug = ArrayDeque<DebugLine>()
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
                if (valueLoggingEnabled) {
                    tabValues.addLast(TabValueRow(nowMs, page, r.pid.label, valueText))
                    while (tabValues.size > MAX_TAB) tabValues.removeFirst()
                }
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

    fun valueRows(): List<ValueRow> = synchronized(lock) { values.toList() }

    fun tabValueRows(): List<TabValueRow> = synchronized(lock) { tabValues.toList() }

    fun probeRows(): List<ProbeRow> = synchronized(lock) { probes.toList() }

    fun clearDebug() = synchronized(lock) { debug.clear() }

    fun clearValues() = synchronized(lock) {
        values.clear()
        tabValues.clear()
        probes.clear()
    }

    fun debugText(): String = synchronized(lock) {
        debug.joinToString("\n") { "${timeFmt.format(it.timestampMs)} ${it.dir} ${it.text}" }
    }

    /**
     * Full session export: dashboard time series + per-tab values + probes + debug.
     * Designed so a shared file has every page's n/s and live readings.
     */
    fun valuesCsv(): String = synchronized(lock) {
        val header = "time_ms,rpm,speed_kmh,coolant1_c,coolant2_c,intake_c,ambient_c," +
            "load_pct,throttle_pct,timing,maf_gps,map_kpa,stft_pct,ltft_pct,ecu_v,gear"
        val rows = values.joinToString("\n") { row ->
            val s = row.snapshot
            listOf(
                row.timestampMs, s.rpm, s.speedKmh, s.coolantC, s.coolant2C, s.intakeC,
                s.ambientC, s.engineLoadPct, s.throttlePct, s.timingAdvance, s.mafGps,
                s.mapKpa, s.stftPct, s.ltftPct, s.batteryVolts, s.gear,
            ).joinToString(",") { it?.toString() ?: "" }
        }
        val snapCsv = if (rows.isEmpty()) header else "$header\n$rows"

        val tabHeader = "time_ms,tab,key,value"
        val tabBody = tabValues.joinToString("\n") { t ->
            listOf(t.timestampMs, csvEscape(t.tab), csvEscape(t.key), csvEscape(t.value))
                .joinToString(",")
        }
        val tabCsv = if (tabBody.isEmpty()) tabHeader else "$tabHeader\n$tabBody"

        val probeHeader = "time_ms,page,pid_id,label,request,supported,value,raw"
        val probeBody = probes.joinToString("\n") { p ->
            listOf(
                p.timestampMs,
                csvEscape(p.page),
                csvEscape(p.pidId),
                csvEscape(p.label),
                csvEscape(p.request),
                p.supported,
                csvEscape(p.value),
                csvEscape(p.raw.orEmpty()),
            ).joinToString(",")
        }
        val probeCsv = if (probeBody.isEmpty()) probeHeader else "$probeHeader\n$probeBody"

        val start = sessionStartMs
        val debugSlice = if (start > 0L) debug.filter { it.timestampMs >= start } else debug.toList()
        val debugHeader = "time_ms,time,dir,text"
        val debugBody = debugSlice.joinToString("\n") { d ->
            listOf(
                d.timestampMs,
                csvEscape(timeFmt.format(d.timestampMs)),
                d.dir.name,
                csvEscape(d.text),
            ).joinToString(",")
        }
        val debugCsv = if (debugBody.isEmpty()) debugHeader else "$debugHeader\n$debugBody"

        buildString {
            appendLine("# fb2_session_log")
            appendLine("# sections: dashboard_snapshots | tab_values | page_probes | debug_log")
            appendLine()
            appendLine("# dashboard_snapshots")
            appendLine(snapCsv)
            appendLine()
            appendLine("# tab_values")
            appendLine(tabCsv)
            appendLine()
            appendLine("# page_probes")
            appendLine(probeCsv)
            appendLine()
            appendLine("# debug_log")
            append(debugCsv)
        }
    }

    private fun csvEscape(s: String): String {
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            return "\"" + s.replace("\"", "\"\"") + "\""
        }
        return s
    }
}
