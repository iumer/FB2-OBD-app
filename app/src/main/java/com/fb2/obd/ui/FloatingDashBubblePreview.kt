package com.fb2.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * Compose stand-in for [com.fb2.obd.service.FloatingDashOverlayService] used in
 * Paparazzi car-HU reviews (real overlay is a WindowManager View).
 */
@Composable
fun FloatingDashBubblePreview(
    metrics: List<FloatingDashMetrics.Metric>,
    index: Int,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    statusLine: String = "LIVE",
) {
    val safe = metrics.ifEmpty {
        listOf(FloatingDashMetrics.Metric("Dash", "--", "", null, "WAITING"))
    }
    val i = index.coerceIn(0, safe.lastIndex)
    val m = safe[i]
    val color = healthColor(m.health)

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

        if (expanded) {
            Column(
                modifier = Modifier
                    .widthIn(min = 200.dp, max = 240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface.copy(alpha = 0.95f))
                    .border(2.dp, color, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = m.label.uppercase(),
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(m.value)
                        if (m.unit.isNotBlank()) {
                            append(' ')
                            append(m.unit)
                        }
                    },
                    color = color,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = m.status ?: m.health ?: "—",
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${i + 1} / ${safe.size}  ·  $statusLine",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "swipe · tap bubble to collapse",
                    color = TextMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("OPEN APP", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("CLOSE", color = CritRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Surface.copy(alpha = 0.92f))
                    .border(3.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${m.label.take(4).uppercase()}\n${m.value}",
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

private fun healthColor(health: String?): Color = when (health) {
    Health.CRITICAL.name -> CritRed
    Health.ELEVATED.name -> HotOrange
    Health.WARN.name -> WarnAmber
    Health.COLD.name -> ColdBlue
    Health.GOOD.name -> GoodGreen
    else -> Accent
}
