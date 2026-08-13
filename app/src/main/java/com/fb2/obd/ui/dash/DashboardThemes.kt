@file:OptIn(ExperimentalFoundationApi::class)

package com.fb2.obd.ui.dash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.obd.EditableMetric
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.MetricStatus
import com.fb2.obd.obd.SnapshotFreshness
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.FreshnessHeartbeat
import com.fb2.obd.ui.color
import com.fb2.obd.ui.theme.ThemePalette
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DigitFace = FontFamily.SansSerif

@Composable
fun OptAThemeDash(
    snapshot: VehicleSnapshot,
    healthSnapshot: VehicleSnapshot,
    thresholds: HealthThresholds,
    gearSource: GearSource,
    gearConfidencePct: Int?,
    dtcCount: Int?,
    healthScore: HealthScore?,
    latchHealth: (String, MetricStatus) -> MetricStatus,
    palette: ThemePalette,
    onRemapBase: (String) -> Unit,
    onDeepSearch: (label: String, pidId: String?) -> Unit,
    onEditThresholds: (EditableMetric) -> Unit,
    rootModifier: Modifier = Modifier,
) {
    val all = remember(snapshot, healthSnapshot, thresholds, dtcCount, healthScore) {
        DashThemeMetrics.sideMetrics(
            snapshot, healthSnapshot, thresholds, dtcCount, healthScore, latchHealth,
        )
    }
    val (left, right) = remember(all) { DashThemeMetrics.splitWheels(all) }
    val rpmFrac = ((snapshot.rpm ?: 0.0) / 8000.0).coerceIn(0.0, 1.0).toFloat()
    val speedFrac = ((snapshot.speedKmh ?: 0.0) / 200.0).coerceIn(0.0, 1.0).toFloat()

    Box(
        modifier = rootModifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2A0A0E), palette.background),
                    radius = 900f,
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawOval(
                color = palette.glow,
                topLeft = Offset(size.width * 0.18f, size.height * 0.12f),
                size = Size(size.width * 0.64f, size.height * 0.76f),
                style = Stroke(width = 10f),
            )
            drawOval(
                brush = Brush.radialGradient(listOf(Color(0x33E53935), Color.Transparent)),
                topLeft = Offset(size.width * 0.22f, size.height * 0.16f),
                size = Size(size.width * 0.56f, size.height * 0.68f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitWheel(
                metrics = left,
                palette = palette,
                onRemapBase = onRemapBase,
                onDeepSearch = onDeepSearch,
                onEditThresholds = onEditThresholds,
                modifier = Modifier
                    .weight(0.18f)
                    .fillMaxHeight(0.92f),
            )

            Column(
                modifier = Modifier
                    .weight(0.24f)
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OrbitValuePanel(
                    label = "RPM",
                    value = snapshot.rpm?.roundToInt()?.toString() ?: "--",
                    unit = "rpm",
                    fraction = rpmFrac,
                    midLabel = "4000",
                    maxLabel = "8000",
                    freshAtMs = snapshot.freshAtMs[SnapshotFreshness.KEY_RPM],
                    palette = palette,
                    onRemap = { onRemapBase("RPM") },
                    onDeepSearch = { onDeepSearch("RPM", "010C") },
                    onEdit = { onEditThresholds(EditableMetric.RPM) },
                )
            }

            Column(
                modifier = Modifier
                    .weight(0.16f)
                    .fillMaxHeight(0.78f)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF2A1016), Color(0xFF12080A))))
                    .border(2.dp, palette.accent.copy(alpha = 0.8f), CircleShape)
                    .padding(8.dp)
                    .themeMetricGestures(
                        onRemap = { onRemapBase("Gear") },
                        onDeepSearch = { onDeepSearch("Gear", null) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("GEAR", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (gearSource == GearSource.NONE) "–" else (snapshot.gear?.toString() ?: "–"),
                    color = palette.textPrimary,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DigitFace,
                    maxLines = 1,
                )
                Canvas(modifier = Modifier.size(width = 64.dp, height = 16.dp)) {
                    val segs = 7
                    for (i in 0 until segs) {
                        drawArc(
                            color = palette.accent.copy(alpha = if (i % 2 == 0) 1f else 0.35f),
                            startAngle = 20f + i * (140f / segs),
                            sweepAngle = (140f / segs) - 2f,
                            useCenter = false,
                            style = Stroke(width = 5f, cap = StrokeCap.Butt),
                        )
                    }
                }
                val badge = when (gearSource) {
                    GearSource.ECU -> "ECU"
                    GearSource.ESTIMATED -> gearConfidencePct?.let { "$it%" } ?: "EST"
                    GearSource.NONE -> " "
                }
                Text(badge, color = palette.accentSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .weight(0.24f)
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OrbitValuePanel(
                    label = "SPEED",
                    value = snapshot.speedKmh?.roundToInt()?.toString() ?: "--",
                    unit = "km/h",
                    fraction = speedFrac,
                    midLabel = "100",
                    maxLabel = "200",
                    freshAtMs = snapshot.freshAtMs[SnapshotFreshness.KEY_SPEED],
                    palette = palette,
                    onRemap = { onRemapBase("Speed") },
                    onDeepSearch = { onDeepSearch("Speed", "010D") },
                )
            }

            OrbitWheel(
                metrics = right,
                palette = palette,
                onRemapBase = onRemapBase,
                onDeepSearch = onDeepSearch,
                onEditThresholds = onEditThresholds,
                modifier = Modifier
                    .weight(0.18f)
                    .fillMaxHeight(0.92f),
            )
        }
    }
}

