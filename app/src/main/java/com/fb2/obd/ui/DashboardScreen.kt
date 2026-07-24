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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun Double?.fmt(digits: Int = 0): String = this?.let {
    if (digits == 0) it.roundToInt().toString() else "%.${digits}f".format(it)
} ?: "--"

private val DashPageTitles = listOf("Dashboard", "Custom", "Cold start", "Fuel", "Transmission")

/**
 * Landscape diagnostic cluster optimized for dense sensor visibility:
 * compact RPM/Gear/Speed strip on top; swipeable dense tile pages below.
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
    val scope = rememberCoroutineScope()
    var pickerSlot by remember { mutableStateOf<Int?>(null) }

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
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        TopBar(state, onConnectClick, onSettingsClick)

        CompactHeroStrip(
            rpm = s.rpm,
            speedKmh = s.speedKmh,
            gear = s.gear,
            gearSource = if (!showEstimatedGear && s.gearSource == GearSource.ESTIMATED) {
                GearSource.NONE
            } else {
                s.gearSource
            },
        )

        PageTabs(
            titles = DashPageTitles,
            current = pagerState.currentPage,
            onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            userScrollEnabled = true,
        ) { page ->
            when (page) {
                0 -> MetricsPage(
                    snapshot = s,
                    extraPidIds = extraPidIds,
                    extraValues = extraValues,
                    catalog = catalog,
                    onEmptySlotClick = { pickerSlot = it },
                )
                1 -> DenseSensorGridPage(
                    title = "Custom sensors",
                    rows = customValues.entries.map { it.key to it.value }.ifEmpty {
                        listOf("Tip" to "Add in Settings → Custom")
                    },
                    action = "Probe" to onRefreshCustom,
                )
                2 -> DenseSensorGridPage(
                    title = "Cold start / rough idle",
                    rows = buildList {
                        idleTips.take(1).forEach { add("Tip" to it.take(48)) }
                        idleValues.entries
                            .filter { !it.key.matches(Regex("^[0-9A-Fa-f]{4,}$")) }
                            .take(24)
                            .forEach { add(it.key to it.value) }
                    }.ifEmpty { listOf("Status" to "Probing…") },
                    action = "Probe" to onRefreshIdle,
                )
                3 -> DenseSensorGridPage(
                    title = "Fuel system",
                    rows = fuelValues.entries.map { it.key to it.value }
                        .ifEmpty { listOf("Status" to "Probing…") },
                    action = "Refresh" to onRefreshFuel,
                )
                else -> DenseSensorGridPage(
                    title = "Transmission",
                    rows = transValues.entries.map { it.key to it.value }
                        .ifEmpty { listOf("Status" to "Probing…") },
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

/** Thin digital strip — keeps RPM/Speed glanceable without stealing the sensor grid. */
@Composable
private fun CompactHeroStrip(
    rpm: Double?,
    speedKmh: Double?,
    gear: Int?,
    gearSource: GearSource,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroDigit(label = "RPM", value = rpm.fmt(), unit = "", accent = Accent, modifier = Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(min = 48.dp),
        ) {
            Text("GEAR", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            Text(
                text = if (gearSource == GearSource.NONE) "–" else (gear?.toString() ?: "–"),
                color = Accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            val badge = when (gearSource) {
                GearSource.ECU -> "ECU" to GoodGreen
                GearSource.ESTIMATED -> "EST" to WarnAmber
                GearSource.NONE -> null
            }
            if (badge != null) {
                Text(badge.first, color = badge.second, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        HeroDigit(label = "SPEED", value = speedKmh.fmt(), unit = "km/h", accent = GoodGreen, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HeroDigit(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun PageTabs(titles: List<String>, current: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        titles.forEachIndexed { i, title ->
            val selected = i == current
            Text(
                text = title,
                color = if (selected) Accent else TextMuted,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(i) }
                    .background(if (selected) Surface else Background)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
    ) {
        items(baseTiles) { t ->
            val unsupported = t.pid != null && t.pid.number in snapshot.unsupportedPids
            DenseTile(
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
            DenseTile(
                label = pid.label.take(14),
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

/** Dense tile grid for Custom / Cold start / Fuel / Transmission pages. */
@Composable
private fun DenseSensorGridPage(
    title: String,
    rows: List<Pair<String, String>>,
    action: Pair<String, () -> Unit>,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                text = action.first,
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { action.second() }
                    .background(Surface)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(rows) { (label, value) ->
                val unsupported = value.startsWith("n/s") || value == "—" || value.startsWith("Tip")
                val unit = value.substringAfter(" ", missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() && !value.startsWith("n/s") && label != "Tip" && label != "Status" }
                    ?: ""
                val displayValue = when {
                    label == "Tip" || label == "Status" -> value.take(28)
                    value.startsWith("n/s") -> "n/s"
                    else -> value.substringBefore(" ")
                }
                DenseTile(
                    label = label.take(16),
                    value = displayValue,
                    unit = unit.take(6),
                    health = null,
                    muted = unsupported,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DenseTile(
    label: String,
    value: String,
    unit: String,
    health: Health? = null,
    muted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val valueColor = when {
        muted -> TextMuted
        health == null || health == Health.UNKNOWN -> TextPrimary
        else -> health.color()
    }
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (health != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(health.color()),
                )
                Text(
                    text = " $label",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = label,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unit.isNotEmpty()) {
                Text(text = " $unit", color = TextMuted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "+", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "add",
            color = TextMuted,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
        )
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
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FB2 DIAG",
            color = Accent,
            fontSize = 16.sp,
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
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(dot),
            )
            Text(text = "  $text", color = TextMuted, fontSize = 11.sp)

            Text(
                text = "SETTINGS",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSettingsClick() }
                    .background(Surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )

            Text(
                text = "CONNECT",
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onConnectClick() }
                    .background(Surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
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
