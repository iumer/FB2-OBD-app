package com.fb2.obd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.Health
import com.fb2.obd.obd.SnapshotFreshness
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.ColdBlue
import com.fb2.obd.ui.theme.CritRed
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.HotOrange
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.WarnAmber
import kotlin.math.roundToInt

fun Health.color(): Color = when (this) {
    Health.COLD -> ColdBlue
    Health.GOOD -> GoodGreen
    Health.WARN -> WarnAmber
    Health.ELEVATED -> HotOrange
    Health.CRITICAL -> CritRed
    Health.UNKNOWN -> TextMuted
}

/**
 * Torque-style green freshness LED.
 *
 * Intentionally **static** (no per-tile timers / Animatable). Low-RAM car HUs
 * stuttered because ~15 tiles each ran a 200 ms clock + pulse while Demo/ELM
 * already recomposes the Dash. Brightness still tracks [lastOkMs] on parent recomposes.
 */
@Composable
fun FreshnessHeartbeat(
    lastOkMs: Long?,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    nowMs: Long = System.currentTimeMillis(),
) {
    val age = lastOkMs?.let { nowMs - it }
    val active = age != null && age < SnapshotFreshness.LED_ACTIVE_MS
    val alpha = when {
        lastOkMs == null -> 0.12f
        !active -> 0.14f
        age != null && age < 700L -> 1f
        else -> 0.72f
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(GoodGreen.copy(alpha = alpha)),
    )
}

/**
 * A large circular sweep gauge (used for RPM and Speed) with the value in the
 * centre. Sweeps 270 degrees like a real instrument cluster.
 */
@Composable
fun CircularGauge(
    label: String,
    value: Double?,
    maxValue: Double,
    unit: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    arcColor: Color = Accent,
) {
    val fraction = ((value ?: 0.0) / maxValue).coerceIn(0.0, 1.0).toFloat()
    // Snap — animated sweeps fight scroll jank on low-RAM HUs.
    val sweepTotal = 270f
    val startAngle = 135f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = Stroke(width = 22f, cap = StrokeCap.Round)
            val arcSize = Size(this.size.width, this.size.height)
            drawArc(
                color = Color(0xFF22303C),
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = Offset.Zero,
                size = arcSize,
                style = stroke,
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(arcColor, arcColor, WarnAmber, CritRed),
                ),
                startAngle = startAngle,
                sweepAngle = sweepTotal * fraction,
                useCenter = false,
                topLeft = Offset.Zero,
                size = arcSize,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value?.let { it.roundToInt().toString() } ?: "--",
                color = TextPrimary,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = unit, color = TextMuted, fontSize = 14.sp)
            Text(
                text = label,
                color = arcColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Compact metric tile. A health-coloured status dot is shown only for metrics
 * that have a health rule ([health] non-null); plain metrics get no dot so the
 * traffic-light dots keep their meaning.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String,
    health: Health? = null,
    modifier: Modifier = Modifier,
) {
    val valueColor = when (health) {
        null, Health.UNKNOWN -> TextPrimary
        else -> health.color()
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (health != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(health.color()),
                )
                Text(
                    text = "  $label",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text(
                    text = label,
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            if (unit.isNotEmpty()) {
                Text(text = " $unit", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

/** Central gear indicator with a source badge (ECU actual vs estimated). */
@Composable
fun GearIndicator(
    gear: Int?,
    source: GearSource,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "GEAR", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            // Null means "unknown / below the speed floor", NOT Neutral.
            text = gear?.toString() ?: "\u2013",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
        )
        val (badge, badgeColor) = when (source) {
            GearSource.ECU -> "ECU" to GoodGreen
            GearSource.ESTIMATED -> "EST" to WarnAmber
            GearSource.NONE -> "" to TextMuted
        }
        if (badge.isNotEmpty()) {
            Text(text = badge, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
