package com.fb2.obd.data

import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.VehicleSnapshot
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * In-memory logging for diagnostics:
 * - a **debug log** of raw ELM327 traffic (TX/RX/INFO lines),
 * - a **value log** of decoded dashboard snapshots (CSV),
 * - a **probe log** of one-shot page probes (custom / fuel / idle / transmission / Honda).
 *
 * Bounded ring buffers. Access is synchronized (BT IO thread + UI thread).
 */
object ObdLogger {

    enum class Dir { TX, RX, INFO }

    data class DebugLine(val timestampMs: Long, val dir: Dir, val text: String)
    data class ValueRow(val timestampMs: Long, val snapshot: VehicleSnapshot)
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

    private const val MAX_DEBUG = 1200
    private const val MAX_VALUES = 2000
    private const val MAX_PROBES = 4000

    private val debug = ArrayDeque<DebugLine>()
    private val values = ArrayDeque<ValueRow>()
    private val probes = ArrayDeque<ProbeRow>()
    private val lock = Any()

    /** When true, decoded dashboard snapshots are appended to the value log. */
    @Volatile
    var valueLoggingEnabled: Boolean = false

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

    /**
     * Log a page probe. Always written to the debug log (Share debug captures it)
     * and to the probe CSV buffer (exported with the value log).
     */
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

    fun probeRows(): List<ProbeRow> = synchronized(lock) { probes.toList() }

    fun clearDebug() = synchronized(lock) { debug.clear() }

    fun clearValues() = synchronized(lock) {
        values.clear()
        probes.clear()
    }

    fun debugText(): String = synchronized(lock) {
        debug.joinToString("\n") { "${timeFmt.format(it.timestampMs)} ${it.dir} ${it.text}" }
    }

    /** Dashboard snapshots + page probe results as one shareable text/CSV blob. */
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

        "# dashboard_snapshots\n$snapCsv\n\n# page_probes\n$probeCsv"
    }

    private fun csvEscape(s: String): String {
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            return "\"" + s.replace("\"", "\"\"") + "\""
        }
        return s
    }
}
