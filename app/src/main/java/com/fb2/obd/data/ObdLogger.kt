package com.fb2.obd.data

import com.fb2.obd.obd.VehicleSnapshot
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * In-memory logging for diagnostics:
 * - a **debug log** of raw ELM327 traffic (TX/RX/INFO lines), and
 * - a **value log** of decoded snapshots over time (exportable as CSV).
 *
 * Both are bounded ring buffers so long sessions don't exhaust memory. Access is
 * synchronized because raw logging happens on the Bluetooth IO thread while the
 * UI reads on the main thread. Pure Kotlin (no Android deps) so it is unit
 * testable; a process-wide singleton so any screen can read it.
 */
object ObdLogger {

    enum class Dir { TX, RX, INFO }

    data class DebugLine(val timestampMs: Long, val dir: Dir, val text: String)
    data class ValueRow(val timestampMs: Long, val snapshot: VehicleSnapshot)

    private const val MAX_DEBUG = 800
    private const val MAX_VALUES = 2000

    private val debug = ArrayDeque<DebugLine>()
    private val values = ArrayDeque<ValueRow>()
    private val lock = Any()

    /** When true, decoded snapshots are appended to the value log. */
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

    fun debugLines(): List<DebugLine> = synchronized(lock) { debug.toList() }

    fun valueRows(): List<ValueRow> = synchronized(lock) { values.toList() }

    fun clearDebug() = synchronized(lock) { debug.clear() }

    fun clearValues() = synchronized(lock) { values.clear() }

    /** Debug log as shareable text. */
    fun debugText(): String = synchronized(lock) {
        debug.joinToString("\n") { "${timeFmt.format(it.timestampMs)} ${it.dir} ${it.text}" }
    }

    /** Value log as CSV (one row per snapshot). */
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
        if (rows.isEmpty()) header else "$header\n$rows"
    }
}
