@file:OptIn(ExperimentalFoundationApi::class)

package com.fb2.obd.ui.dash

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
                    colors = listOf(Color(0xFF3A0C12), Color(0xFF120508), Color(0xFF050203)),
                    radius = 1100f,
                ),
            ),
    ) {
        // Brushed metal streaks + particle dust (static — cheap)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (i in 0..28) {
                val y = h * (0.08f + i * 0.032f)
                drawLine(
                    color = Color.White.copy(alpha = if (i % 3 == 0) 0.04f else 0.018f),
                    start = Offset(0f, y),
                    end = Offset(w, y + (i % 5) * 0.4f),
                    strokeWidth = 1.2f,
                )
            }
            // Soft particle field
            val seeds = listOf(
                0.12f to 0.18f, 0.22f to 0.72f, 0.31f to 0.41f, 0.48f to 0.15f,
                0.55f to 0.68f, 0.67f to 0.33f, 0.78f to 0.55f, 0.88f to 0.22f,
                0.15f to 0.88f, 0.42f to 0.92f, 0.73f to 0.81f, 0.91f to 0.74f,
            )
            seeds.forEachIndexed { i, (nx, ny) ->
                drawCircle(
                    color = palette.accent.copy(alpha = if (i % 2 == 0) 0.22f else 0.12f),
                    radius = if (i % 3 == 0) 2.8f else 1.6f,
                    center = Offset(w * nx, h * ny),
                )
            }
            // Outer bloom ring (Red Orbit)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55E53935), Color(0x22E53935), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.48f),
                    radius = w * 0.42f,
                ),
                topLeft = Offset(w * 0.12f, h * 0.06f),
                size = Size(w * 0.76f, h * 0.88f),
            )
            drawOval(
                color = palette.accent.copy(alpha = 0.55f),
                topLeft = Offset(w * 0.16f, h * 0.10f),
                size = Size(w * 0.68f, h * 0.80f),
                style = Stroke(width = 14f),
            )
            drawOval(
                color = palette.accentSoft.copy(alpha = 0.35f),
                topLeft = Offset(w * 0.19f, h * 0.13f),
                size = Size(w * 0.62f, h * 0.74f),
                style = Stroke(width = 3f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitWheel(
                metrics = left,
                palette = palette,
                onRemapBase = onRemapBase,
                onDeepSearch = onDeepSearch,
                onEditThresholds = onEditThresholds,
                modifier = Modifier
                    .weight(0.17f)
                    .fillMaxHeight(0.94f),
            )

            Column(
                modifier = Modifier
                    .weight(0.25f)
                    .padding(horizontal = 4.dp),
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
                    .fillMaxHeight(0.82f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF3A1218), Color(0xFF1A080C), Color(0xFF0A0406)),
                        ),
                    )
                    .border(2.5.dp, palette.accent.copy(alpha = 0.9f), CircleShape)
                    .padding(6.dp)
                    .themeMetricGestures(
                        onRemap = { onRemapBase("Gear") },
                        onDeepSearch = { onDeepSearch("Gear", null) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "GEAR",
                    color = palette.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = if (gearSource == GearSource.NONE) "–" else (snapshot.gear?.toString() ?: "–"),
                    color = palette.textPrimary,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DigitFace,
                    maxLines = 1,
                )
                // Segmented gear arc (sample style)
                Canvas(modifier = Modifier.size(width = 72.dp, height = 22.dp)) {
                    val segs = 8
                    val gearN = (snapshot.gear ?: 0).coerceIn(0, segs)
                    for (i in 0 until segs) {
                        val lit = i < gearN || (gearN == 0 && i % 2 == 0)
                        drawArc(
                            color = palette.accent.copy(alpha = if (lit) 1f else 0.22f),
                            startAngle = 200f + i * (140f / segs),
                            sweepAngle = (140f / segs) - 3f,
                            useCenter = false,
                            topLeft = Offset(size.width * 0.05f, -size.height * 0.4f),
                            size = Size(size.width * 0.9f, size.height * 2.2f),
                            style = Stroke(width = 6f, cap = StrokeCap.Butt),
                        )
                    }
                }
                val badge = when (gearSource) {
                    GearSource.ECU -> "ECU"
                    GearSource.ESTIMATED -> gearConfidencePct?.let { "$it%" } ?: "EST"
                    GearSource.NONE -> " "
                }
                Text(badge, color = palette.accentSoft, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .weight(0.25f)
                    .padding(horizontal = 4.dp),
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
                    .weight(0.17f)
                    .fillMaxHeight(0.94f),
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
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0C1520), palette.background),
                    radius = 900f,
                ),
            )
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
                unit = "×1000",
                fraction = rpmFrac,
                majorTicks = 8,
                tickLabels = (0..8).map { it.toString() },
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
                    .width(92.dp)
                    .fillMaxHeight(0.74f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF152030), palette.surface)),
                    )
                    .border(1.5.dp, palette.accent.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .padding(vertical = 10.dp)
                    .themeMetricGestures(
                        onRemap = { onRemapBase("Gear") },
                        onDeepSearch = { onDeepSearch("Gear", null) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text("GEAR", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Box(modifier = Modifier.width(32.dp).height(2.dp).background(palette.accent))
                Text(
                    text = if (gearSource == GearSource.NONE) "–" else (snapshot.gear?.toString() ?: "–"),
                    color = palette.textPrimary,
                    fontSize = 56.sp,
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
                tickLabels = listOf("0", "40", "80", "120", "160", "200", "240"),
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
            .background(Brush.verticalGradient(listOf(Color(0xFF0E121A), palette.background)))
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.44f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1A2430), palette.surface),
                        radius = 500f,
                    ),
                )
                .border(1.dp, palette.accent.copy(alpha = 0.28f), RoundedCornerShape(26.dp))
                .padding(10.dp),
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
                PulseRpmGauge(fraction = rpmFrac, palette = palette, modifier = Modifier.size(168.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
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
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DigitFace,
                    )
                    Text("rpm", color = palette.accentSoft, fontSize = 12.sp)
                }
            }

            Box(
                modifier = Modifier
                    .weight(0.32f)
                    .fillMaxHeight()
                    .themeMetricGestures(
                        onRemap = { onRemapBase("Gear") },
                        onDeepSearch = { onDeepSearch("Gear", null) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(130.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    // Side arcs + motion streaks (Pulse Deck)
                    drawArc(
                        color = palette.accent.copy(alpha = 0.95f),
                        startAngle = 100f,
                        sweepAngle = 42f,
                        useCenter = false,
                        topLeft = Offset(cx - 58f, cy - 58f),
                        size = Size(116f, 116f),
                        style = Stroke(width = 6f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = palette.accent.copy(alpha = 0.95f),
                        startAngle = 38f,
                        sweepAngle = 42f,
                        useCenter = false,
                        topLeft = Offset(cx - 58f, cy - 58f),
                        size = Size(116f, 116f),
                        style = Stroke(width = 6f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = palette.accent.copy(alpha = 0.35f),
                        startAngle = 95f,
                        sweepAngle = 52f,
                        useCenter = false,
                        topLeft = Offset(cx - 68f, cy - 68f),
                        size = Size(136f, 136f),
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = palette.accent.copy(alpha = 0.35f),
                        startAngle = 32f,
                        sweepAngle = 52f,
                        useCenter = false,
                        topLeft = Offset(cx - 68f, cy - 68f),
                        size = Size(136f, 136f),
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                    )
                    // Motion streaks behind gear
                    for (i in 0..4) {
                        val y = cy - 20f + i * 10f
                        drawLine(
                            color = palette.accent.copy(alpha = 0.08f + i * 0.03f),
                            start = Offset(cx - 70f, y),
                            end = Offset(cx - 28f, y),
                            strokeWidth = 2.5f,
                        )
                        drawLine(
                            color = palette.accent.copy(alpha = 0.08f + i * 0.03f),
                            start = Offset(cx + 28f, y),
                            end = Offset(cx + 70f, y),
                            strokeWidth = 2.5f,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GEAR", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(
                        text = if (gearSource == GearSource.NONE) "–" else (snapshot.gear?.toString() ?: "–"),
                        color = palette.accent,
                        fontSize = 56.sp,
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
                    .fillMaxHeight()
                    .themeMetricGestures(
                        onRemap = { onRemapBase("Speed") },
                        onDeepSearch = { onDeepSearch("Speed", "010D") },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FreshnessHeartbeat(
                        lastOkMs = snapshot.freshAtMs[SnapshotFreshness.KEY_SPEED],
                        size = 7.dp,
                    )
                    Text(" SPEED", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.height(72.dp).fillMaxWidth(),
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        for (i in 0..6) {
                            drawLine(
                                color = palette.accent.copy(alpha = 0.10f + i * 0.05f),
                                start = Offset(0f, size.height * 0.2f + i * 5.5f),
                                end = Offset(size.width * 0.28f, size.height * 0.2f + i * 5.5f),
                                strokeWidth = 2.5f,
                            )
                        }
                    }
                    Text(
                        text = snapshot.speedKmh?.roundToInt()?.toString() ?: "--",
                        color = palette.textPrimary,
                        fontSize = 52.sp,
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
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF2A1016), palette.surfaceAlt)),
            )
            .border(1.5.dp, palette.accent.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .themeMetricGestures(onRemap, onDeepSearch, onEdit)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FreshnessHeartbeat(lastOkMs = freshAtMs, size = 7.dp)
            Text(
                " $label",
                color = palette.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
        }
        Text(
            text = value,
            color = palette.textPrimary,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            fontFamily = DigitFace,
            maxLines = 1,
        )
        Text(unit, color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(palette.accent.copy(alpha = 0.55f), palette.accent, palette.accentSoft),
                        ),
                    ),
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

/** Fixed-slot vertical slider — focus stays centered; values circulate in/out. */
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
    // Track by label so live value updates never shift the selected slot
    var centerLabel by remember {
        mutableStateOf(pages.getOrNull(1)?.label ?: pages.first().label)
    }
    val center = pages.indexOfFirst { it.label == centerLabel }.takeIf { it >= 0 } ?: 0
    val scope = rememberCoroutineScope()
    val dragPx = remember { Animatable(0f) }
    val density = LocalDensity.current

    fun idx(delta: Int): Int {
        if (pages.isEmpty()) return 0
        var i = (center + delta) % pages.size
        if (i < 0) i += pages.size
        return i
    }

    fun step(dir: Int) {
        val next = pages[idx(dir)]
        centerLabel = next.label
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2A1016), palette.surface, Color(0xFF1A080C)),
                ),
            )
            .border(2.dp, palette.accent.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
            .pointerInput(pages.size, centerLabel) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val threshold = with(density) { 28.dp.toPx() }
                            when {
                                dragPx.value > threshold -> step(-1)
                                dragPx.value < -threshold -> step(1)
                            }
                            dragPx.animateTo(0f, spring(stiffness = 500f, dampingRatio = 0.85f))
                        }
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        scope.launch {
                            dragPx.snapTo((dragPx.value + amount).coerceIn(-80f, 80f))
                        }
                    },
                )
            }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("▴", color = palette.accent.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val slotH = maxHeight / 3
            // Sliding strip of 3 fixed slots — focus frame is always the middle band
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, dragPx.value.roundToInt()) },
                ) {
                    listOf(-1, 0, 1).forEach { delta ->
                        val focused = delta == 0
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(slotH),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = pages[idx(delta)],
                                transitionSpec = {
                                    (slideInVertically { h -> h / 4 } + fadeIn()) togetherWith
                                        (slideOutVertically { h -> -h / 4 } + fadeOut())
                                },
                                label = "orbit-slot-$delta",
                                contentKey = { it.label },
                                modifier = Modifier.fillMaxSize(),
                            ) { metric ->
                                OrbitWheelItem(
                                    metric = metric,
                                    focused = focused,
                                    palette = palette,
                                    onRemapBase = onRemapBase,
                                    onDeepSearch = onDeepSearch,
                                    onEditThresholds = onEditThresholds,
                                )
                            }
                        }
                    }
                }
                // Fixed focus chrome — never moves with content height
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(slotH)
                        .padding(horizontal = 3.dp)
                        .border(1.5.dp, palette.accent.copy(alpha = 0.85f), RoundedCornerShape(14.dp)),
                )
                // Top/bottom fade → picker/slider look
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(slotH * 0.55f)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(palette.surface.copy(alpha = 0.92f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(slotH * 0.55f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, palette.surface.copy(alpha = 0.92f)),
                            ),
                        ),
                )
            }
        }
        Text("▾", color = palette.accent.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
    // Fixed-height cell so focus never jumps when labels/values change length
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .themeMetricGestures(remap, deep, edit),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ThemeIcon(
            iconKindForMetric(metric.label),
            palette.accent.copy(alpha = if (focused) 1f else 0.55f),
            size = if (focused) 16.dp else 13.dp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FreshnessHeartbeat(lastOkMs = metric.freshAtMs, size = if (focused) 7.dp else 5.dp)
            Text(
                text = " ${metric.label.uppercase()}",
                color = palette.accentSoft.copy(alpha = if (focused) 1f else 0.55f),
                fontSize = if (focused) 11.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = buildString {
                append(metric.value)
                if (metric.unit.isNotEmpty()) append(' ').append(metric.unit)
            },
            color = when {
                metric.health == Health.UNKNOWN -> palette.textPrimary
                else -> metric.health.color()
            }.copy(alpha = if (focused) 1f else 0.55f),
            fontSize = if (focused) 22.sp else 15.sp,
            fontWeight = FontWeight.Black,
            fontFamily = DigitFace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RealisticNeedleGauge(
    valueText: String,
    unit: String,
    fraction: Float,
    majorTicks: Int,
    tickLabels: List<String>,
    arcColor: Color,
    redlineFrom: Float,
    freshAtMs: Long?,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(210.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.40f
            val start = 135f
            val sweep = 270f

            // Dial depth rings
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1A2838), Color(0xFF0A1018)),
                    center = Offset(cx, cy),
                    radius = r + 28f,
                ),
                radius = r + 26f,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = Color(0xFF243040),
                radius = r + 22f,
                center = Offset(cx, cy),
                style = Stroke(width = 8f),
            )
            drawCircle(
                color = arcColor.copy(alpha = 0.25f),
                radius = r + 18f,
                center = Offset(cx, cy),
                style = Stroke(width = 2f),
            )

            val track = Stroke(width = 14f, cap = StrokeCap.Round)
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
                brush = Brush.sweepGradient(
                    colors = listOf(arcColor.copy(alpha = 0.55f), arcColor),
                    center = Offset(cx, cy),
                ),
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

            val labelPaint = Paint().apply {
                color = palette.textMuted.toArgb()
                textAlign = Paint.Align.CENTER
                textSize = 18f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            for (i in 0..majorTicks) {
                val a = Math.toRadians((start + sweep * i / majorTicks).toDouble())
                val outer = r + 6f
                val inner = r - (if (i % 2 == 0) 16f else 10f)
                drawLine(
                    color = if (i >= (redlineFrom * majorTicks).toInt() && redlineFrom < 1f) {
                        palette.critical.copy(alpha = 0.9f)
                    } else {
                        palette.textPrimary.copy(alpha = 0.75f)
                    },
                    start = Offset(cx + outer * cos(a).toFloat(), cy + outer * sin(a).toFloat()),
                    end = Offset(cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat()),
                    strokeWidth = if (i % 2 == 0) 3.5f else 2f,
                    cap = StrokeCap.Round,
                )
                val label = tickLabels.getOrNull(i)
                if (label != null) {
                    val lr = r - 28f
                    val lx = cx + lr * cos(a).toFloat()
                    val ly = cy + lr * sin(a).toFloat() + 6f
                    drawContext.canvas.nativeCanvas.drawText(label, lx, ly, labelPaint)
                }
            }

            // Glow needle + tapered body
            val na = Math.toRadians((start + sweep * f).toDouble())
            val tip = Offset(cx + (r - 6f) * cos(na).toFloat(), cy + (r - 6f) * sin(na).toFloat())
            val back = Offset(cx - 22f * cos(na).toFloat(), cy - 22f * sin(na).toFloat())
            val perp = na + Math.PI / 2
            fun needlePath(halfW: Float) = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo((back.x + halfW * cos(perp)).toFloat(), (back.y + halfW * sin(perp)).toFloat())
                lineTo((back.x - halfW * cos(perp)).toFloat(), (back.y - halfW * sin(perp)).toFloat())
                close()
            }
            drawPath(needlePath(10f), color = arcColor.copy(alpha = 0.18f))
            drawPath(needlePath(7f), color = arcColor.copy(alpha = 0.35f))
            drawPath(needlePath(4.5f), color = arcColor)
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(
                    (back.x + tip.x) / 2f,
                    (back.y + tip.y) / 2f,
                ),
                end = tip,
                strokeWidth = 1.5f,
                cap = StrokeCap.Round,
            )
            drawCircle(color = Color(0xFF0A1018), radius = 16f, center = Offset(cx, cy))
            drawCircle(color = arcColor.copy(alpha = 0.45f), radius = 13f, center = Offset(cx, cy))
            drawCircle(color = arcColor, radius = 9f, center = Offset(cx, cy))
            drawCircle(color = Color.White.copy(alpha = 0.95f), radius = 3.2f, center = Offset(cx, cy))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 78.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FreshnessHeartbeat(lastOkMs = freshAtMs, size = 7.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = valueText,
                    color = arcColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DigitFace,
                )
            }
            Text(unit, color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .border(1.dp, palette.accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .themeMetricGestures(remap, deep, edit)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeIcon(iconKindForMetric(metric.label), palette.accent, size = 13.dp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                metric.label.uppercase(),
                color = palette.textMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FreshnessHeartbeat(lastOkMs = metric.freshAtMs, size = 6.dp)
        }
        Text(
            text = buildString {
                append(metric.value)
                if (metric.unit.isNotEmpty()) append(' ').append(metric.unit)
            },
            color = palette.textPrimary,
            fontSize = 15.sp,
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
private fun PulseRpmGauge(
    fraction: Float,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(168.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.44f
        val start = 135f
        val sweep = 270f
        val f = fraction.coerceIn(0f, 1f)

        // Outer halo
        drawCircle(
            color = palette.accent.copy(alpha = 0.08f),
            radius = r + 14f,
            center = Offset(cx, cy),
        )
        drawArc(
            color = palette.track,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = 16f, cap = StrokeCap.Round),
        )
        drawArc(
            color = palette.accent.copy(alpha = 0.9f),
            startAngle = start,
            sweepAngle = sweep * f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = 16f, cap = StrokeCap.Round),
        )

        val labelPaint = Paint().apply {
            color = palette.textMuted.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 20f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        for (i in 0..7) {
            val a = Math.toRadians((start + sweep * i / 7.0))
            val rOuter = r + 6f
            val rInner = r - 12f
            drawLine(
                color = palette.textPrimary.copy(alpha = 0.7f),
                start = Offset(cx + rInner * cos(a).toFloat(), cy + rInner * sin(a).toFloat()),
                end = Offset(cx + rOuter * cos(a).toFloat(), cy + rOuter * sin(a).toFloat()),
                strokeWidth = if (i % 2 == 0) 3.5f else 2f,
            )
            val lr = r - 26f
            drawContext.canvas.nativeCanvas.drawText(
                i.toString(),
                cx + lr * cos(a).toFloat(),
                cy + lr * sin(a).toFloat() + 6f,
                labelPaint,
            )
        }

        // Orange tapered needle (Pulse Deck style — matches OptB weight, OptC color)
        val na = Math.toRadians((start + sweep * f).toDouble())
        val tip = Offset(cx + (r - 10f) * cos(na).toFloat(), cy + (r - 10f) * sin(na).toFloat())
        val back = Offset(cx - 16f * cos(na).toFloat(), cy - 16f * sin(na).toFloat())
        val perp = na + Math.PI / 2
        fun needle(halfW: Float) = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo((back.x + halfW * cos(perp)).toFloat(), (back.y + halfW * sin(perp)).toFloat())
            lineTo((back.x - halfW * cos(perp)).toFloat(), (back.y - halfW * sin(perp)).toFloat())
            close()
        }
        drawPath(needle(8f), color = palette.accent.copy(alpha = 0.25f))
        drawPath(needle(4.5f), color = palette.accent)
        drawCircle(color = Color(0xFF101820), radius = 12f, center = Offset(cx, cy))
        drawCircle(color = palette.accent, radius = 7f, center = Offset(cx, cy))
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 2.8f, center = Offset(cx, cy))
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
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceAlt)
            .border(1.dp, palette.accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .themeMetricGestures(remap, deep, edit)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeIcon(iconKindForMetric(m.label), palette.accent, size = 20.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                m.label.uppercase(),
                color = palette.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = buildString {
                    append(m.value)
                    if (m.unit.isNotEmpty()) append(' ').append(m.unit)
                },
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = DigitFace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
