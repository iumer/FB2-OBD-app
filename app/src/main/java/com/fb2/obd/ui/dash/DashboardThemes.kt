@file:OptIn(ExperimentalFoundationApi::class)

package com.fb2.obd.ui.dash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthEvaluator
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.MetricStatus
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.color
import com.fb2.obd.ui.theme.ThemePalette
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DigitFace = FontFamily.SansSerif

/** OptA — Red Orbit (sample fidelity). */
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
        // Soft oval glow behind centre cluster
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawOval(
                color = palette.glow,
                topLeft = Offset(size.width * 0.18f, size.height * 0.12f),
                size = Size(size.width * 0.64f, size.height * 0.76f),
                style = Stroke(width = 10f),
            )
            drawOval(
                brush = Brush.radialGradient(
                    listOf(Color(0x33E53935), Color.Transparent),
                ),
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
                modifier = Modifier
                    .weight(0.18f)
                    .fillMaxHeight(0.92f),
            )

            Column(
                modifier = Modifier
                    .weight(0.24f)
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OrbitValuePanel(
                    value = snapshot.rpm?.roundToInt()?.toString() ?: "--",
                    unit = "rpm",
                    fraction = rpmFrac,
                    maxLabel = "8000",
                    palette = palette,
                )
            }

            Column(
                modifier = Modifier
                    .weight(0.16f)
                    .fillMaxHeight(0.78f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF2A1016), Color(0xFF12080A)),
                        ),
                    )
                    .border(2.dp, palette.accent.copy(alpha = 0.75f), CircleShape)
                    .padding(8.dp),
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
                Canvas(modifier = Modifier.size(width = 56.dp, height = 14.dp)) {
                    drawArc(
                        color = palette.accent,
                        startAngle = 20f,
                        sweepAngle = 140f,
                        useCenter = false,
                        style = Stroke(width = 4f, cap = StrokeCap.Round),
                    )
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
                verticalArrangement = Arrangement.Center,
            ) {
                OrbitValuePanel(
                    value = snapshot.speedKmh?.roundToInt()?.toString() ?: "--",
                    unit = "km/h",
                    fraction = speedFrac,
                    maxLabel = "200",
                    palette = palette,
                )
            }

            OrbitWheel(
                metrics = right,
                palette = palette,
                modifier = Modifier
                    .weight(0.18f)
                    .fillMaxHeight(0.92f),
            )
        }
    }
}

