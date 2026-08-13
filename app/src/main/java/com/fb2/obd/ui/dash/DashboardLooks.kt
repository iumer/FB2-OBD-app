@file:OptIn(ExperimentalFoundationApi::class)

package com.fb2.obd.ui.dash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.fb2.obd.ui.CircularGauge
import com.fb2.obd.ui.color
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.WarnAmber
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val OrbitBg = Color(0xFF14080A)
private val OrbitPanel = Color(0xFF2A1016)
private val OrbitRed = Color(0xFFE53935)
private val OrbitRedSoft = Color(0xFFFF6B63)
private val OrbitTrack = Color(0xFF4A1A22)

private val PulseBg = Color(0xFF0A1018)
private val PulsePanel = Color(0xFF15202C)
private val PulseAccent = Color(0xFFFF8A3D)

private val DigitFace = FontFamily.SansSerif

@Composable
fun RedOrbitDash(
    snapshot: VehicleSnapshot,
    healthSnapshot: VehicleSnapshot,
    thresholds: HealthThresholds,
    gearSource: GearSource,
    gearConfidencePct: Int?,
    dtcCount: Int?,
    healthScore: HealthScore?,
    latchHealth: (String, MetricStatus) -> MetricStatus,
    modifier: Modifier = Modifier,
) {
    val all = remember(snapshot, healthSnapshot, thresholds, dtcCount, healthScore) {
        DashLookMetrics.sideMetrics(
            snapshot, healthSnapshot, thresholds, dtcCount, healthScore, latchHealth,
        )
    }
    val (left, right) = remember(all) { DashLookMetrics.splitWheels(all) }
    val rpmStatus = HealthEvaluator.rpm(snapshot.rpm, thresholds)
    val rpmFrac = ((snapshot.rpm ?: 0.0) / thresholds.rpmHighMax.coerceAtLeast(1.0))
        .coerceIn(0.0, 1.0).toFloat()
    val speedFrac = ((snapshot.speedKmh ?: 0.0) / 220.0).coerceIn(0.0, 1.0).toFloat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(OrbitBg, Color(0xFF1C0A0E), OrbitBg),
                ),
            )
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetricCylinder(
                metrics = left,
                accent = OrbitRedSoft,
                panel = OrbitPanel,
                modifier = Modifier
                    .weight(0.20f)
                    .fillMaxHeight()
                    .padding(end = 4.dp),
            )

            Column(
                modifier = Modifier
                    .weight(0.22f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ProgressMetricColumn(
                    label = "RPM",
                    value = snapshot.rpm?.roundToInt()?.toString() ?: "--",
                    unit = "",
                    fraction = rpmFrac,
                    barColor = if (rpmStatus.health == Health.GOOD || rpmStatus.health == Health.UNKNOWN) {
                        OrbitRedSoft
                    } else {
                        rpmStatus.health.color()
                    },
                    track = OrbitTrack,
                )
            }

            Column(
                modifier = Modifier
                    .weight(0.16f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(OrbitPanel)
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("GEAR", color = OrbitRedSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (gearSource == GearSource.NONE) "–" else (snapshot.gear?.toString() ?: "–"),
                    color = TextPrimary,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DigitFace,
                    maxLines = 1,
                )
                val badge = when (gearSource) {
                    GearSource.ECU -> "ECU"
                    GearSource.ESTIMATED -> gearConfidencePct?.let { "$it%" } ?: "EST"
                    GearSource.NONE -> " "
                }
                Text(
                    text = badge,
                    color = when (gearSource) {
                        GearSource.ECU -> GoodGreen
                        GearSource.ESTIMATED -> WarnAmber
                        GearSource.NONE -> TextMuted
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier = Modifier
                    .weight(0.22f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ProgressMetricColumn(
                    label = "SPEED",
                    value = snapshot.speedKmh?.roundToInt()?.toString() ?: "--",
                    unit = "km/h",
                    fraction = speedFrac,
                    barColor = OrbitRed,
                    track = OrbitTrack,
                )
            }

            MetricCylinder(
                metrics = right,
                accent = OrbitRed,
                panel = OrbitPanel,
                modifier = Modifier
                    .weight(0.20f)
                    .fillMaxHeight()
                    .padding(start = 4.dp),
            )
        }
    }
}

@Composable
fun TwinGaugeDash(
    snapshot: VehicleSnapshot,
    healthSnapshot: VehicleSnapshot,
    thresholds: HealthThresholds,
    gearSource: GearSource,
    dtcCount: Int?,
    healthScore: HealthScore?,
    latchHealth: (String, MetricStatus) -> MetricStatus,
    modifier: Modifier = Modifier,
) {
    val metrics = remember(snapshot, healthSnapshot, thresholds, dtcCount, healthScore) {
        DashLookMetrics.sideMetrics(
            snapshot, healthSnapshot, thresholds, dtcCount, healthScore, latchHealth,
        )
    }
    val rpmColor = HealthEvaluator.rpm(snapshot.rpm, thresholds).health.let {
        if (it == Health.UNKNOWN || it == Health.GOOD) Accent else it.color()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Background)
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.62f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CircularGauge(
                label = "RPM",
                value = snapshot.rpm,
                maxValue = thresholds.rpmHighMax,
                unit = "",
                size = 168.dp,
                arcColor = rpmColor,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GEAR", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (gearSource == GearSource.NONE) "–" else (snapshot.gear?.toString() ?: "–"),
                    color = Accent,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DigitFace,
                )
                Text(
                    text = when (gearSource) {
                        GearSource.ECU -> "ECU"
                        GearSource.ESTIMATED -> "EST"
                        GearSource.NONE -> ""
                    },
                    color = if (gearSource == GearSource.ECU) GoodGreen else WarnAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            CircularGauge(
                label = "SPEED",
                value = snapshot.speedKmh,
                maxValue = 220.0,
                unit = "km/h",
                size = 168.dp,
                arcColor = GoodGreen,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.38f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(metrics.take(8), key = { it.label }) { m ->
                TwinMetricChip(m)
            }
        }
    }
}

@Composable
fun PulseDeckDash(
    snapshot: VehicleSnapshot,
    healthSnapshot: VehicleSnapshot,
    thresholds: HealthThresholds,
    gearSource: GearSource,
    gearConfidencePct: Int?,
    dtcCount: Int?,
    healthScore: HealthScore?,
    latchHealth: (String, MetricStatus) -> MetricStatus,
    modifier: Modifier = Modifier,
) {
    val metrics = remember(snapshot, healthSnapshot, thresholds, dtcCount, healthScore) {
        DashLookMetrics.sideMetrics(
            snapshot, healthSnapshot, thresholds, dtcCount, healthScore, latchHealth,
        )
    }
    val rpmFrac = ((snapshot.rpm ?: 0.0) / thresholds.rpmHighMax.coerceAtLeast(1.0))
        .coerceIn(0.0, 1.0).toFloat()

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(PulseBg, Color(0xFF121A24), PulseBg)),
            )
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.48f)
                .clip(RoundedCornerShape(24.dp))
                .background(PulsePanel)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(0.34f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                RpmHalo(fraction = rpmFrac, color = PulseAccent)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RPM", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = snapshot.rpm?.roundToInt()?.toString() ?: "--",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = DigitFace,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(0.32f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("GEAR", color = PulseAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (gearSource == GearSource.NONE) "–" else (snapshot.gear?.toString() ?: "–"),
                    color = TextPrimary,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DigitFace,
                )
                Text(
                    text = when (gearSource) {
                        GearSource.ECU -> "ECU"
                        GearSource.ESTIMATED -> gearConfidencePct?.let { "$it%" } ?: "EST"
                        GearSource.NONE -> " "
                    },
                    color = when (gearSource) {
                        GearSource.ECU -> GoodGreen
                        GearSource.ESTIMATED -> WarnAmber
                        GearSource.NONE -> TextMuted
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.weight(0.34f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("SPEED", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = snapshot.speedKmh?.roundToInt()?.toString() ?: "--",
                    color = GoodGreen,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = DigitFace,
                    maxLines = 1,
                )
                Text("km/h", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.52f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(metrics.take(9), key = { it.label }) { m ->
                PulseMetricCard(m)
            }
        }
    }
}

@Composable
private fun ProgressMetricColumn(
    label: String,
    value: String,
    unit: String,
    fraction: Float,
    barColor: Color,
    track: Color,
) {
    Text(label, color = OrbitRedSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Text(
        text = value,
        color = TextPrimary,
        fontSize = 36.sp,
        fontWeight = FontWeight.Black,
        fontFamily = DigitFace,
        maxLines = 1,
    )
    if (unit.isNotEmpty()) {
        Text(unit, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(modifier = Modifier.height(10.dp))
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp)),
        color = barColor,
        trackColor = track,
        strokeCap = StrokeCap.Round,
    )
}

@Composable
private fun MetricCylinder(
    metrics: List<DashLookMetric>,
    accent: Color,
    panel: Color,
    modifier: Modifier = Modifier,
) {
    val pages = metrics.ifEmpty {
        listOf(DashLookMetric("–", "--", "", Health.UNKNOWN))
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(panel)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1,
        ) { page ->
            val m = pages[page]
            val offset = cylinderOffset(pagerState, page)
            val scale = (1f - offset * 0.28f).coerceIn(0.62f, 1f)
            val alpha = (1f - offset * 0.55f).coerceIn(0.28f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        // Slight pitch so neighbours feel like a rotating drum.
                        rotationX = (pagerState.currentPage - page +
                            pagerState.currentPageOffsetFraction) * -18f
                    }
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = m.label.uppercase(),
                    color = accent.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = m.value,
                    color = if (m.health == Health.UNKNOWN) TextPrimary else m.health.color(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DigitFace,
                    maxLines = 1,
                )
                if (m.unit.isNotEmpty()) {
                    Text(m.unit, color = TextMuted, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        // Soft vignette hints that more items sit above/below.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(18.dp)
                .background(
                    Brush.verticalGradient(listOf(panel, Color.Transparent)),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(18.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, panel)),
                ),
        )
    }
}

private fun cylinderOffset(pagerState: PagerState, page: Int): Float =
    ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

@Composable
private fun TwinMetricChip(m: DashLookMetric) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(m.label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                m.value,
                color = if (m.health == Health.UNKNOWN) TextPrimary else m.health.color(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (m.unit.isNotEmpty()) {
                Text(" ${m.unit}", color = TextMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PulseMetricCard(m: DashLookMetric) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PulsePanel)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        when (m.health) {
                            Health.UNKNOWN -> TextMuted
                            else -> m.health.color()
                        },
                    ),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                m.label,
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = buildString {
                append(m.value)
                if (m.unit.isNotEmpty()) append(' ').append(m.unit)
            },
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = DigitFace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RpmHalo(fraction: Float, color: Color) {
    Canvas(modifier = Modifier.size(132.dp)) {
        val stroke = Stroke(width = 14f, cap = StrokeCap.Round)
        drawArc(
            color = Color(0xFF243040),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = 135f,
            sweepAngle = 270f * fraction,
            useCenter = false,
            style = stroke,
        )
    }
}
