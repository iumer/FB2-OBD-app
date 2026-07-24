package com.fb2.obd.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.DashboardUiState
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthEvaluator
import com.fb2.obd.obd.LiveSnapshotOverlay
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.PidDefinition
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.CritRed
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.WarnAmber
import kotlin.math.roundToInt

private fun Double?.fmt(digits: Int = 0): String = this?.let {
    if (digits == 0) it.roundToInt().toString() else "%.${digits}f".format(it)
} ?: "--"

private val DashPageTitles = listOf("Dashboard", "Custom", "Cold start", "Fuel", "Transmission")

/**
 * Landscape instrument cluster: gauges on top; swipeable / scrollable sensor
 * pages below (dashboard tiles, custom, cold-start, fuel, transmission).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    showEstimatedGear: Boolean = true,
    onConnectClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    catalog: List<PidDefinition> = StandardPidCatalog.all,
    extraPidIds: List<String> = emptyList(),
    extraValues: Map<String, String> = emptyMap(),
    onSetExtraPid: (slot: Int, pid: PidDefinition) -> Unit = { _, _ -> },
    customValues: Map<String, String> = emptyMap(),
    fuelValues: Map<String, String> = emptyMap(),
    idleValues: Map<String, String> = emptyMap(),
    idleTips: List<String> = emptyList(),
    transValues: Map<String, String> = emptyMap(),
    onRefreshCustom: () -> Unit = {},
    onRefreshIdle: () -> Unit = {},
    onRefreshFuel: () -> Unit = {},
    onRefreshTrans: () -> Unit = {},
) {
    val s = state.snapshot
    val pagerState = rememberPagerState(pageCount = { DashPageTitles.size })
    var pickerSlot by remember { mutableStateOf<Int?>(null) }

    // Prefetch page data when user swipes to it.
    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            1 -> onRefreshCustom()
            2 -> onRefreshIdle()
            3 -> onRefreshFuel()
            4 -> onRefreshTrans()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        TopBar(state, onConnectClick, onSettingsClick)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularGauge(
                label = "RPM",
                value = s.rpm,
                maxValue = 7000.0,
                unit = "rpm",
                size = 150.dp,
                arcColor = Accent,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val gearSource = if (!showEstimatedGear && s.gearSource == GearSource.ESTIMATED) {
                    GearSource.NONE
                } else {
                    s.gearSource
                }
                val gear = if (gearSource == GearSource.NONE) null else s.gear
                GearIndicator(gear = gear, source = gearSource)
            }
            CircularGauge(
                label = "SPEED",
                value = s.speedKmh,
                maxValue = 200.0,
                unit = "km/h",
                size = 150.dp,
                arcColor = GoodGreen,
            )
        }

        // Page dots + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = DashPageTitles[pagerState.currentPage],
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 10.dp),
            )
            DashPageTitles.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == pagerState.currentPage) Accent else TextMuted),
                )
            }
            Text(
                text = "  swipe pages",
                color = TextMuted,
                fontSize = 11.sp,
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when (page) {
                0 -> MetricsPage(
                    snapshot = s,
                    extraPidIds = extraPidIds,
                    extraValues = extraValues,
                    catalog = catalog,
                    onEmptySlotClick = { pickerSlot = it },
                )
                1 -> DashListPage(
                    title = "Custom sensors (selected)",
                    rows = customValues.entries.map { it.key to it.value }.ifEmpty {
                        listOf("Tip" to "Add sensors in Settings → Custom, or Probe from there")
                    },
                    action = "Probe" to onRefreshCustom,
                )
                2 -> DashListPage(
                    title = "Cold start / rough idle",
                    rows = buildList {
                        idleTips.take(2).forEach { add("Tip" to it) }
                        idleValues.entries
                            // Prefer human labels; skip hex PID-id duplicates.
                            .filter { !it.key.matches(Regex("^[0-9A-Fa-f]{4,}$")) }
                            .take(18)
                            .forEach { add(it.key to it.value) }
                    }.ifEmpty { listOf("Status" to "Swipe here then wait — probing…") },
                    action = "Probe" to onRefreshIdle,
                )
                3 -> DashListPage(
                    title = "Fuel system",
                    rows = fuelValues.entries.map { it.key to it.value }
                        .ifEmpty { listOf("Status" to "Probing fuel PIDs…") },
                    action = "Refresh" to onRefreshFuel,
                )
                else -> DashListPage(
                    title = "Transmission",
                    rows = transValues.entries.map { it.key to it.value }
                        .ifEmpty { listOf("Status" to "Probing TCM pack…") },
                    action = "Probe" to onRefreshTrans,
                )
            }
        }
    }

    val slot = pickerSlot
    if (slot != null) {
        SensorPickerDialog(
            catalog = catalog,
            onPick = { pid ->
                onSetExtraPid(slot, pid)
                pickerSlot = null
            },
            onDismiss = { pickerSlot = null },
        )
    }
}

@Composable
private fun MetricsPage(
    snapshot: VehicleSnapshot,
    extraPidIds: List<String>,
    extraValues: Map<String, String>,
    catalog: List<PidDefinition>,
    onEmptySlotClick: (Int) -> Unit,
) {
    val engineRunning = (snapshot.rpm ?: 0.0) > 0.0
    val baseTiles = listOf(
        TileData("Coolant 1", snapshot.coolantC.fmt(), "\u00B0C", HealthEvaluator.coolant(snapshot.coolantC), ObdPid.COOLANT_TEMP),
        TileData("Coolant 2", snapshot.coolant2C.fmt(), "\u00B0C", HealthEvaluator.coolant(snapshot.coolant2C), ObdPid.COOLANT_TEMP_2),
        TileData("Battery", snapshot.batteryVolts.fmt(1), "V", HealthEvaluator.battery(snapshot.batteryVolts, engineRunning), ObdPid.CONTROL_MODULE_VOLTAGE),
        TileData("Intake", snapshot.intakeC.fmt(), "\u00B0C", pid = ObdPid.INTAKE_TEMP),
        TileData("Ambient", snapshot.ambientC.fmt(), "\u00B0C", pid = ObdPid.AMBIENT_TEMP),
        TileData("Load", snapshot.engineLoadPct.fmt(), "%", pid = ObdPid.ENGINE_LOAD),
        TileData("Throttle", snapshot.throttlePct.fmt(), "%", pid = ObdPid.THROTTLE),
        TileData("STFT", snapshot.stftPct.fmt(1), "%", HealthEvaluator.fuelTrim(snapshot.stftPct), ObdPid.STFT_B1),
        TileData("LTFT", snapshot.ltftPct.fmt(1), "%", HealthEvaluator.fuelTrim(snapshot.ltftPct), ObdPid.LTFT_B1),
        TileData("MAF", snapshot.mafGps.fmt(1), "g/s", pid = ObdPid.MAF),
        TileData("MAP", snapshot.mapKpa.fmt(), "kPa", pid = ObdPid.INTAKE_MAP),
        TileData("Timing", snapshot.timingAdvance.fmt(), "\u00B0", pid = ObdPid.TIMING_ADVANCE),
    )

    val extras = extraPidIds.mapNotNull { id -> catalog.find { it.id.equals(id, true) } }
    val emptySlots = (0 until 6).toList()

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(baseTiles) { t ->
            val unsupported = t.pid != null && t.pid.number in snapshot.unsupportedPids
            StatTile(
                label = t.label,
                value = if (unsupported) "n/s" else t.value,
                unit = if (unsupported) "" else t.unit,
                health = if (unsupported) null else t.health,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(extras) { pid ->
            val text = LiveSnapshotOverlay.formatLiveOrNs(
                pid,
                snapshot,
                fallback = extraValues[pid.id],
            )
            val unsupported = text.startsWith("n/s") || text == "—"
            StatTile(
                label = pid.label.take(12),
                value = text.substringBefore(" "),
                unit = if (unsupported) "" else pid.unit,
                health = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(emptySlots.size) { idx ->
            EmptyTile(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onEmptySlotClick(extras.size + idx) },
            )
        }
    }
}

@Composable
private fun EmptyTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "+", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "add",
            color = TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun DashListPage(
    title: String,
    rows: List<Pair<String, String>>,
    action: Pair<String, () -> Unit>,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                text = action.first,
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { action.second() }
                    .background(Surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEach { (left, right) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(left, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        right.take(40),
                        color = if (right.startsWith("n/s")) TextMuted else Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorPickerDialog(
    catalog: List<PidDefinition>,
    onPick: (PidDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add sensor", color = TextPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.7f).verticalScroll(rememberScrollState())) {
                catalog.forEach { pid ->
                    Text(
                        text = "${pid.label}  (${pid.request})",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(pid) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Accent) }
        },
        containerColor = Background,
    )
}

@Composable
private fun TopBar(
    state: DashboardUiState,
    onConnectClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FB2 DIAG",
            color = Accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (dot, text) = when (state.connection) {
                ConnectionState.CONNECTED ->
                    if (state.sourceIsLive) {
                        GoodGreen to "LIVE \u00B7 ${state.sourceName}"
                    } else {
                        WarnAmber to "DEMO \u00B7 simulated"
                    }
                ConnectionState.CONNECTING -> Accent to "CONNECTING \u00B7 ${state.sourceName}"
                ConnectionState.ERROR -> CritRed to "ERROR \u00B7 ${state.sourceName}"
                ConnectionState.DISCONNECTED -> TextMuted to "OFFLINE"
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(dot),
            )
            Text(text = "  $text", color = TextMuted, fontSize = 13.sp)

            Text(
                text = "SETTINGS",
                color = TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSettingsClick() }
                    .background(Surface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )

            Text(
                text = "CONNECT",
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onConnectClick() }
                    .background(Surface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private data class TileData(
    val label: String,
    val value: String,
    val unit: String,
    val health: Health? = null,
    val pid: ObdPid? = null,
)
