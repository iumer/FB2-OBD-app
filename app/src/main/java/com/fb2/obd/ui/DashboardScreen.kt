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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.DashboardUiState
import com.fb2.obd.PerformanceState
import com.fb2.obd.TripState
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthEvaluator
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.LiveSnapshotOverlay
import com.fb2.obd.obd.MetricStatus
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.PidCategory
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

/** All former Settings “Live pages” — swipe right on the dashboard. */
private val DashPageTitles = listOf(
    "Dash", "Custom", "Idle", "Fuel", "Trip", "Trans", "Perf", "G-force", "Health",
)

/**
 * Landscape diagnostic cluster optimized for dense sensor visibility:
 * compact RPM/Gear/Speed strip on top; swipeable live pages below.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    showEstimatedGear: Boolean = true,
    loggingActive: Boolean = false,
    onConnectClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onDiagnosticsClick: () -> Unit = {},
    onToggleLogging: () -> Unit = {},
    catalog: List<PidDefinition> = StandardPidCatalog.all,
    extraPidIds: List<String> = emptyList(),
    extraValues: Map<String, String> = emptyMap(),
    onSetExtraPid: (slot: Int, pid: PidDefinition) -> Unit = { _, _ -> },
    customValues: Map<String, String> = emptyMap(),
    fuelValues: Map<String, String> = emptyMap(),
    idleValues: Map<String, String> = emptyMap(),
    idleTips: List<String> = emptyList(),
    transValues: Map<String, String> = emptyMap(),
    trip: TripState = TripState(),
    performance: PerformanceState = PerformanceState(),
    health: HealthScore? = null,
    gForceAx: Float = 0f,
    gForceAy: Float = 0f,
    gForceAz: Float = 9.81f,
    onRefreshCustom: () -> Unit = {},
    onRefreshIdle: () -> Unit = {},
    onRefreshFuel: () -> Unit = {},
    onRefreshTrans: () -> Unit = {},
    onManageCustom: () -> Unit = {},
    onResetTrip: () -> Unit = {},
    onSetFuelPrice: (Double) -> Unit = {},
    onResetPerformance: () -> Unit = {},
    onRefreshHealth: () -> Unit = {},
    deepFoundValues: Map<String, String> = emptyMap(),
    onDeepSearch: (label: String, pidId: String?) -> Unit = { _, _ -> },
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
            5 -> onRefreshTrans()
            8 -> onRefreshHealth()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        TopBar(
            state = state,
            loggingActive = loggingActive,
            onConnectClick = onConnectClick,
            onSettingsClick = onSettingsClick,
            onDiagnosticsClick = onDiagnosticsClick,
            onToggleLogging = onToggleLogging,
        )

        CompactHeroStrip(
            rpm = s.rpm,
            speedKmh = s.speedKmh,
            gear = s.gear,
            gearSource = if (!showEstimatedGear && s.gearSource == GearSource.ESTIMATED) {
                GearSource.NONE
            } else {
                s.gearSource
            },
            gearConfidencePct = s.gearConfidencePct,
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
            // Fixed page slot so swipe does not resize the hero strip above.
            Box(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    0 -> MetricsPage(
                        snapshot = s,
                        extraPidIds = extraPidIds,
                        extraValues = extraValues,
                        deepFoundValues = deepFoundValues,
                        catalog = catalog,
                        onEmptySlotClick = { pickerSlot = it },
                        onDeepSearch = onDeepSearch,
                    )
                    1 -> DenseSensorGridPage(
                        title = "Custom sensors",
                        rows = customValues.entries.map { it.key to it.value }.ifEmpty {
                            listOf("Tip" to "Tap Manage to pick sensors from the catalog")
                        },
                        action = "Probe" to onRefreshCustom,
                        secondaryAction = "Manage" to onManageCustom,
                        deepFoundValues = deepFoundValues,
                        onDeepSearch = onDeepSearch,
                    )
                    2 -> DenseSensorGridPage(
                        title = "Cold start / rough idle",
                        tip = idleTips.firstOrNull(),
                        rows = idleValues.entries
                            .filter { !it.key.matches(Regex("^[0-9A-Fa-f]{4,}$")) }
                            .take(24)
                            .map { it.key to it.value }
                            .ifEmpty { listOf("Status" to "Probing…") },
                        action = "Probe" to onRefreshIdle,
                        deepFoundValues = deepFoundValues,
                        onDeepSearch = onDeepSearch,
                    )
                    3 -> DenseSensorGridPage(
                        title = "Fuel system",
                        rows = fuelValues.entries.map { it.key to it.value }
                            .ifEmpty { listOf("Status" to "Probing…") },
                        action = "Refresh" to onRefreshFuel,
                        deepFoundValues = deepFoundValues,
                        onDeepSearch = onDeepSearch,
                    )
                    4 -> TripScreen(
                        distanceKm = trip.distanceKm,
                        kmPerL = trip.kmPerLiter,
                        cost = trip.cost,
                        idleSec = trip.idleSeconds,
                        fuelPrice = trip.fuelPrice,
                        onReset = onResetTrip,
                        onFuelPriceChange = onSetFuelPrice,
                        embedded = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    5 -> DenseSensorGridPage(
                        title = "Transmission",
                        rows = transValues.entries.map { it.key to it.value }
                            .ifEmpty { listOf("Status" to "Probing…") },
                        action = "Probe" to onRefreshTrans,
                        deepFoundValues = deepFoundValues,
                        onDeepSearch = onDeepSearch,
                    )
                    6 -> PerformanceScreen(
                        state = performance,
                        onReset = onResetPerformance,
                        phase = performance.phase,
                        embedded = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    7 -> GForceScreen(
                        ax = gForceAx,
                        ay = gForceAy,
                        az = gForceAz,
                        embedded = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> HealthScoresScreen(
                        score = health,
                        onRefresh = onRefreshHealth,
                        embedded = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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

/** Thin digital strip — fixed height; fonts sized so digits are never clipped. */
@Composable
private fun CompactHeroStrip(
    rpm: Double?,
    speedKmh: Double?,
    gear: Int?,
    gearSource: GearSource,
    gearConfidencePct: Int? = null,
) {
    val rpmStatus = HealthEvaluator.rpm(rpm)
    val speedStatus = HealthEvaluator.speed(speedKmh)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroDigit(
            label = "RPM",
            value = rpm.fmt(),
            unit = "",
            accent = if (rpmStatus.health == Health.UNKNOWN || rpmStatus.health == Health.GOOD) {
                Accent
            } else {
                rpmStatus.health.color()
            },
            valueColor = if (rpmStatus.health == Health.UNKNOWN) TextPrimary else rpmStatus.health.color(),
            modifier = Modifier.weight(1f),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(min = 52.dp)
                .fillMaxHeight(),
        ) {
            Text(
                "GEAR",
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                style = tightTextStyle(9.sp),
            )
            Text(
                text = if (gearSource == GearSource.NONE) "–" else (gear?.toString() ?: "–"),
                color = Accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = tightTextStyle(20.sp),
            )
            val badge = when (gearSource) {
                GearSource.ECU -> "ECU" to GoodGreen
                GearSource.ESTIMATED -> {
                    val conf = gearConfidencePct?.let { "$it%" } ?: "EST"
                    conf to WarnAmber
                }
                GearSource.NONE -> " " to TextMuted
            }
            Text(
                text = badge.first,
                color = badge.second,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                style = tightTextStyle(8.sp),
            )
        }
        HeroDigit(
            label = "SPEED",
            value = speedKmh.fmt(),
            unit = "km/h",
            accent = GoodGreen,
            valueColor = speedStatus.health.color(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HeroDigit(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    valueColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, style = tightTextStyle(9.sp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = tightTextStyle(20.sp),
                maxLines = 1,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    color = TextMuted,
                    fontSize = 10.sp,
                    style = tightTextStyle(10.sp),
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }
    }
}

/** Avoid Android font padding clipping bottoms of bold digits in short rows. */
private fun tightTextStyle(size: androidx.compose.ui.unit.TextUnit) = TextStyle(
    fontSize = size,
    lineHeight = size,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

@Composable
private fun PageTabs(titles: List<String>, current: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.Start,
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
    deepFoundValues: Map<String, String>,
    catalog: List<PidDefinition>,
    onEmptySlotClick: (Int) -> Unit,
    onDeepSearch: (label: String, pidId: String?) -> Unit,
) {
    val engineRunning = (snapshot.rpm ?: 0.0) > 0.0
    val baseTiles = listOf(
        TileData(
            "Coolant 1", snapshot.coolantC.fmt(), "\u00B0C",
            HealthEvaluator.coolant(snapshot.coolantC), ObdPid.COOLANT_TEMP,
        ),
        TileData(
            "Coolant 2", snapshot.coolant2C.fmt(), "\u00B0C",
            HealthEvaluator.coolant(snapshot.coolant2C), ObdPid.COOLANT_TEMP_2,
        ),
        TileData(
            "Battery", snapshot.batteryVolts.fmt(1), "V",
            HealthEvaluator.battery(snapshot.batteryVolts, engineRunning), ObdPid.CONTROL_MODULE_VOLTAGE,
        ),
        TileData(
            "Intake", snapshot.intakeC.fmt(), "\u00B0C",
            HealthEvaluator.intakeAir(snapshot.intakeC), ObdPid.INTAKE_TEMP,
        ),
        TileData(
            "Ambient", snapshot.ambientC.fmt(), "\u00B0C",
            HealthEvaluator.ambient(snapshot.ambientC), ObdPid.AMBIENT_TEMP,
        ),
        TileData(
            "Load", snapshot.engineLoadPct.fmt(), "%",
            HealthEvaluator.engineLoad(snapshot.engineLoadPct), ObdPid.ENGINE_LOAD,
        ),
        TileData(
            "Throttle", snapshot.throttlePct.fmt(), "%",
            HealthEvaluator.throttle(snapshot.throttlePct), ObdPid.THROTTLE,
        ),
        TileData(
            "STFT", snapshot.stftPct.fmt(1), "%",
            HealthEvaluator.fuelTrim(snapshot.stftPct), ObdPid.STFT_B1,
        ),
        TileData(
            "LTFT", snapshot.ltftPct.fmt(1), "%",
            HealthEvaluator.fuelTrim(snapshot.ltftPct), ObdPid.LTFT_B1,
        ),
        TileData(
            "MAF", snapshot.mafGps.fmt(1), "g/s",
            HealthEvaluator.maf(snapshot.mafGps, snapshot.rpm, snapshot.speedKmh), ObdPid.MAF,
        ),
        TileData(
            "MAP", snapshot.mapKpa.fmt(), "kPa",
            HealthEvaluator.map(snapshot.mapKpa, snapshot.throttlePct), ObdPid.INTAKE_MAP,
        ),
        TileData(
            "Timing", snapshot.timingAdvance.fmt(), "\u00B0",
            HealthEvaluator.timing(snapshot.timingAdvance), ObdPid.TIMING_ADVANCE,
        ),
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
            val recovered = deepFoundValues[t.label]
            val showNs = unsupported && recovered == null
            val value = when {
                recovered != null -> recovered.substringBefore(" ")
                unsupported -> "n/s"
                else -> t.value
            }
            val unit = when {
                recovered != null -> recovered.substringAfter(" ", "")
                unsupported -> ""
                else -> t.unit
            }
            DenseTile(
                label = t.label,
                value = value,
                unit = unit,
                health = if (showNs) null else t.status?.health,
                statusLabel = if (showNs) null else t.status?.label,
                muted = showNs,
                deepSearchHint = showNs,
                onDeepSearch = if (showNs) {
                    { onDeepSearch(t.label, t.pid?.request) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(extras) { pid ->
            val recovered = deepFoundValues[pid.label] ?: deepFoundValues[pid.id]
            val text = recovered ?: LiveSnapshotOverlay.formatLiveOrNs(
                pid,
                snapshot,
                fallback = extraValues[pid.id],
            )
            val unsupported = recovered == null && (text.startsWith("n/s") || text == "—")
            DenseTile(
                label = pid.label.take(14),
                value = text.substringBefore(" "),
                unit = if (unsupported) "" else text.substringAfter(" ", pid.unit).ifBlank { pid.unit },
                health = null,
                muted = unsupported,
                deepSearchHint = unsupported,
                onDeepSearch = if (unsupported) {
                    { onDeepSearch(pid.label, pid.id) }
                } else {
                    null
                },
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
    secondaryAction: Pair<String, () -> Unit>? = null,
    tip: String? = null,
    deepFoundValues: Map<String, String> = emptyMap(),
    onDeepSearch: (label: String, pidId: String?) -> Unit = { _, _ -> },
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (secondaryAction != null) {
                Text(
                    text = secondaryAction.first,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { secondaryAction.second() }
                        .background(Surface)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .padding(end = 6.dp),
                )
            }
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
        if (!tip.isNullOrBlank()) {
            Text(
                text = "Tip: $tip",
                color = WarnAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                val recovered = deepFoundValues[label]
                val effective = recovered ?: value
                val unsupported = recovered == null &&
                    (value.startsWith("n/s") || value == "—" || value == "Probing…")
                val unit = effective.substringAfter(" ", missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() && !effective.startsWith("n/s") && label != "Status" }
                    ?: ""
                val displayValue = when {
                    label == "Status" -> effective
                    effective.startsWith("n/s") -> "n/s"
                    else -> effective.substringBefore(" ")
                }
                val transStatus = if (unsupported) null else HealthEvaluator.forTransmissionLabel(label, effective)
                DenseTile(
                    label = label,
                    value = displayValue,
                    unit = unit.take(8),
                    health = transStatus?.health,
                    statusLabel = transStatus?.label,
                    muted = unsupported && label != "Status",
                    deepSearchHint = unsupported && label != "Status",
                    onDeepSearch = if (unsupported && label != "Status") {
                        { onDeepSearch(label, null) }
                    } else {
                        null
                    },
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
    statusLabel: String? = null,
    muted: Boolean = false,
    deepSearchHint: Boolean = false,
    onDeepSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val valueColor = when {
        muted -> TextMuted
        health == null || health == Health.UNKNOWN -> TextPrimary
        else -> health.color()
    }
    var taps by remember { mutableIntStateOf(0) }
    var lastTapMs by remember { mutableLongStateOf(0L) }

    Column(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .then(
                if (onDeepSearch != null) {
                    Modifier.clickable {
                        val now = System.currentTimeMillis()
                        taps = if (now - lastTapMs < 700L) taps + 1 else 1
                        lastTapMs = now
                        if (taps >= 3) {
                            taps = 0
                            onDeepSearch()
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (health != null && health != Health.UNKNOWN) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(health.color()),
                )
                Text(
                    text = " $label",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = tightTextStyle(9.sp),
                )
            } else {
                Text(
                    text = label,
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = tightTextStyle(9.sp),
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tightTextStyle(15.sp),
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    color = TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    style = tightTextStyle(9.sp),
                )
            }
        }
        when {
            deepSearchHint -> Text("tap×3 deep", color = Accent, fontSize = 8.sp, maxLines = 1, style = tightTextStyle(8.sp))
            !statusLabel.isNullOrBlank() && !muted -> Text(
                text = statusLabel,
                color = health?.color() ?: TextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tightTextStyle(8.sp),
            )
            else -> Text(" ", fontSize = 8.sp, style = tightTextStyle(8.sp)) // reserve line
        }
    }
}

@Composable
private fun EmptyTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "+", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "add",
            color = TextMuted,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
        )
    }
}

private fun PidCategory.displayName(): String = when (this) {
    PidCategory.ENGINE -> "Engine"
    PidCategory.FUEL -> "Fuel"
    PidCategory.TEMPS -> "Temperatures"
    PidCategory.AIR -> "Air / Intake"
    PidCategory.ELECTRICAL -> "Electrical"
    PidCategory.EMISSIONS -> "Emissions"
    PidCategory.TRANSMISSION -> "Transmission"
    PidCategory.ABS -> "ABS / Brakes"
    PidCategory.EPS -> "Steering (EPS)"
    PidCategory.SRS -> "SRS / Airbags"
    PidCategory.BODY -> "Body"
    PidCategory.CLIMATE -> "HVAC / Climate"
    PidCategory.TPMS -> "Tire pressure"
    PidCategory.OTHER -> "Other"
}

private fun profileDisplayName(profile: String): String = when {
    profile.equals("SAE", ignoreCase = true) -> "Standard OBD (Mode 01)"
    profile.contains("tcm", ignoreCase = true) -> "Honda Transmission"
    profile.contains("engine", ignoreCase = true) -> "Honda Engine"
    profile.contains("abs", ignoreCase = true) -> "Honda ABS"
    profile.contains("eps", ignoreCase = true) -> "Honda EPS"
    profile.contains("srs", ignoreCase = true) -> "Honda SRS"
    profile.contains("body", ignoreCase = true) -> "Honda Body"
    profile.contains("climate", ignoreCase = true) -> "Honda HVAC"
    profile.contains("tpms", ignoreCase = true) -> "Honda TPMS"
    else -> profile
}

/**
 * In-dialog drill-down: Categories → Subcategories (profile packs) → Sensors.
 * Stays in the same AlertDialog list — no separate screen.
 */
@Composable
private fun SensorPickerDialog(
    catalog: List<PidDefinition>,
    onPick: (PidDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    var category by remember { mutableStateOf<PidCategory?>(null) }
    var subProfile by remember { mutableStateOf<String?>(null) }

    val title = when {
        category == null -> "Add sensor — pick category"
        subProfile == null -> category!!.displayName()
        else -> "${category!!.displayName()} › ${profileDisplayName(subProfile!!)}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.82f).verticalScroll(rememberScrollState())) {
                Text(
                    text = "Scroll for all categories (Engine, Fuel, Air, Electrical…)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                // Back row inside the same list
                if (category != null) {
                    Text(
                        text = if (subProfile != null) "← Subcategories" else "← Categories",
                        color = Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (subProfile != null) {
                                    subProfile = null
                                } else {
                                    category = null
                                }
                            }
                            .padding(vertical = 6.dp),
                    )
                }

                when {
                    category == null -> {
                        val cats = catalog
                            .groupBy { it.category }
                            .entries
                            .sortedBy { it.key.displayName() }
                        cats.forEach { (cat, pids) ->
                            PickerRow(
                                title = cat.displayName(),
                                subtitle = "${pids.size} sensors",
                                trailing = "›",
                                onClick = {
                                    category = cat
                                    subProfile = null
                                },
                            )
                        }
                    }

                    subProfile == null -> {
                        val inCat = catalog.filter { it.category == category }
                        val groups = inCat.groupBy { it.profile }.entries.sortedBy { profileDisplayName(it.key) }
                        if (groups.size <= 1) {
                            // Only one pack — skip subcategory and list sensors directly.
                            inCat.sortedBy { it.label }.forEach { pid ->
                                PickerRow(
                                    title = pid.label,
                                    subtitle = pid.request + if (pid.unit.isNotBlank()) " · ${pid.unit}" else "",
                                    trailing = "+",
                                    onClick = { onPick(pid) },
                                )
                            }
                        } else {
                            groups.forEach { (profile, pids) ->
                                PickerRow(
                                    title = profileDisplayName(profile),
                                    subtitle = "${pids.size} sensors",
                                    trailing = "›",
                                    onClick = { subProfile = profile },
                                )
                            }
                        }
                    }

                    else -> {
                        catalog
                            .filter { it.category == category && it.profile == subProfile }
                            .sortedBy { it.label }
                            .forEach { pid ->
                                PickerRow(
                                    title = pid.label,
                                    subtitle = pid.request + if (pid.unit.isNotBlank()) " · ${pid.unit}" else "",
                                    trailing = "+",
                                    onClick = { onPick(pid) },
                                )
                            }
                    }
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
private fun PickerRow(
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(Surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trailing, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TopBarChip(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(Surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun TopBar(
    state: DashboardUiState,
    loggingActive: Boolean,
    onConnectClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onToggleLogging: () -> Unit,
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
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            val (dot, text) = when (state.connection) {
                ConnectionState.CONNECTED ->
                    if (state.sourceIsLive) {
                        GoodGreen to "LIVE"
                    } else {
                        WarnAmber to "DEMO"
                    }
                ConnectionState.CONNECTING -> Accent to "…"
                ConnectionState.ERROR -> CritRed to "ERR"
                ConnectionState.DISCONNECTED -> TextMuted to "OFF"
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(dot),
            )
            Text(text = " $text", color = TextMuted, fontSize = 11.sp)

            TopBarChip(if (loggingActive) "STOP LOG" else "LOG", if (loggingActive) CritRed else Accent, onToggleLogging)
            TopBarChip("DIAG", Accent, onDiagnosticsClick)
            TopBarChip("SETTINGS", TextMuted, onSettingsClick)
            // CONNECTED only for a real ELM adapter — Demo keeps CONNECT (+ yellow DEMO badge).
            val liveConnected = state.connection == ConnectionState.CONNECTED && state.sourceIsLive
            TopBarChip(
                text = when {
                    liveConnected -> "CONNECTED"
                    state.connection == ConnectionState.CONNECTING -> "…"
                    else -> "CONNECT"
                },
                color = if (liveConnected) GoodGreen else Accent,
                onClick = onConnectClick,
            )
        }
    }
}

private data class TileData(
    val label: String,
    val value: String,
    val unit: String,
    val status: MetricStatus? = null,
    val pid: ObdPid? = null,
)
