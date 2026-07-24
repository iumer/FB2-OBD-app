package com.fb2.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.car.FloatingDashMetrics
import com.fb2.obd.obd.Health
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.ColdBlue
import com.fb2.obd.ui.theme.CritRed
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.HotOrange
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.WarnAmber
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Compose stand-in for [com.fb2.obd.service.FloatingDashOverlayService] used in
 * Paparazzi car-HU reviews (real overlay is a WindowManager View).
 *
 * Collapsed = one circle. Expanded = center + up to 5 satellites on a ring,
 * paged via [pageIndex].
 */
@Composable
fun FloatingDashBubblePreview(
    metrics: List<FloatingDashMetrics.Metric>,
    pageIndex: Int = 0,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    statusLine: String = "LIVE",
    /** @deprecated Kept for older call sites; prefer [pageIndex]. */
    index: Int = 0,
) {
    val safe = metrics.ifEmpty {
        listOf(FloatingDashMetrics.Metric("Dash", "--", "", null, "WAITING"))
    }
    val page = if (expanded) {
        FloatingDashMetrics.page(safe, pageIndex)
    } else {
        val i = index.coerceIn(0, safe.lastIndex)
        listOf(safe[i])
    }
    val pages = FloatingDashMetrics.pageCount(safe)
    val worst = FloatingDashMetrics.worstHealth(safe.mapNotNull { it.health })
    val rim = healthColor(worst)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Fake CarPlay/maps backdrop so we can judge contrast on HU.
        Text(
            text = "CARPLAY / MAP LAYER",
            color = TextMuted.copy(alpha = 0.35f),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )

        Box(
            modifier = Modifier
                .padding(start = 24.dp)
                .size(if (expanded) 280.dp else 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (expanded) {
                val radiusPx = 96f
                page.forEachIndexed { i, m ->
                    val angleDeg = -90.0 + i * (360.0 / FloatingDashMetrics.PAGE_SIZE)
                    val rad = Math.toRadians(angleDeg)
                    val ox = (radiusPx * cos(rad)).roundToInt()
                    val oy = (radiusPx * sin(rad)).roundToInt()
                    SatelliteBubble(
                        metric = m,
                        modifier = Modifier.offset(ox.dp, oy.dp),
                    )
                }
                Text(
                    text = "↕ scroll · $statusLine",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                )
            }

            // Center floating button (always)
            val centerMetric = page.firstOrNull() ?: safe.first()
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Surface.copy(alpha = 0.92f))
                    .border(3.dp, rim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (expanded) {
                        "FB2\n${pageIndex.coerceIn(0, pages - 1) + 1}/$pages"
                    } else {
                        "${centerMetric.label.take(4).uppercase()}\n${centerMetric.value}"
                    },
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun SatelliteBubble(
    metric: FloatingDashMetrics.Metric,
    modifier: Modifier = Modifier,
) {
    val color = healthColor(metric.health)
    Box(
        modifier = modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(Surface.copy(alpha = 0.92f))
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = buildString {
                append(metric.label.take(7).uppercase())
                append('\n')
                append(metric.value)
                if (metric.unit.isNotBlank()) {
                    append('\n')
                    append(metric.unit.take(4))
                }
            },
            color = TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp,
            maxLines = 3,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun healthColor(health: String?): Color = when (health) {
    Health.CRITICAL.name -> CritRed
    Health.ELEVATED.name -> HotOrange
    Health.WARN.name -> WarnAmber
    Health.COLD.name -> ColdBlue
    Health.GOOD.name -> GoodGreen
    else -> Accent
}
