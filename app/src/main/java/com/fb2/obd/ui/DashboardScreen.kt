@file:OptIn(ExperimentalFoundationApi::class)

package com.fb2.obd.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.DashboardUiState
import com.fb2.obd.PerformanceState
import com.fb2.obd.TripState
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.DashboardLook
import com.fb2.obd.obd.EditableMetric
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthEvaluator
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.LiveSnapshotOverlay
import com.fb2.obd.obd.MetricStatus
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.PidCategory
import com.fb2.obd.obd.PidDefinition
import com.fb2.obd.obd.SnapshotFreshness
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.VehicleProfile
import com.fb2.obd.obd.VehicleProfileConfig
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.obd.isEffectivelyBlank
import com.fb2.obd.ui.dash.PulseDeckDash
import com.fb2.obd.ui.dash.RedOrbitDash
import com.fb2.obd.ui.dash.TwinGaugeDash
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.CritRed
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.WarnAmber
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Column count — prefer wider tiles on car HUs (readable while driving). */
private fun dashColumnsForWidth(maxWidth: Dp): Int = when {
    maxWidth >= 1700.dp -> 4
    maxWidth >= 1100.dp -> 3
    else -> 2
}

private fun denseColumnsForWidth(maxWidth: Dp): Int = when {
    maxWidth >= 1400.dp -> 3
    else -> 2
}

/**
 * In-car type scale — glanceable on a dash HU without overflowing short
 * 1024×600 / denser 1280×720 landscapes. Values use ellipsis; tiles are fixed height.
 * Grid scrolls for extra rows — never clips text inside a tile.
 */
private object DashType {
    val tileH = 80.dp
    val tileGap = 6.dp
    val tileLabel = 12.sp
    val tileValue = 22.sp
    val tileUnit = 12.sp
    val tileStatus = 11.sp
    val tileHint = 10.sp

    val heroH = 92.dp
    val heroLabel = 11.sp
    val heroValue = 30.sp
    val heroUnit = 12.sp
    val heroBadge = 11.sp

    val tab = 14.sp
    val topTitle = 18.sp
    val topChip = 13.sp
    val pageTitle = 13.sp
}
private fun Double?.fmt(digits: Int = 0): String = this?.let {
    if (digits == 0) it.roundToInt().toString() else "%.${digits}f".format(it)
} ?: "--"

/** Default FB2 pages; Generic OBD2 drops Trans via [VehicleProfileConfig]. */
private val DefaultDashPageTitles = VehicleProfileConfig.dashPageTitles(VehicleProfile.FB2)

