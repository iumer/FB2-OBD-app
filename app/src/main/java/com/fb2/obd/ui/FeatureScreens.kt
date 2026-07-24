package com.fb2.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.data.MaintenanceEntry
import com.fb2.obd.obd.ColdStartIdleCatalog
import com.fb2.obd.obd.FreezeFrame
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.Mode06Result
import com.fb2.obd.obd.ModuleScanResult
import com.fb2.obd.obd.O2TestResult
import com.fb2.obd.obd.PidCategory
import com.fb2.obd.obd.PidDefinition
import com.fb2.obd.obd.ReadinessStatus
import com.fb2.obd.obd.VehicleInfo
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.CritRed
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.WarnAmber

@Composable
private fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Background else Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(end = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(if (selected) Accent else Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun CardRow(left: String, right: String, rightColor: androidx.compose.ui.graphics.Color = Accent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = left, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(text = right, color = rightColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CustomSensorsScreen(
    catalog: List<PidDefinition>,
    selectedIds: Set<String>,
    liveValues: Map<String, String>,
    filter: PidCategory?,
    probing: Boolean,
    onFilter: (PidCategory?) -> Unit,
    onToggle: (PidDefinition) -> Unit,
    onProbeSelected: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Background).padding(16.dp)) {
        ScreenHeader(title = "Custom sensors", onBack = onBack) {
            Chip(if (probing) "Probing\u2026" else "Probe selected") { onProbeSelected() }
        }
        Text("Tap + / SEL to add or remove. Probe tests which ones your ECU answers. SEL = selected (not “working”).", color = TextMuted, fontSize = 12.sp)
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Chip("All", selected = filter == null) { onFilter(null) }
            listOf(PidCategory.FUEL, PidCategory.ENGINE, PidCategory.TEMPS, PidCategory.TRANSMISSION, PidCategory.AIR)
                .forEach { c ->
                    Chip(c.name.take(6), selected = filter == c) { onFilter(c) }
                }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            catalog.filter { filter == null || it.category == filter }.forEach { pid ->
                val on = pid.id in selectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .clickable { onToggle(pid) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(pid.label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${pid.request} \u00B7 ${pid.profile}", color = TextMuted, fontSize = 11.sp)
                    }
                    val shown = liveValues[pid.id]
                    Text(
                        text = when {
                            shown == null && on -> "selected"
                            shown == null -> ""
                            else -> shown
                        },
                        color = if (shown != null && shown.startsWith("n/s")) TextMuted else Accent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Text(
                        if (on) "SEL" else "+",
                        color = if (on) GoodGreen else Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun IdleDiagnosticsScreen(
    values: Map<String, String>,
    tips: List<String>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Background).padding(16.dp)) {
        ScreenHeader(title = "Cold start / rough idle", onBack = onBack) {
            Chip(if (loading) "Probing\u2026" else "Probe now") { onRefresh() }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "For rough idle: probe once cold (AC off, Park), wait for warm idle, probe again. " +
                    "Share Debug log so we can lock real Honda misfire / fuel-pressure addresses.",
                color = TextMuted,
                fontSize = 12.sp,
            )
            if (tips.isNotEmpty()) {
                Text(
                    "CHECKS / TIPS",
                    color = WarnAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                tips.forEach { tip ->
                    Text(
                        "• $tip",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            ColdStartIdleCatalog.sections.forEach { section ->
                Text(
                    section.title.uppercase(),
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
                Text(section.hint, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                section.pids.distinctBy { it.id }.forEach { pid ->
                    val display = values[pid.id] ?: values[pid.label] ?: "—"
                    val numeric = display.substringBefore(" ").toDoubleOrNull()
                    val color = when {
                        display.startsWith("n/s") || display == "—" -> TextMuted
                        pid.label.contains("Misfire", true) && numeric != null && numeric > 0 -> CritRed
                        pid.label.contains("Misfire", true) && numeric == 0.0 -> GoodGreen
                        else -> Accent
                    }
                    CardRow(pid.label, display, color)
                }
            }
        }
    }
}

@Composable
fun FuelPageScreen(
    values: Map<String, String>,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Background).padding(16.dp)) {
        ScreenHeader(title = "Fuel system", onBack = onBack) { Chip("Refresh") { onRefresh() } }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            listOf(
                "STFT Bank 1", "LTFT Bank 1", "STFT Bank 2", "LTFT Bank 2",
                "Fuel system status", "O2 B1S1 voltage", "O2 B1S2 voltage",
                "O2 S1 lambda", "O2 S1 AFR", "Commanded AFR / EQ ratio",
                "Fuel rail pressure (rel)", "Fuel rail pressure (abs)",
                "Fuel rail abs pressure", "Engine fuel rate", "Injector pulse width",
            ).forEach { label ->
                CardRow(label, values[label] ?: "n/s / —")
            }
        }
    }
}

@Composable
fun TripScreen(
    distanceKm: Double,
    kmPerL: Double?,
    lPer100: Double?,
    cost: Double,
    idleSec: Double,
    fuelPrice: Double,
    onReset: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(if (embedded) 4.dp else 16.dp)
            .padding(bottom = 16.dp),
    ) {
        if (embedded) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Trip computer", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Chip("Reset trip") { onReset() }
            }
        } else {
            ScreenHeader(title = "Trip computer", onBack = onBack) { Chip("Reset trip") { onReset() } }
        }
        CardRow("Distance", "%.1f km".format(distanceKm))
        CardRow("Economy", kmPerL?.let { "%.1f km/L".format(it) } ?: "—")
        CardRow("Consumption", lPer100?.let { "%.1f L/100km".format(it) } ?: "—")
        CardRow("Trip cost", "%.0f (price %.0f/L)".format(cost, fuelPrice), GoodGreen)
        CardRow("Idle time", "%.0f s".format(idleSec))
        Text("Economy uses fuel-rate PID when available, else MAF estimate.", color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
fun VehicleInfoScreen(info: VehicleInfo, loading: Boolean, onRefresh: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(Background).padding(16.dp)) {
        ScreenHeader(title = "Vehicle info", onBack = onBack) {
            Chip(if (loading) "Reading\u2026" else "Read Mode 09") { onRefresh() }
        }
        CardRow("VIN", info.vin ?: "—")
        CardRow("ECU name", info.ecuName ?: "—")
        Text("Calibration IDs", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        info.calibrationIds.ifEmpty { listOf("—") }.forEach {
            Text(it, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
fun DiagnosticsDepthScreen(
    readiness: ReadinessStatus?,
    freeze: FreezeFrame?,
    o2: List<O2TestResult>,
    mode06: List<Mode06Result>,
    loading: Boolean,
    onScan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Background).padding(16.dp)) {
        ScreenHeader(title = "Deep diagnostics", onBack = onBack) {
            Chip(if (loading) "Scanning\u2026" else "Scan all") { onScan() }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("I/M READINESS", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            readiness?.let { r ->
                CardRow("MIL", if (r.milOn) "ON" else "OFF", if (r.milOn) CritRed else GoodGreen)
                CardRow("DTC count", r.dtcCount.toString())
                r.monitors.forEach {
                    CardRow(it.name, when {
                        !it.available -> "n/a"
                        it.complete -> "Ready"
                        else -> "Not ready"
                    }, if (it.complete) GoodGreen else WarnAmber)
                }
            } ?: Text("Not scanned yet", color = TextMuted, fontSize = 13.sp)

            Text("FREEZE FRAME", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            freeze?.let { f ->
                CardRow("Trigger DTC", f.dtc ?: "none")
                f.values.forEach { (k, v) -> CardRow(k, v) }
            }

            Text("MODE 05 O2 TESTS", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            if (o2.isEmpty()) Text("No Mode 05 data / not supported", color = TextMuted, fontSize = 13.sp)
            o2.forEach { CardRow("${it.sensor} ${it.testId}", it.value) }

            Text("MODE 06 ON-BOARD TESTS", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            if (mode06.isEmpty()) Text("No Mode 06 data / not supported", color = TextMuted, fontSize = 13.sp)
            mode06.forEach { CardRow("TID ${it.tid}", it.raw.take(40)) }
        }
    }
}

@Composable
fun TransmissionDashScreen(
    values: Map<String, String>,
    health: HealthScore?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Background).padding(16.dp)) {
        ScreenHeader(title = "Transmission", onBack = onBack) { Chip("Probe / refresh") { onRefresh() } }
        health?.let {
            val label = if (it.transmissionDataOk && it.transmissionPct != null) {
                "${it.transmissionPct}%"
            } else {
                "n/a — insufficient data"
            }
            val color = when {
                !it.transmissionDataOk -> TextMuted
                (it.transmissionPct ?: 0) >= 85 -> GoodGreen
                else -> WarnAmber
            }
            CardRow("Trans health", label, color)
            it.transmissionNotes.forEach { n -> Text("• $n", color = TextMuted, fontSize = 12.sp) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            listOf(
                "ATF temperature", "Current gear (raw)", "Range selector (PRND)", "Gear ratio (live)",
                "Input shaft RPM", "Output shaft RPM", "TC slip RPM", "TC lock-up status",
                "Line pressure", "Shift solenoid A", "Shift solenoid B", "Shift solenoid C",
                "Shift solenoid D", "Transmission load", "Kickdown status", "Adaptive learning status",
            ).forEach { CardRow(it, values[it] ?: "n/s — not on this ECU yet", TextMuted) }
        }
    }
}

@Composable
fun HealthScoresScreen(
    score: HealthScore?,
    onRefresh: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(if (embedded) 4.dp else 16.dp)
            .padding(bottom = 16.dp),
    ) {
        if (embedded) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Health scores", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Chip("Recalc") { onRefresh() }
            }
        } else {
            ScreenHeader(title = "Health scores", onBack = onBack) { Chip("Recalc") { onRefresh() } }
        }
        if (score == null) {
            Text("Connect and open this page to compute scores.", color = TextMuted)
        } else {
            fun scoreLabel(ok: Boolean, pct: Int?) =
                if (ok && pct != null) "$pct%" else "n/a — insufficient data"
            fun scoreColor(ok: Boolean, pct: Int?) = when {
                !ok || pct == null -> TextMuted
                pct >= 85 -> GoodGreen
                pct >= 60 -> WarnAmber
                else -> CritRed
            }
            CardRow("Engine", scoreLabel(score.engineDataOk, score.enginePct), scoreColor(score.engineDataOk, score.enginePct))
            score.engineNotes.forEach { Text("• $it", color = TextMuted, fontSize = 13.sp) }
            CardRow(
                "Transmission",
                scoreLabel(score.transmissionDataOk, score.transmissionPct),
                scoreColor(score.transmissionDataOk, score.transmissionPct),
            )
            score.transmissionNotes.forEach { Text("• $it", color = TextMuted, fontSize = 13.sp) }
        }
    }
}

@Composable
fun MaintenanceScreen(
    entries: List<MaintenanceEntry>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Background).padding(16.dp)) {
        ScreenHeader(title = "Maintenance log", onBack = onBack)
        Text("Edit via JSON later / next build — template for FB2 service items:", color = TextMuted, fontSize = 12.sp)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            entries.forEach { e ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(Surface).padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(e.item, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(e.spec.ifBlank { "—" }, color = TextMuted, fontSize = 12.sp)
                        Text("Last: ${e.lastChanged ?: "—"}  Next: ${e.nextDueKm?.toString()?.plus(" km") ?: e.nextDueDate ?: "—"}", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun HiddenHondaMenuScreen(
    results: List<ModuleScanResult>,
    loading: Boolean,
    onScan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Background).padding(16.dp)) {
        ScreenHeader(title = "Honda modules", onBack = onBack) {
            Chip(if (loading) "Scanning\u2026" else "Full-system probe") { onScan() }
        }
        Text("Probes ABS / EPS / SRS / TCM / Body / Climate / TPMS / Engine enhanced Mode 22 packs against your ECU.", color = TextMuted, fontSize = 12.sp)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            if (results.isEmpty()) Text("Not scanned yet — tap Full-system probe while connected.", color = TextMuted)
            results.forEach { r ->
                CardRow(r.module, "${r.supportedCount}/${r.totalCount} · ${r.status}", if (r.supportedCount > 0) GoodGreen else TextMuted)
                if (r.samplePids.isNotEmpty()) {
                    Text("  " + r.samplePids.joinToString(", "), color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun GForceScreen(
    ax: Float, ay: Float, az: Float,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    val g = kotlin.math.sqrt((ax * ax + ay * ay + az * az).toDouble()) / 9.81
    Column(
        modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(if (embedded) 4.dp else 16.dp)
            .padding(bottom = 16.dp),
    ) {
        if (embedded) {
            Text(
                "G-force",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        } else {
            ScreenHeader(title = "G-force", onBack = onBack)
        }
        CardRow("Total", "%.2f g".format(g), Accent)
        CardRow("X (lat)", "%.2f m/s\u00B2".format(ax))
        CardRow("Y (long)", "%.2f m/s\u00B2".format(ay))
        CardRow("Z (vert)", "%.2f m/s\u00B2".format(az))
        Text("Uses phone accelerometer. Mount the phone firmly for meaningful readings. 0–100 is on the Performance page.", color = TextMuted, fontSize = 12.sp)
    }
}