/** OptB — Twin Gauge (sample fidelity). */
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
    rootModifier: Modifier = Modifier,
) {
    val metrics = remember(snapshot, healthSnapshot, thresholds, dtcCount, healthScore) {
        DashThemeMetrics.sideMetrics(
            snapshot, healthSnapshot, thresholds, dtcCount, healthScore, latchHealth,
        ).take(6)
    }
    val rpmMax = 8000.0
    val speedMax = 240.0
    val rpmFrac = ((snapshot.rpm ?: 0.0) / rpmMax).coerceIn(0.0, 1.0).toFloat()
    val speedFrac = ((snapshot.speedKmh ?: 0.0) / speedMax).coerceIn(0.0, 1.0).toFloat()

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
            NeedleGauge(
                valueText = snapshot.rpm?.roundToInt()?.toString() ?: "--",
                unit = "rpm",
                fraction = rpmFrac,
                arcColor = palette.accent,
                redlineFrom = 0.78f,
                palette = palette,
                modifier = Modifier.weight(1f),
            )
            Column(
                modifier = Modifier
                    .width(92.dp)
                    .fillMaxHeight(0.72f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.accent.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text("GEAR", color = palette.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(palette.accent),
                )
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
            NeedleGauge(
                valueText = snapshot.speedKmh?.roundToInt()?.toString() ?: "--",
                unit = "km/h",
                fraction = speedFrac,
                arcColor = palette.good,
                redlineFrom = 1.1f,
                palette = palette,
                modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** OptC — Pulse Deck (sample fidelity). */
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
    rootModifier: Modifier = Modifier,
) {
    val metrics = remember(snapshot, healthSnapshot, thresholds, dtcCount, healthScore) {
        DashThemeMetrics.sideMetrics(
            snapshot, healthSnapshot, thresholds, dtcCount, healthScore, latchHealth,
        ).take(9)
    }
    val rpmFrac = ((snapshot.rpm ?: 0.0) / 7000.0).coerceIn(0.0, 1.0).toFloat()

    Column(
        modifier = rootModifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0B1018), palette.background)),
            )
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.46f)
                .clip(RoundedCornerShape(28.dp))
                .background(palette.surface)
                .border(1.dp, palette.accent.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(0.34f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                PulseRpmGauge(fraction = rpmFrac, palette = palette)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RPM", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = snapshot.rpm?.roundToInt()?.toString() ?: "--",
                        color = palette.textPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = DigitFace,
                    )
                    Text("rpm", color = palette.accentSoft, fontSize = 11.sp)
                }
            }

            Column(
                modifier = Modifier.weight(0.32f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Side glow arcs
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(110.dp)) {
                        drawArc(
                            color = palette.accent.copy(alpha = 0.85f),
                            startAngle = 100f,
                            sweepAngle = 40f,
                            useCenter = false,
                            style = Stroke(width = 5f, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = palette.accent.copy(alpha = 0.85f),
                            startAngle = 40f,
                            sweepAngle = 40f,
                            useCenter = false,
                            style = Stroke(width = 5f, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GEAR", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            }

            Column(
                modifier = Modifier.weight(0.34f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("SPEED", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(contentAlignment = Alignment.Center) {
                    // Motion streaks
                    Canvas(modifier = Modifier.size(width = 140.dp, height = 48.dp)) {
                        for (i in 0..4) {
                            drawLine(
                                color = palette.accent.copy(alpha = 0.15f + i * 0.05f),
                                start = Offset(0f, size.height * 0.3f + i * 6f),
                                end = Offset(size.width * 0.35f, size.height * 0.3f + i * 6f),
                                strokeWidth = 2f,
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
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.54f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(metrics, key = { it.label }) { m ->
                PulseCard(m, palette)
            }
        }
    }
}

@Composable
private fun OrbitValuePanel(
    value: String,
    unit: String,
    fraction: Float,
    maxLabel: String,
    palette: ThemePalette,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceAlt)
            .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = palette.textPrimary,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            fontFamily = DigitFace,
            maxLines = 1,
        )
        Text(unit, color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
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
                    .background(
                        Brush.horizontalGradient(listOf(palette.accent, palette.accentSoft)),
                    ),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", color = palette.textMuted, fontSize = 9.sp)
            Text(maxLabel, color = palette.textMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun OrbitWheel(
    metrics: List<DashThemeMetric>,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
) {
    val pages = metrics.ifEmpty { listOf(DashThemeMetric("–", "--", "")) }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surface)
            .border(2.dp, palette.accent.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("▴", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            beyondBoundsPageCount = 1,
        ) { page ->
            val m = pages[page]
            val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val scale = (1f - offset * 0.22f).coerceIn(0.7f, 1f)
            val alpha = (1f - offset * 0.5f).coerceIn(0.35f, 1f)
            val focused = offset < 0.35f
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        rotationX = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction) * -16f
                    }
                    .then(
                        if (focused) {
                            Modifier
                                .padding(horizontal = 4.dp)
                                .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        } else {
                            Modifier
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = iconFor(m.label),
                    color = palette.accent,
                    fontSize = 14.sp,
                )
                Text(
                    text = m.label.uppercase(),
                    color = palette.accentSoft,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = m.value,
                    color = if (m.health == Health.UNKNOWN) palette.textPrimary else m.health.color(),
                    fontSize = if (focused) 22.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DigitFace,
                    maxLines = 1,
                )
                if (m.unit.isNotEmpty()) {
                    Text(m.unit, color = palette.textMuted, fontSize = 10.sp)
                }
            }
        }
        Text("▾", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NeedleGauge(
    valueText: String,
    unit: String,
    fraction: Float,
    arcColor: Color,
    redlineFrom: Float,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(190.dp)) {
            val stroke = Stroke(width = 18f, cap = StrokeCap.Round)
            val start = 135f
            val sweep = 270f
            drawArc(
                color = palette.track,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                style = stroke,
            )
            val goodSweep = sweep * fraction.coerceIn(0f, redlineFrom.coerceAtMost(1f))
            drawArc(
                color = arcColor,
                startAngle = start,
                sweepAngle = goodSweep,
                useCenter = false,
                style = stroke,
            )
            if (fraction > redlineFrom && redlineFrom < 1f) {
                drawArc(
                    color = palette.critical,
                    startAngle = start + sweep * redlineFrom,
                    sweepAngle = sweep * (fraction - redlineFrom).coerceAtMost(1f - redlineFrom),
                    useCenter = false,
                    style = stroke,
                )
            }
            // Needle
            val angle = Math.toRadians((start + sweep * fraction).toDouble())
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.38f
            drawLine(
                color = arcColor,
                start = Offset(cx, cy),
                end = Offset(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat()),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
            drawCircle(color = arcColor, radius = 8f, center = Offset(cx, cy))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = valueText,
                color = arcColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = DigitFace,
            )
            Text(unit, color = palette.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TwinBottomChip(
    metric: DashThemeMetric,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
) {
    val frac = guessFraction(metric)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(iconFor(metric.label), color = palette.accent, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                metric.label.uppercase(),
                color = palette.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        // Tick labels positions approximate 0..7
        for (i in 0..7) {
            val a = Math.toRadians((135.0 + 270.0 * i / 7.0))
            val r = size.minDimension * 0.42f
            val cx = size.width / 2f + r * cos(a).toFloat()
            val cy = size.height / 2f + r * sin(a).toFloat()
            drawCircle(color = palette.textMuted.copy(alpha = 0.5f), radius = 2f, center = Offset(cx, cy))
        }
    }
}

@Composable
private fun PulseCard(m: DashThemeMetric, palette: ThemePalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceAlt)
            .border(1.dp, palette.accent.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
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
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (m.health) {
                        Health.UNKNOWN, Health.GOOD, Health.COLD -> palette.good
                        Health.WARN -> palette.warn
                        Health.ELEVATED -> palette.warn
                        Health.CRITICAL -> palette.critical
                    },
                ),
        )
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