/**
 * Landscape diagnostic cluster optimized for dense sensor visibility:
 * compact RPM/Gear/Speed strip on top; swipeable live pages below.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    showEstimatedGear: Boolean = true,
    dashboardLook: DashboardLook = DashboardLook.CLASSIC,
    loggingActive: Boolean = false,
    pageTitles: List<String> = DefaultDashPageTitles,
    profileBadge: String = VehicleProfile.FB2.badge,
    onConnectClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onDiagnosticsClick: () -> Unit = {},
    onToggleLogging: () -> Unit = {},
    onMinimizeClick: () -> Unit = {},
    catalog: List<PidDefinition> = StandardPidCatalog.all,
    extraPidIds: List<String> = emptyList(),
    extraValues: Map<String, String> = emptyMap(),
    tileOverrides: Map<String, String> = emptyMap(),
    onSetExtraPid: (slot: Int, pid: PidDefinition) -> Unit = { _, _ -> },
    onSetTileOverride: (baseLabel: String, pid: PidDefinition) -> Unit = { _, _ -> },
    onClearTileOverride: (baseLabel: String) -> Unit = {},
    customValues: Map<String, String> = emptyMap(),
    fuelValues: Map<String, String> = emptyMap(),
    idleValues: Map<String, String> = emptyMap(),
    idleTips: List<String> = emptyList(),
    transValues: Map<String, String> = emptyMap(),
    trip: TripState = TripState(),
    performance: PerformanceState = PerformanceState(),
    health: HealthScore? = null,
    dtcCount: Int? = null,
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
    healthThresholds: HealthThresholds = HealthThresholds.DEFAULT,
    onThresholdFieldChange: (id: String, value: Double) -> Unit = { _, _ -> },
    onResetThresholds: () -> Unit = {},
    /** Zone hysteresis from [DiagnosticBrain] — keeps phone Dash colours stable. */
    onLatchHealth: (String, MetricStatus) -> MetricStatus = { _, status -> status },
) {
    val s = state.snapshot
    val titles = pageTitles.ifEmpty { DefaultDashPageTitles }
    val pagerState = rememberPagerState(pageCount = { titles.size })
    val scope = rememberCoroutineScope()
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var editMetric by remember { mutableStateOf<EditableMetric?>(null) }

    LaunchedEffect(pagerState.currentPage, titles) {
        when (titles.getOrNull(pagerState.currentPage)) {
            "Custom" -> onRefreshCustom()
            "Idle" -> onRefreshIdle()
            "Fuel" -> onRefreshFuel()
            "Trans" -> onRefreshTrans()
            "Health" -> onRefreshHealth()
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
            profileBadge = profileBadge,
            onConnectClick = onConnectClick,
            onSettingsClick = onSettingsClick,
            onDiagnosticsClick = onDiagnosticsClick,
            onToggleLogging = onToggleLogging,
            onMinimizeClick = onMinimizeClick,
        )

        val onDashPage = titles.getOrNull(pagerState.currentPage) == "Dash"
        val useAltHero = dashboardLook != DashboardLook.CLASSIC && onDashPage
        if (!useAltHero) {
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
                thresholds = healthThresholds,
                rpmFreshAtMs = s.freshAtMs[SnapshotFreshness.KEY_RPM],
                speedFreshAtMs = s.freshAtMs[SnapshotFreshness.KEY_SPEED],
                onEditRpm = { editMetric = EditableMetric.RPM },
            )
        }

        PageTabs(
            titles = titles,
            current = pagerState.currentPage,
            onSelect = { page -> scope.launch { pagerState.scrollToPage(page) } },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // Vertical metric wheels fight nested horizontal swipe — use tabs on Red Orbit Dash.
            userScrollEnabled = !(
                dashboardLook == DashboardLook.RED_ORBIT &&
                    titles.getOrNull(pagerState.currentPage) == "Dash"
                ),
            beyondBoundsPageCount = 0,
        ) { page ->
            // Fixed page slot so swipe does not resize the hero strip above.
            Box(modifier = Modifier.fillMaxSize()) {
                when (titles.getOrNull(page)) {
                    "Dash" -> {
                        val gearSrc = if (!showEstimatedGear && s.gearSource == GearSource.ESTIMATED) {
                            GearSource.NONE
                        } else {
                            s.gearSource
                        }
                        val healthSnap = state.decisionSnapshot.takeUnless { it.isEffectivelyBlank() } ?: s
                        when (dashboardLook) {
                            DashboardLook.CLASSIC -> MetricsPage(
                                snapshot = s,
                                healthSnapshot = healthSnap,
                                latchHealth = onLatchHealth,
                                extraPidIds = extraPidIds,
                                extraValues = extraValues,
                                tileOverrides = tileOverrides,
                                deepFoundValues = deepFoundValues,
                                catalog = catalog,
                                thresholds = healthThresholds,
                                dtcCount = dtcCount,
                                healthScore = health,
                                onEmptySlotClick = { pickerTarget = PickerTarget.ExtraSlot(it) },
                                onRemapBaseTile = { label -> pickerTarget = PickerTarget.RemapBase(label) },
                                onRemapExtra = { index -> pickerTarget = PickerTarget.ExtraSlot(index) },
                                onDeepSearch = onDeepSearch,
                                onEditThresholds = { editMetric = it },
                            )
                            DashboardLook.RED_ORBIT -> RedOrbitDash(
                                snapshot = s,
                                healthSnapshot = healthSnap,
                                thresholds = healthThresholds,
                                gearSource = gearSrc,
                                gearConfidencePct = s.gearConfidencePct,
                                dtcCount = dtcCount,
                                healthScore = health,
                                latchHealth = onLatchHealth,
                            )
                            DashboardLook.TWIN_GAUGE -> TwinGaugeDash(
                                snapshot = s,
                                healthSnapshot = healthSnap,
                                thresholds = healthThresholds,
                                gearSource = gearSrc,
                                dtcCount = dtcCount,
                                healthScore = health,
                                latchHealth = onLatchHealth,
                            )
                            DashboardLook.PULSE_DECK -> PulseDeckDash(
                                snapshot = s,
                                healthSnapshot = healthSnap,
                                thresholds = healthThresholds,
                                gearSource = gearSrc,
                                gearConfidencePct = s.gearConfidencePct,
                                dtcCount = dtcCount,
                                healthScore = health,
                                latchHealth = onLatchHealth,
                            )
                        }
                    }
                    "Custom" -> DenseSensorGridPage(
                        title = "Custom sensors",
                        rows = customValues.entries.map { it.key to it.value }.ifEmpty {
                            listOf("Tip" to "Tap Manage to pick sensors from the catalog")
                        },
                        action = "Probe" to onRefreshCustom,
                        secondaryAction = "Manage" to onManageCustom,
                        deepFoundValues = deepFoundValues,
                        onDeepSearch = onDeepSearch,
                        thresholds = healthThresholds,
                        snapshot = s,
                        onEditThresholds = { editMetric = it },
                    )
                    "Idle" -> DenseSensorGridPage(
                        title = "Cold start / rough idle",
                        tip = idleTips.firstOrNull(),
                        rows = idleValues.entries
                            .filter { !it.key.matches(Regex("^[0-9A-Fa-f]{4,}$")) }
                            .take(16)
                            .map { it.key to it.value }
                            .ifEmpty { listOf("Status" to "Probing…") },
                        action = "Probe" to onRefreshIdle,
                        deepFoundValues = deepFoundValues,
                        onDeepSearch = onDeepSearch,
                        thresholds = healthThresholds,
                        snapshot = s,
                        onEditThresholds = { editMetric = it },
                    )
                    "Fuel" -> DenseSensorGridPage(
                        title = "Fuel system",
                        rows = fuelValues.entries.map { it.key to it.value }
                            .ifEmpty { listOf("Status" to "Probing…") },
                        action = "Refresh" to onRefreshFuel,
                        deepFoundValues = deepFoundValues,
                        onDeepSearch = onDeepSearch,
                        thresholds = healthThresholds,
                        snapshot = s,
                        onEditThresholds = { editMetric = it },
                    )
                    "Trip" -> TripScreen(
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
                    "Trans" -> DenseSensorGridPage(
                        title = "Transmission",
                        rows = transValues.entries.map { it.key to it.value }
                            .ifEmpty { listOf("Status" to "Probing…") },
                        action = "Probe" to onRefreshTrans,
                        deepFoundValues = deepFoundValues,
                        onDeepSearch = onDeepSearch,
                        thresholds = healthThresholds,
                        snapshot = s,
                        onEditThresholds = { editMetric = it },
                    )
                    "Perf" -> PerformanceScreen(
                        state = performance,
                        onReset = onResetPerformance,
                        phase = performance.phase,
                        embedded = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    "G-force" -> GForceScreen(
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

    val target = pickerTarget
    if (target != null) {
        SensorPickerDialog(
            catalog = catalog,
            restoreLabel = (target as? PickerTarget.RemapBase)?.label
                ?.takeIf { tileOverrides.containsKey(it) },
            onPick = { pid ->
                when (target) {
                    is PickerTarget.ExtraSlot -> onSetExtraPid(target.index, pid)
                    is PickerTarget.RemapBase -> onSetTileOverride(target.label, pid)
                }
                pickerTarget = null
            },
            onRestore = {
                (target as? PickerTarget.RemapBase)?.let { onClearTileOverride(it.label) }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
        )
    }

    val metric = editMetric
    if (metric != null) {
        ThresholdEditorDialog(
            metric = metric,
            thresholds = healthThresholds,
            onChangeField = onThresholdFieldChange,
            onResetAll = onResetThresholds,
            onDismiss = { editMetric = null },
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
    thresholds: HealthThresholds = HealthThresholds.DEFAULT,
    rpmFreshAtMs: Long? = null,
    speedFreshAtMs: Long? = null,
    onEditRpm: (() -> Unit)? = null,
) {
    val rpmStatus = HealthEvaluator.rpm(rpm, thresholds)
    val speedStatus = HealthEvaluator.speed(speedKmh)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp)
            .height(DashType.heroH)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
            freshAtMs = rpmFreshAtMs,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onEditRpm != null) {
                        Modifier.combinedClickable(onClick = {}, onLongClick = onEditRpm)
                    } else {
                        Modifier
                    },
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(min = 84.dp)
                .padding(horizontal = 4.dp),
        ) {
            Text(
                "GEAR",
                color = TextMuted,
                fontSize = DashType.heroLabel,
                fontWeight = FontWeight.Bold,
                style = tightTextStyle(DashType.heroLabel),
                maxLines = 1,
            )
            Text(
                text = if (gearSource == GearSource.NONE) "–" else (gear?.toString() ?: "–"),
                color = Accent,
                fontSize = DashType.heroValue,
                fontWeight = FontWeight.Bold,
                style = tightTextStyle(DashType.heroValue),
                maxLines = 1,
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
                fontSize = DashType.heroBadge,
                fontWeight = FontWeight.Bold,
                // Avoid Trim.Both — "%" descender was clipped on phones.
                style = TextStyle(
                    fontSize = DashType.heroBadge,
                    lineHeight = 14.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(top = 2.dp, bottom = 1.dp),
            )
        }
        HeroDigit(
            label = "SPEED",
            value = speedKmh.fmt(),
            unit = "km/h",
            accent = GoodGreen,
            valueColor = speedStatus.health.color(),
            freshAtMs = speedFreshAtMs,
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
    freshAtMs: Long? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FreshnessHeartbeat(lastOkMs = freshAtMs, size = 7.dp)
            Text(
                text = " $label",
                color = accent,
                fontSize = DashType.heroLabel,
                fontWeight = FontWeight.Bold,
                style = tightTextStyle(DashType.heroLabel),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontSize = DashType.heroValue,
                fontWeight = FontWeight.Bold,
                style = tightTextStyle(DashType.heroValue),
                maxLines = 1,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    color = TextMuted,
                    fontSize = DashType.heroUnit,
                    fontWeight = FontWeight.Bold,
                    style = tightTextStyle(DashType.heroUnit),
                    modifier = Modifier.padding(bottom = 4.dp),
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
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        titles.forEachIndexed { i, title ->
            val selected = i == current
            Text(
                text = title,
                color = if (selected) Accent else TextMuted,
                fontSize = DashType.tab,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(i) }
                    .background(if (selected) Surface else Background)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun MetricsPage(
    snapshot: VehicleSnapshot,
    healthSnapshot: VehicleSnapshot = snapshot,
    latchHealth: (String, MetricStatus) -> MetricStatus = { _, status -> status },
    extraPidIds: List<String>,
    extraValues: Map<String, String>,
    tileOverrides: Map<String, String>,
    deepFoundValues: Map<String, String>,
    catalog: List<PidDefinition>,
    thresholds: HealthThresholds,
    dtcCount: Int?,
    healthScore: HealthScore?,
    onEmptySlotClick: (Int) -> Unit,
    onRemapBaseTile: (label: String) -> Unit,
    onRemapExtra: (index: Int) -> Unit,
    onDeepSearch: (label: String, pidId: String?) -> Unit,
    onEditThresholds: (EditableMetric) -> Unit,
) {
    val hs = healthSnapshot
    val engineRunning = (hs.rpm ?: snapshot.rpm ?: 0.0) > 0.0
    val t = thresholds
    val vehiclePct = healthScore?.vehiclePct
    fun L(key: String, status: MetricStatus) = latchHealth(key, status)
    val baseTiles = listOf(
        TileData(
            "Coolant 1", snapshot.coolantC.fmt(), "\u00B0C",
            L("coolant1", HealthEvaluator.coolant(hs.coolantC, t)), ObdPid.COOLANT_TEMP,
        ),
        TileData(
            "Coolant 2", snapshot.coolant2C.fmt(), "\u00B0C",
            L("coolant2", HealthEvaluator.coolant(hs.coolant2C, t)), ObdPid.COOLANT_TEMP_2,
        ),
        TileData(
            "Battery", snapshot.batteryVolts.fmt(1), "V",
            L(
                "battery",
                HealthEvaluator.battery(hs.batteryVolts, engineRunning, t, rpm = hs.rpm ?: snapshot.rpm),
            ),
            ObdPid.CONTROL_MODULE_VOLTAGE,
        ),
        TileData(
            "Intake", snapshot.intakeC.fmt(), "\u00B0C",
            L("intake", HealthEvaluator.intakeAir(hs.intakeC, t)), ObdPid.INTAKE_TEMP,
        ),
        TileData(
            "Ambient", snapshot.ambientC.fmt(), "\u00B0C",
            L("ambient", HealthEvaluator.ambient(hs.ambientC, t)), ObdPid.AMBIENT_TEMP,
        ),
        TileData(
            "Load", snapshot.engineLoadPct.fmt(), "%",
            HealthEvaluator.engineLoad(snapshot.engineLoadPct, t), ObdPid.ENGINE_LOAD,
        ),
        TileData(
            "Throttle", snapshot.throttlePct.fmt(), "%",
            HealthEvaluator.throttle(snapshot.throttlePct), ObdPid.THROTTLE,
        ),
        TileData(
            "STFT", snapshot.stftPct.fmt(1), "%",
            L("stft", HealthEvaluator.fuelTrim(hs.stftPct, t)), ObdPid.STFT_B1,
        ),
        TileData(
            "LTFT", snapshot.ltftPct.fmt(1), "%",
            L("ltft", HealthEvaluator.fuelTrim(hs.ltftPct, t)), ObdPid.LTFT_B1,
        ),
        TileData(
            "MAF", snapshot.mafGps.fmt(1), "g/s",
            L(
                "maf",
                HealthEvaluator.maf(
                    hs.mafGps, hs.rpm ?: snapshot.rpm, hs.speedKmh ?: snapshot.speedKmh,
                    hs.throttlePct ?: snapshot.throttlePct, t,
                ),
            ),
            ObdPid.MAF,
        ),
        TileData(
            "MAP", snapshot.mapKpa.fmt(), "kPa",
            L(
                "map",
                HealthEvaluator.map(
                    hs.mapKpa, hs.throttlePct ?: snapshot.throttlePct,
                    hs.rpm ?: snapshot.rpm, hs.speedKmh ?: snapshot.speedKmh, t,
                ),
            ),
            ObdPid.INTAKE_MAP,
        ),
        TileData(
            "Timing", snapshot.timingAdvance.fmt(), "\u00B0",
            L("timing", HealthEvaluator.timing(hs.timingAdvance, t)), ObdPid.TIMING_ADVANCE,
        ),
        TileData(
            "Fuel loop",
            snapshot.fuelSystemStatus?.take(12) ?: "--",
            "",
            HealthEvaluator.fuelSystem(snapshot.fuelSystemStatus, snapshot.coolantC),
            ObdPid.FUEL_SYSTEM_STATUS,
        ),
        TileData(
            "DTCs",
            dtcCount?.toString() ?: "--",
            "",
            HealthEvaluator.dtcCount(dtcCount),
            null,
        ),
        TileData(
            "Health",
            vehiclePct?.toString() ?: "--",
            if (vehiclePct != null) "%" else "",
            HealthEvaluator.vehicleHealth(vehiclePct),
            null,
        ),
    )

    val extras = extraPidIds.mapNotNull { id -> catalog.find { it.id.equals(id, true) } }
    // Cap empty "+" slots — 6 blank tiles hurt scroll on weak HUs.
    val emptySlots = (0 until 3).toList()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = dashColumnsForWidth(maxWidth)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(DashType.tileGap),
            verticalArrangement = Arrangement.spacedBy(DashType.tileGap),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
        items(baseTiles, key = { it.label }) { t ->
            val overrideId = tileOverrides[t.label]
            val overridePid = overrideId?.let { id -> catalog.find { it.id.equals(id, true) } }
            if (overridePid != null) {
                val recovered = deepFoundValues[overridePid.label] ?: deepFoundValues[overridePid.id]
                val text = recovered ?: LiveSnapshotOverlay.formatLiveOrNs(
                    overridePid,
                    snapshot,
                    fallback = extraValues[overridePid.id],
                )
                val unsupported = recovered == null && (text.startsWith("n/s") || text == "—")
                DenseTile(
                    label = overridePid.label.take(14),
                    value = text.substringBefore(" "),
                    unit = if (unsupported) "" else text.substringAfter(" ", overridePid.unit).ifBlank { overridePid.unit },
                    health = null,
                    muted = unsupported,
                    deepSearchHint = unsupported,
                    remappedHint = true,
                    freshAtMs = SnapshotFreshness.keyForTileLabel(overridePid.label)
                        ?.let { snapshot.freshAtMs[it] },
                    onDeepSearch = if (unsupported) {
                        { onDeepSearch(overridePid.label, overridePid.id) }
                    } else {
                        null
                    },
                    onRemap = { onRemapBaseTile(t.label) },
                    onEditThresholds = EditableMetric.fromTileLabel(overridePid.label)?.let { m ->
                        { onEditThresholds(m) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val unsupported = t.pid != null && t.pid.number in snapshot.unsupportedPids
                val recovered = deepFoundValues[t.label]
                // Prefer a live or recovered value over the support-bitmask "n/s"
                // (Battery often works via ATRV even when ECU omits 0142).
                val hasLive = t.value != "--" && t.value.isNotBlank()
                val showNs = unsupported && recovered == null && !hasLive
                val value = when {
                    recovered != null -> recovered.substringBefore(" ")
                    hasLive -> t.value
                    unsupported -> "n/s"
                    else -> t.value
                }
                val unit = when {
                    recovered != null -> recovered.substringAfter(" ", "")
                    hasLive -> t.unit
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
                    freshAtMs = t.pid?.let { SnapshotFreshness.keyFor(it) }
                        ?.let { snapshot.freshAtMs[it] }
                        ?.takeUnless { showNs },
                    onDeepSearch = if (showNs) {
                        { onDeepSearch(t.label, t.pid?.request) }
                    } else {
                        null
                    },
                    onRemap = { onRemapBaseTile(t.label) },
                    onEditThresholds = EditableMetric.fromTileLabel(t.label)?.let { m ->
                        { onEditThresholds(m) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(extras.size) { extraIndex ->
            val pid = extras[extraIndex]
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
                freshAtMs = SnapshotFreshness.keyForTileLabel(pid.label)
                    ?.let { snapshot.freshAtMs[it] }
                    ?.takeUnless { unsupported },
                onDeepSearch = if (unsupported) {
                    { onDeepSearch(pid.label, pid.id) }
                } else {
                    null
                },
                onRemap = { onRemapExtra(extraIndex) },
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
    thresholds: HealthThresholds = HealthThresholds.DEFAULT,
    snapshot: VehicleSnapshot = VehicleSnapshot.EMPTY,
    onEditThresholds: (EditableMetric) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = TextMuted, fontSize = DashType.pageTitle, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (secondaryAction != null) {
                Text(
                    text = secondaryAction.first,
                    color = TextMuted,
                    fontSize = DashType.pageTitle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { secondaryAction.second() }
                        .background(Surface)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .padding(end = 6.dp),
                )
            }
            Text(
                text = action.first,
                color = Accent,
                fontSize = DashType.pageTitle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { action.second() }
                    .background(Surface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        if (!tip.isNullOrBlank()) {
            Text(
                text = "Tip: $tip",
                color = WarnAmber,
                fontSize = DashType.pageTitle,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = denseColumnsForWidth(maxWidth)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(DashType.tileGap),
                verticalArrangement = Arrangement.spacedBy(DashType.tileGap),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
            items(rows, key = { it.first }) { (label, value) ->
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
                val transStatus = if (unsupported) {
                    null
                } else {
                    val fuelLoopLabel = label.contains("fuel system", true) ||
                        label.contains("fuel loop", true)
                    when {
                        fuelLoopLabel -> HealthEvaluator.fuelSystem(effective, snapshot.coolantC)
                        else -> HealthEvaluator.forTransmissionLabel(label, effective, thresholds)
                            ?: EditableMetric.fromTileLabel(label)?.let { metric ->
                                // Custom / Idle / Fuel pages: colour by known metric if label matches.
                                when (metric) {
                                    EditableMetric.COOLANT -> HealthEvaluator.coolant(
                                        effective.substringBefore(" ").toDoubleOrNull(),
                                        thresholds,
                                    )
                                    EditableMetric.BATTERY -> HealthEvaluator.battery(
                                        effective.substringBefore(" ").toDoubleOrNull(),
                                        true,
                                        thresholds,
                                        rpm = snapshot.rpm,
                                    )
                                    EditableMetric.FUEL_TRIM -> HealthEvaluator.fuelTrim(
                                        effective.substringBefore(" ").toDoubleOrNull(),
                                        thresholds,
                                    )
                                    EditableMetric.ENGINE_LOAD -> HealthEvaluator.engineLoad(
                                        effective.substringBefore(" ").toDoubleOrNull(),
                                        thresholds,
                                    )
                                    EditableMetric.INTAKE -> HealthEvaluator.intakeAir(
                                        effective.substringBefore(" ").toDoubleOrNull(),
                                        thresholds,
                                    )
                                    EditableMetric.MAF -> HealthEvaluator.maf(
                                        effective.substringBefore(" ").toDoubleOrNull(),
                                        snapshot.rpm,
                                        snapshot.speedKmh,
                                        snapshot.throttlePct,
                                        thresholds,
                                    )
                                    EditableMetric.MAP -> HealthEvaluator.map(
                                        effective.substringBefore(" ").toDoubleOrNull(),
                                        snapshot.throttlePct,
                                        snapshot.rpm,
                                        snapshot.speedKmh,
                                        thresholds,
                                    )
                                    EditableMetric.TIMING -> HealthEvaluator.timing(
                                        effective.substringBefore(" ").toDoubleOrNull(),
                                        thresholds,
                                    )
                                    EditableMetric.RPM -> HealthEvaluator.rpm(
                                        effective.substringBefore(" ").toDoubleOrNull(),
                                        thresholds,
                                    )
                                    EditableMetric.ATF -> HealthEvaluator.atfTemp(
                                        effective.substringBefore(" ").toDoubleOrNull(),
                                        thresholds,
                                    )
                                    EditableMetric.TC_SLIP -> HealthEvaluator.tcSlip(
                                        effective.substringBefore(" ").toDoubleOrNull(),
                                        thresholds,
                                    )
                                    else -> null
                                }
                            }
                    }
                }
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
                    onEditThresholds = EditableMetric.fromTileLabel(label)?.let { m ->
                        { onEditThresholds(m) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
    remappedHint: Boolean = false,
    freshAtMs: Long? = null,
    onDeepSearch: (() -> Unit)? = null,
    onRemap: (() -> Unit)? = null,
    onEditThresholds: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val valueColor = when {
        muted -> TextMuted
        health == null || health == Health.UNKNOWN -> TextPrimary
        else -> health.color()
    }
    var taps by remember { mutableIntStateOf(0) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    var pendingRemap by remember { mutableStateOf<Job?>(null) }

    Column(
        modifier = modifier
            .height(DashType.tileH)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .combinedClickable(
                onClick = {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTapMs < 520L) taps + 1 else 1
                    lastTapMs = now
                    pendingRemap?.cancel()
                    when {
                        taps >= 3 -> {
                            taps = 0
                            onDeepSearch?.invoke()
                        }
                        taps == 2 && onRemap != null -> {
                            pendingRemap = scope.launch {
                                delay(280)
                                if (taps == 2) {
                                    taps = 0
                                    onRemap()
                                }
                            }
                        }
                    }
                },
                onLongClick = {
                    onEditThresholds?.invoke()
                },
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (health != null && health != Health.UNKNOWN) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(health.color()),
                )
                Text(
                    text = " $label",
                    color = TextMuted,
                    fontSize = DashType.tileLabel,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = tightTextStyle(DashType.tileLabel),
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                Text(
                    text = label,
                    color = TextMuted,
                    fontSize = DashType.tileLabel,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = tightTextStyle(DashType.tileLabel),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            // Torque-style freshness blink (separate from health traffic-light dot).
            FreshnessHeartbeat(
                lastOkMs = if (muted) null else freshAtMs,
                size = 8.dp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontSize = DashType.tileValue,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tightTextStyle(DashType.tileValue),
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    color = TextMuted,
                    fontSize = DashType.tileUnit,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = tightTextStyle(DashType.tileUnit),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        when {
            deepSearchHint -> Text(
                "tap×3 deep · 2× change",
                color = Accent,
                fontSize = DashType.tileHint,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = tightTextStyle(DashType.tileHint),
            )
            remappedHint -> Text(
                "2× change",
                color = Accent,
                fontSize = DashType.tileHint,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = tightTextStyle(DashType.tileHint),
            )
            !statusLabel.isNullOrBlank() && !muted -> Text(
                text = statusLabel,
                color = health?.color() ?: TextMuted,
                fontSize = DashType.tileStatus,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tightTextStyle(DashType.tileStatus),
            )
            onRemap != null -> Text(
                "2× change",
                color = TextMuted,
                fontSize = DashType.tileHint,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = tightTextStyle(DashType.tileHint),
            )
            else -> Text(" ", fontSize = DashType.tileHint, style = tightTextStyle(DashType.tileHint))
        }
    }
}

@Composable
private fun EmptyTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(DashType.tileH)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "+", color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "add",
            color = TextMuted,
            fontSize = DashType.tileHint,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
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
 * Type in the search box to jump straight to matching sensors.
 */
@Composable
private fun SensorPickerDialog(
    catalog: List<PidDefinition>,
    onPick: (PidDefinition) -> Unit,
    onDismiss: () -> Unit,
    restoreLabel: String? = null,
    onRestore: (() -> Unit)? = null,
) {
    var category by remember { mutableStateOf<PidCategory?>(null) }
    var subProfile by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val searching = q.length >= 2

    val title = when {
        searching -> "Search sensors"
        category == null -> "Add sensor — pick category"
        subProfile == null -> category!!.displayName()
        else -> "${category!!.displayName()} › ${profileDisplayName(subProfile!!)}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.82f)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true,
                    placeholder = {
                        Text("Type to search (e.g. gear, MAF, 01A4)", color = TextMuted, fontSize = 12.sp)
                    },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                )
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (restoreLabel != null && onRestore != null && !searching) {
                        PickerRow(
                            title = "Restore default",
                            subtitle = restoreLabel,
                            trailing = "↺",
                            onClick = onRestore,
                        )
                    }
                    if (searching) {
                        val hits = catalog.filter { pid ->
                            pid.label.contains(q, true) ||
                                pid.request.contains(q, true) ||
                                pid.id.contains(q, true) ||
                                pid.unit.contains(q, true) ||
                                pid.category.displayName().contains(q, true) ||
                                profileDisplayName(pid.profile).contains(q, true)
                        }.sortedBy { it.label }.take(60)
                        if (hits.isEmpty()) {
                            Text(
                                text = "No sensors match \"$q\"",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            hits.forEach { pid ->
                                PickerRow(
                                    title = pid.label,
                                    subtitle = listOfNotNull(
                                        pid.request,
                                        pid.unit.takeIf { it.isNotBlank() },
                                        pid.category.displayName(),
                                    ).joinToString(" · "),
                                    trailing = "+",
                                    onClick = { onPick(pid) },
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Scroll categories, or type above to search",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
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
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Accent) }
        },
        containerColor = Background,
    )
}

private sealed class PickerTarget {
    data class ExtraSlot(val index: Int) : PickerTarget()
    data class RemapBase(val label: String) : PickerTarget()
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
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trailing, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TopBarChip(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = DashType.topChip,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun TopBar(
    state: DashboardUiState,
    loggingActive: Boolean,
    profileBadge: String = VehicleProfile.FB2.badge,
    onConnectClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onToggleLogging: () -> Unit,
    onMinimizeClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "FB2 DIAG",
                color = Accent,
                fontSize = DashType.topTitle,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "  $profileBadge",
                color = TextMuted,
                fontSize = DashType.topChip,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            val (dot, text) = when {
                state.reconnecting ||
                    (state.connection == ConnectionState.CONNECTING && state.sourceIsLive) ->
                    WarnAmber to "RETRY"
                state.connection == ConnectionState.CONNECTED && state.sourceIsLive ->
                    GoodGreen to "LIVE"
                state.connection == ConnectionState.CONNECTED && !state.sourceIsLive ->
                    WarnAmber to "DEMO"
                state.connection == ConnectionState.CONNECTING -> Accent to "…"
                state.connection == ConnectionState.ERROR -> CritRed to "ERR"
                else -> TextMuted to "OFF"
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(dot),
            )
            Text(
                text = " $text",
                color = TextMuted,
                fontSize = DashType.topChip,
                fontWeight = FontWeight.Bold,
            )

            TopBarChip(if (loggingActive) "STOP LOG" else "LOG", if (loggingActive) CritRed else Accent, onToggleLogging)
            TopBarChip("MIN", Accent, onMinimizeClick)
            TopBarChip("DIAG", Accent, onDiagnosticsClick)
            TopBarChip("SETTINGS", TextMuted, onSettingsClick)
            // CONNECTED only for a real ELM adapter — Demo keeps CONNECT (+ yellow DEMO badge).
            val liveConnected = state.connection == ConnectionState.CONNECTED &&
                state.sourceIsLive && !state.reconnecting
            TopBarChip(
                text = when {
                    liveConnected -> "CONNECTED"
                    state.reconnecting || state.connection == ConnectionState.CONNECTING -> "RETRY…"
                    state.connection == ConnectionState.ERROR -> "RECONNECT"
                    else -> "CONNECT"
                },
                color = when {
                    liveConnected -> GoodGreen
                    state.connection == ConnectionState.ERROR -> CritRed
                    else -> Accent
                },
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
