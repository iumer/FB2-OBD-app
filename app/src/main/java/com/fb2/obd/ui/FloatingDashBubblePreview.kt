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

/** Matches [com.fb2.obd.service.FloatingDashOverlayService] HU-sized ring. */
private object BubbleScale {
    val collapsed = 96.dp
    val center = 96.dp
    val sat = 112.dp
    val expanded = 460.dp
    val radius = 160f
    val centerText = 16.sp
    val satText = 15.sp
    val hint = 13.sp
}

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
        Text(
            text = "CARPLAY / MAP LAYER",
            color = TextMuted.copy(alpha = 0.35f),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )

        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .size(if (expanded) BubbleScale.expanded else BubbleScale.collapsed),
            contentAlignment = Alignment.Center,
        ) {
            if (expanded) {
                page.forEachIndexed { i, m ->
                    val angleDeg = -90.0 + i * (360.0 / FloatingDashMetrics.PAGE_SIZE)
                    val rad = Math.toRadians(angleDeg)
                    val ox = (BubbleScale.radius * cos(rad)).roundToInt()
                    val oy = (BubbleScale.radius * sin(rad)).roundToInt()
                    SatelliteBubble(
                        metric = m,
                        modifier = Modifier.offset(ox.dp, oy.dp),
                    )
                }
                Text(
                    text = "↕ scroll · $statusLine",
                    color = TextMuted,
                    fontSize = BubbleScale.hint,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
                )
            }

            val centerMetric = page.firstOrNull() ?: safe.first()
            Box(
                modifier = Modifier
                    .size(BubbleScale.center)
                    .clip(CircleShape)
                    .background(Surface.copy(alpha = 0.92f))
                    .border(4.dp, rim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (expanded) {
                        "FB2\n${pageIndex.coerceIn(0, pages - 1) + 1}/$pages"
                    } else {
                        "${centerMetric.label.take(4).uppercase()}\n${centerMetric.value}"
                    },
                    color = TextPrimary,
                    fontSize = BubbleScale.centerText,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
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
            .size(BubbleScale.sat)
            .clip(CircleShape)
            .background(Surface.copy(alpha = 0.92f))
            .border(3.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = buildString {
                append(metric.label.take(8).uppercase())
                append('\n')
                append(metric.value)
                if (metric.unit.isNotBlank()) {
                    append('\n')
                    append(metric.unit.take(4))
                }
            },
            color = TextPrimary,
            fontSize = BubbleScale.satText,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
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