@Composable
fun OptBThemeDash(
    snapshot: VehicleSnapshot,
    healthSnapshot: VehicleSnapshot,
    thresholds: HealthThresholds,
    gearSource: GearSource,
    dtcCount: Int?,
    healthScore: HealthScore?,
    latchHealth: (String, MetricStatus) -> MetricStatus,
    palette: ThemePalette,
    onRemapBase: (String) -> Unit,
    onDeepSearch: (label: String, pidId: String?) -> Unit,
    onEditThresholds: (EditableMetric) -> Unit,
    rootModifier: Modifier = Modifier,
) {
    val metrics = remember(snapshot, healthSnapshot, thresholds, dtcCount, healthScore) {
        DashThemeMetrics.sideMetrics(
            snapshot, healthSnapshot, thresholds, dtcCount, healthScore, latchHealth,
        ).take(6)
    }
    val rpmFrac = ((snapshot.rpm ?: 0.0) / 8000.0).coerceIn(0.0, 1.0).toFloat()
    val speedFrac = ((snapshot.speedKmh ?: 0.0) / 240.0).coerceIn(0.0, 1.0).toFloat()

    Column(
        modifier = rootModifier
            .fillMaxSize()
            .background(palette.background)
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RealisticNeedleGauge(
                valueText = snapshot.rpm?.roundToInt()?.toString() ?: "--",
                unit = "rpm",
                fraction = rpmFrac,
                majorTicks = 8,
                arcColor = palette.accent,
                redlineFrom = 6.5f / 8f,
                freshAtMs = snapshot.freshAtMs[SnapshotFreshness.KEY_RPM],
                palette = palette,
                modifier = Modifier
                    .weight(1f)
                    .themeMetricGestures(
                        onRemap = { onRemapBase("RPM") },
                        onDeepSearch = { onDeepSearch("RPM", "010C") },
                        onEditThresholds = { onEditThresholds(EditableMetric.RPM) },
                    ),
            )
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight(0.72f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.accent.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                    .padding(vertical = 12.dp)
                    .themeMetricGestures(
                        onRemap = { onRemapBase("Gear") },
                        onDeepSearch = { onDeepSearch("Gear", null) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text("GEAR", color = palette.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.width(28.dp).height(2.dp).background(palette.accent))
                Text(
                    text = if (gearSource == GearSource.NONE) "–" else (snapshot.gear?.toString() ?: "–"),
                    color = palette.textPrimary,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DigitFace,
                )
                Text(
                    text = when (gearSource) {
                        GearSource.ECU -> "ECU"
                        GearSource.ESTIMATED -> "EST"
                        GearSource.NONE -> "—"
                    },
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .border(1.dp, palette.accent, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            RealisticNeedleGauge(
                valueText = snapshot.speedKmh?.roundToInt()?.toString() ?: "--",
                unit = "km/h",
                fraction = speedFrac,
                majorTicks = 6,
                arcColor = palette.good,
                redlineFrom = 1.1f,
                freshAtMs = snapshot.freshAtMs[SnapshotFreshness.KEY_SPEED],
                palette = palette,
                modifier = Modifier
                    .weight(1f)
                    .themeMetricGestures(
                        onRemap = { onRemapBase("Speed") },
                        onDeepSearch = { onDeepSearch("Speed", "010D") },
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            metrics.forEach { m ->
                TwinBottomChip(
                    metric = m,
                    palette = palette,
                    onRemapBase = onRemapBase,
                    onDeepSearch = onDeepSearch,
                    onEditThresholds = onEditThresholds,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun OptCThemeDash(
    snapshot: VehicleSnapshot,
    healthSnapshot: VehicleSnapshot,
    thresholds: HealthThresholds,
    gearSource: GearSource,
    gearConfidencePct: Int?,
    dtcCount: Int?,
    healthScore: HealthScore?,
    latchHealth: (String, MetricStatus) -> MetricStatus,
    palette: ThemePalette,
    onRemapBase: (String) -> Unit,
    onDeepSearch: (label: String, pidId: String?) -> Unit,
    onEditThresholds: (EditableMetric) -> Unit,
    rootModifier: Modifier = Modifier,
) {
    val metrics = remember(snapshot, healthSnapshot, thresholds, dtcCount, healthScore) {
        // Sample order: Coolant, Battery, Intake, Throttle, MAP, MAF, STFT, Timing, Health
        val preferred = listOf(
            "Coolant 1", "Battery", "Intake", "Throttle", "MAP", "MAF", "STFT", "Timing", "Health",
        )
        val all = DashThemeMetrics.sideMetrics(
            snapshot, healthSnapshot, thresholds, dtcCount, healthScore, latchHealth,
        )
        preferred.mapNotNull { name -> all.firstOrNull { it.label.equals(name, true) } }
            .ifEmpty { all.take(9) }
    }
    val rpmFrac = ((snapshot.rpm ?: 0.0) / 7000.0).coerceIn(0.0, 1.0).toFloat()

    Column(
        modifier = rootModifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1018), palette.background)))
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.44f)
                .clip(RoundedCornerShape(28.dp))
                .background(palette.surface)
                .border(1.dp, palette.accent.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(0.34f)
                    .fillMaxHeight()
                    .themeMetricGestures(
                        onRemap = { onRemapBase("RPM") },
                        onDeepSearch = { onDeepSearch("RPM", "010C") },
                        onEditThresholds = { onEditThresholds(EditableMetric.RPM) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                PulseRpmGauge(fraction = rpmFrac, palette = palette)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FreshnessHeartbeat(
                            lastOkMs = snapshot.freshAtMs[SnapshotFreshness.KEY_RPM],
                            size = 7.dp,
                        )
                        Text(" RPM", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = snapshot.rpm?.roundToInt()?.toString() ?: "--",
                        color = palette.textPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DigitFace,
                    )
                    Text("rpm", color = palette.accentSoft, fontSize = 11.sp)
                }
            }

            Box(
                modifier = Modifier
                    .weight(0.32f)
                    .themeMetricGestures(
                        onRemap = { onRemapBase("Gear") },
                        onDeepSearch = { onDeepSearch("Gear", null) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(118.dp)) {
                    drawArc(
                        color = palette.accent.copy(alpha = 0.9f),
                        startAngle = 105f,
                        sweepAngle = 35f,
                        useCenter = false,
                        style = Stroke(width = 5f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = palette.accent.copy(alpha = 0.9f),
                        startAngle = 40f,
                        sweepAngle = 35f,
                        useCenter = false,
                        style = Stroke(width = 5f, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GEAR", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (gearSource == GearSource.NONE) "–" else (snapshot.gear?.toString() ?: "–"),
                        color = palette.accent,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DigitFace,
                    )
                    Text(
                        text = when (gearSource) {
                            GearSource.ECU -> "ECU"
                            GearSource.ESTIMATED -> gearConfidencePct?.let { "$it%" } ?: "EST"
                            GearSource.NONE -> "—"
                        },
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .border(1.dp, palette.accent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.34f)
                    .themeMetricGestures(
                        onRemap = { onRemapBase("Speed") },
                        onDeepSearch = { onDeepSearch("Speed", "010D") },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FreshnessHeartbeat(
                        lastOkMs = snapshot.freshAtMs[SnapshotFreshness.KEY_SPEED],
                        size = 7.dp,
                    )
                    Text(" SPEED", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(width = 150.dp, height = 52.dp)) {
                        for (i in 0..5) {
                            drawLine(
                                color = palette.accent.copy(alpha = 0.12f + i * 0.06f),
                                start = Offset(0f, size.height * 0.25f + i * 5f),
                                end = Offset(size.width * 0.42f, size.height * 0.25f + i * 5f),
                                strokeWidth = 2.5f,
                            )
                        }
                    }
                    Text(
                        text = snapshot.speedKmh?.roundToInt()?.toString() ?: "--",
                        color = palette.textPrimary,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DigitFace,
                        maxLines = 1,
                    )
                }
                Text("km/h", color = palette.accentSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().weight(0.56f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(metrics, key = { it.label }) { m ->
                PulseCard(m, palette, onRemapBase, onDeepSearch, onEditThresholds)
            }
        }
    }
}

@Composable
private fun OrbitValuePanel(
    label: String,
    value: String,
    unit: String,
    fraction: Float,
    midLabel: String,
    maxLabel: String,
    freshAtMs: Long?,
    palette: ThemePalette,
    onRemap: () -> Unit,
    onDeepSearch: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceAlt)
            .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .themeMetricGestures(onRemap, onDeepSearch, onEdit)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FreshnessHeartbeat(lastOkMs = freshAtMs, size = 7.dp)
            Text(" $label", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = value,
            color = palette.textPrimary,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            fontFamily = DigitFace,
            maxLines = 1,
        )
        Text(unit, color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(palette.accent, palette.accentSoft))),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", color = palette.textMuted, fontSize = 9.sp)
            Text(midLabel, color = palette.textMuted, fontSize = 9.sp)
            Text(maxLabel, color = palette.textMuted, fontSize = 9.sp)
        }
    }
}

/**
 * Shows 3 metrics at once (above / focus / below) like the Red Orbit sample.
 * Vertical drag rotates the wheel.
 */
@Composable
private fun OrbitWheel(
    metrics: List<DashThemeMetric>,
    palette: ThemePalette,
    onRemapBase: (String) -> Unit,
    onDeepSearch: (label: String, pidId: String?) -> Unit,
    onEditThresholds: (EditableMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = metrics.ifEmpty { listOf(DashThemeMetric("–", "--", "")) }
    var center by remember(pages.size) { mutableIntStateOf(pages.size.coerceAtMost(1).let { if (pages.size > 1) 1 else 0 }) }
    var dragAcc by remember { mutableFloatStateOf(0f) }

    fun idx(delta: Int): Int {
        if (pages.isEmpty()) return 0
        var i = (center + delta) % pages.size
        if (i < 0) i += pages.size
        return i
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surface)
            .border(2.dp, palette.accent.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
            .pointerInput(pages.size) {
                detectVerticalDragGestures(
                    onDragEnd = { dragAcc = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAcc += dragAmount
                        if (dragAcc > 42f) {
                            center = idx(-1)
                            dragAcc = 0f
                        } else if (dragAcc < -42f) {
                            center = idx(1)
                            dragAcc = 0f
                        }
                    },
                )
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("▴", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OrbitWheelItem(
                metric = pages[idx(-1)],
                focused = false,
                palette = palette,
                onRemapBase = onRemapBase,
                onDeepSearch = onDeepSearch,
                onEditThresholds = onEditThresholds,
            )
            // Focus notch markers
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(6.dp).height(2.dp).background(palette.accent))
                OrbitWheelItem(
                    metric = pages[idx(0)],
                    focused = true,
                    palette = palette,
                    onRemapBase = onRemapBase,
                    onDeepSearch = onDeepSearch,
                    onEditThresholds = onEditThresholds,
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.width(6.dp).height(2.dp).background(palette.accent))
            }
            OrbitWheelItem(
                metric = pages[idx(1)],
                focused = false,
                palette = palette,
                onRemapBase = onRemapBase,
                onDeepSearch = onDeepSearch,
                onEditThresholds = onEditThresholds,
            )
        }
        Text("▾", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OrbitWheelItem(
    metric: DashThemeMetric,
    focused: Boolean,
    palette: ThemePalette,
    onRemapBase: (String) -> Unit,
    onDeepSearch: (label: String, pidId: String?) -> Unit,
    onEditThresholds: (EditableMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (remap, deep, edit) = metric.interaction(onRemapBase, onDeepSearch, onEditThresholds)
    Column(
        modifier = modifier
            .then(
                if (focused) {
                    Modifier
                        .padding(horizontal = 2.dp)
                        .border(1.dp, palette.accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(vertical = 4.dp)
                } else {
                    Modifier.padding(vertical = 2.dp)
                },
            )
            .themeMetricGestures(remap, deep, edit),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FreshnessHeartbeat(lastOkMs = metric.freshAtMs, size = if (focused) 7.dp else 5.dp)
            Text(
                text = " ${metric.label.uppercase()}",
                color = palette.accentSoft.copy(alpha = if (focused) 1f else 0.65f),
                fontSize = if (focused) 9.sp else 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = metric.value,
            color = when {
                metric.health == Health.UNKNOWN -> palette.textPrimary
                else -> metric.health.color()
            }.copy(alpha = if (focused) 1f else 0.7f),
            fontSize = if (focused) 20.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = DigitFace,
            maxLines = 1,
        )
        if (metric.unit.isNotEmpty()) {
            Text(
                metric.unit,
                color = palette.textMuted,
                fontSize = if (focused) 10.sp else 9.sp,
            )
        }
    }
}

@Composable
private fun RealisticNeedleGauge(
    valueText: String,
    unit: String,
    fraction: Float,
    majorTicks: Int,
    arcColor: Color,
    redlineFrom: Float,
    freshAtMs: Long?,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.42f
            val start = 135f
            val sweep = 270f
            val track = Stroke(width = 16f, cap = StrokeCap.Round)

            drawArc(
                color = palette.track,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = track,
            )
            val f = fraction.coerceIn(0f, 1f)
            val goodEnd = f.coerceAtMost(redlineFrom.coerceAtMost(1f))
            drawArc(
                color = arcColor,
                startAngle = start,
                sweepAngle = sweep * goodEnd,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = track,
            )
            if (f > redlineFrom && redlineFrom < 1f) {
                drawArc(
                    color = palette.critical,
                    startAngle = start + sweep * redlineFrom,
                    sweepAngle = sweep * (f - redlineFrom),
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2, r * 2),
                    style = track,
                )
            }

            // Tick marks
            for (i in 0..majorTicks) {
                val a = Math.toRadians((start + sweep * i / majorTicks).toDouble())
                val outer = r + 4f
                val inner = r - 14f
                drawLine(
                    color = palette.textMuted.copy(alpha = 0.85f),
                    start = Offset(cx + outer * cos(a).toFloat(), cy + outer * sin(a).toFloat()),
                    end = Offset(cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat()),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }

            // Needle body (tapered) + glow
            val na = Math.toRadians((start + sweep * f).toDouble())
            val tip = Offset(cx + (r - 8f) * cos(na).toFloat(), cy + (r - 8f) * sin(na).toFloat())
            val back = Offset(cx - 18f * cos(na).toFloat(), cy - 18f * sin(na).toFloat())
            val perp = na + Math.PI / 2
            val halfW = 5.5f
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(
                    (back.x + halfW * cos(perp)).toFloat(),
                    (back.y + halfW * sin(perp)).toFloat(),
                )
                lineTo(
                    (back.x - halfW * cos(perp)).toFloat(),
                    (back.y - halfW * sin(perp)).toFloat(),
                )
                close()
            }
            drawPath(path, color = arcColor.copy(alpha = 0.35f))
            drawPath(path, color = arcColor)
            // Hub
            drawCircle(color = Color(0xFF101820), radius = 14f, center = Offset(cx, cy))
            drawCircle(color = arcColor, radius = 9f, center = Offset(cx, cy))
            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 3.5f, center = Offset(cx, cy))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 70.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FreshnessHeartbeat(lastOkMs = freshAtMs, size = 7.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = valueText,
                    color = arcColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DigitFace,
                )
            }
            Text(unit, color = palette.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TwinBottomChip(
    metric: DashThemeMetric,
    palette: ThemePalette,
    onRemapBase: (String) -> Unit,
    onDeepSearch: (label: String, pidId: String?) -> Unit,
    onEditThresholds: (EditableMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frac = guessFraction(metric)
    val (remap, deep, edit) = metric.interaction(onRemapBase, onDeepSearch, onEditThresholds)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .themeMetricGestures(remap, deep, edit)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(iconFor(metric.label), color = palette.accent, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                metric.label.uppercase(),
                color = palette.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FreshnessHeartbeat(lastOkMs = metric.freshAtMs, size = 7.dp)
        }
        Text(
            text = buildString {
                append(metric.value)
                if (metric.unit.isNotEmpty()) append(' ').append(metric.unit)
            },
            color = palette.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = DigitFace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .background(palette.accent),
            )
        }
    }
}

@Composable
private fun PulseRpmGauge(fraction: Float, palette: ThemePalette) {
    Canvas(modifier = Modifier.size(150.dp)) {
        val stroke = Stroke(width = 14f, cap = StrokeCap.Round)
        drawArc(
            color = palette.track,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            style = stroke,
        )
        drawArc(
            color = palette.accent,
            startAngle = 135f,
            sweepAngle = 270f * fraction,
            useCenter = false,
            style = stroke,
        )
        // Scale ticks 0..7
        for (i in 0..7) {
            val a = Math.toRadians(135.0 + 270.0 * i / 7.0)
            val rOuter = size.minDimension * 0.46f
            val rInner = size.minDimension * 0.40f
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(
                color = palette.textMuted.copy(alpha = 0.7f),
                start = Offset(cx + rInner * cos(a).toFloat(), cy + rInner * sin(a).toFloat()),
                end = Offset(cx + rOuter * cos(a).toFloat(), cy + rOuter * sin(a).toFloat()),
                strokeWidth = 2.5f,
            )
        }
    }
}

@Composable
private fun PulseCard(
    m: DashThemeMetric,
    palette: ThemePalette,
    onRemapBase: (String) -> Unit,
    onDeepSearch: (label: String, pidId: String?) -> Unit,
    onEditThresholds: (EditableMetric) -> Unit,
) {
    val (remap, deep, edit) = m.interaction(onRemapBase, onDeepSearch, onEditThresholds)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceAlt)
            .border(1.dp, palette.accent.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .themeMetricGestures(remap, deep, edit)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(iconFor(m.label), color = palette.accent, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                m.label.uppercase(),
                color = palette.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = buildString {
                    append(m.value)
                    if (m.unit.isNotEmpty()) append(' ').append(m.unit)
                },
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = DigitFace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Live LED (green blink) preferred; health colour only when critical/warn
        when (m.health) {
            Health.WARN, Health.ELEVATED, Health.CRITICAL -> Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(m.health.color()),
            )
            else -> FreshnessHeartbeat(lastOkMs = m.freshAtMs, size = 8.dp)
        }
    }
}

private fun iconFor(label: String): String = when {
    label.contains("Coolant", true) -> "●"
    label.contains("Battery", true) -> "▣"
    label.contains("Intake", true) -> "◇"
    label.contains("Throttle", true) -> "◎"
    label.contains("Load", true) -> "◔"
    label.contains("MAP", true) -> "◐"
    label.contains("MAF", true) -> "≋"
    label.contains("STFT", true) -> "∿"
    label.contains("LTFT", true) -> "∿"
    label.contains("Timing", true) -> "✚"
    label.contains("Health", true) -> "⬡"
    label.contains("Ambient", true) -> "○"
    label.contains("Fuel", true) -> "▷"
    label.contains("DTC", true) -> "!"
    else -> "•"
}

private fun guessFraction(m: DashThemeMetric): Float {
    val v = m.value.toFloatOrNull() ?: return 0.35f
    return when {
        m.unit.contains("°C", true) -> (v / 120f).coerceIn(0.05f, 1f)
        m.unit.equals("V", true) -> ((v - 11f) / 4f).coerceIn(0.05f, 1f)
        m.unit.equals("%", true) -> (v / 100f).coerceIn(0.05f, 1f)
        m.unit.contains("kPa", true) -> (v / 100f).coerceIn(0.05f, 1f)
        m.unit.contains("g/s", true) -> (v / 40f).coerceIn(0.05f, 1f)
        else -> 0.4f
    }
}
