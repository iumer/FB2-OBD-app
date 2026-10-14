package com.fb2.obd.ui.dash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Lightweight canvas icons (no icon font / emoji) — matches sample glyph style. */
@Composable
fun ThemeIcon(
    kind: ThemeIconKind,
    color: Color,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = (s * 0.1f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
        when (kind) {
            ThemeIconKind.THERMOMETER -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(s * 0.42f, s * 0.08f),
                    size = Size(s * 0.16f, s * 0.55f),
                    cornerRadius = CornerRadius(s * 0.08f),
                    style = stroke,
                )
                drawCircle(color = color, radius = s * 0.18f, center = Offset(s * 0.5f, s * 0.78f), style = stroke)
                drawCircle(color = color, radius = s * 0.08f, center = Offset(s * 0.5f, s * 0.78f))
            }
            ThemeIconKind.BATTERY -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(s * 0.12f, s * 0.28f),
                    size = Size(s * 0.68f, s * 0.44f),
                    cornerRadius = CornerRadius(s * 0.06f),
                    style = stroke,
                )
                drawRect(color = color, topLeft = Offset(s * 0.82f, s * 0.38f), size = Size(s * 0.08f, s * 0.24f))
                drawLine(color, Offset(s * 0.28f, s * 0.5f), Offset(s * 0.62f, s * 0.5f), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
            }
            ThemeIconKind.GAUGE -> {
                drawArc(color, 140f, 260f, false, Offset(s * 0.12f, s * 0.12f), Size(s * 0.76f, s * 0.76f), style = stroke)
                drawLine(color, Offset(s * 0.5f, s * 0.5f), Offset(s * 0.72f, s * 0.28f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
                drawCircle(color, s * 0.06f, Offset(s * 0.5f, s * 0.5f))
            }
            ThemeIconKind.AIR -> {
                drawArc(color, 200f, 140f, false, Offset(s * 0.1f, s * 0.15f), Size(s * 0.8f, s * 0.7f), style = stroke)
                drawLine(color, Offset(s * 0.2f, s * 0.55f), Offset(s * 0.8f, s * 0.55f), strokeWidth = s * 0.07f)
                drawLine(color, Offset(s * 0.25f, s * 0.7f), Offset(s * 0.75f, s * 0.7f), strokeWidth = s * 0.07f)
            }
            ThemeIconKind.THROTTLE -> {
                drawCircle(color, s * 0.38f, Offset(s * 0.5f, s * 0.5f), style = stroke)
                drawCircle(color, s * 0.18f, Offset(s * 0.5f, s * 0.5f), style = stroke)
                drawLine(color, Offset(s * 0.5f, s * 0.5f), Offset(s * 0.78f, s * 0.28f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            }
            ThemeIconKind.WAVE -> {
                val p = Path().apply {
                    moveTo(s * 0.1f, s * 0.55f)
                    cubicTo(s * 0.25f, s * 0.15f, s * 0.35f, s * 0.95f, s * 0.5f, s * 0.55f)
                    cubicTo(s * 0.65f, s * 0.15f, s * 0.75f, s * 0.95f, s * 0.9f, s * 0.55f)
                }
                drawPath(p, color, style = stroke)
            }
            ThemeIconKind.SHIELD -> {
                val p = Path().apply {
                    moveTo(s * 0.5f, s * 0.08f)
                    lineTo(s * 0.88f, s * 0.22f)
                    lineTo(s * 0.82f, s * 0.62f)
                    quadraticBezierTo(s * 0.5f, s * 0.95f, s * 0.18f, s * 0.62f)
                    lineTo(s * 0.12f, s * 0.22f)
                    close()
                }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(s * 0.35f, s * 0.52f), Offset(s * 0.48f, s * 0.65f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
                drawLine(color, Offset(s * 0.48f, s * 0.65f), Offset(s * 0.7f, s * 0.38f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            }
            ThemeIconKind.LOAD -> {
                drawCircle(color, s * 0.36f, Offset(s * 0.5f, s * 0.5f), style = stroke)
                drawArc(color, -90f, 200f, false, Offset(s * 0.22f, s * 0.22f), Size(s * 0.56f, s * 0.56f), style = Stroke(width = s * 0.12f, cap = StrokeCap.Round))
            }
            ThemeIconKind.BLUETOOTH -> {
                val p = Path().apply {
                    moveTo(s * 0.4f, s * 0.15f)
                    lineTo(s * 0.7f, s * 0.35f)
                    lineTo(s * 0.4f, s * 0.55f)
                    lineTo(s * 0.4f, s * 0.15f)
                    moveTo(s * 0.4f, s * 0.55f)
                    lineTo(s * 0.7f, s * 0.75f)
                    lineTo(s * 0.4f, s * 0.95f)
                }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(s * 0.25f, s * 0.3f), Offset(s * 0.4f, s * 0.45f), strokeWidth = s * 0.08f)
                drawLine(color, Offset(s * 0.25f, s * 0.8f), Offset(s * 0.4f, s * 0.65f), strokeWidth = s * 0.08f)
            }
            ThemeIconKind.OBD -> {
                drawRoundRect(color, Offset(s * 0.15f, s * 0.3f), Size(s * 0.7f, s * 0.4f), CornerRadius(s * 0.08f), style = stroke)
                drawCircle(color, s * 0.06f, Offset(s * 0.35f, s * 0.5f))
                drawCircle(color, s * 0.06f, Offset(s * 0.5f, s * 0.5f))
                drawCircle(color, s * 0.06f, Offset(s * 0.65f, s * 0.5f))
            }
            ThemeIconKind.ENGINE -> {
                drawRoundRect(color, Offset(s * 0.2f, s * 0.35f), Size(s * 0.55f, s * 0.35f), CornerRadius(s * 0.05f), style = stroke)
                drawRect(color, Offset(s * 0.08f, s * 0.45f), Size(s * 0.14f, s * 0.15f), style = stroke)
                drawLine(color, Offset(s * 0.35f, s * 0.2f), Offset(s * 0.35f, s * 0.35f), strokeWidth = s * 0.08f)
                drawLine(color, Offset(s * 0.55f, s * 0.2f), Offset(s * 0.55f, s * 0.35f), strokeWidth = s * 0.08f)
            }
            ThemeIconKind.MENU -> {
                for (i in 0..2) {
                    val y = s * (0.28f + i * 0.22f)
                    drawLine(color, Offset(s * 0.2f, y), Offset(s * 0.8f, y), strokeWidth = s * 0.1f, cap = StrokeCap.Round)
                }
            }
            ThemeIconKind.MORE -> {
                drawCircle(color, s * 0.08f, Offset(s * 0.5f, s * 0.25f))
                drawCircle(color, s * 0.08f, Offset(s * 0.5f, s * 0.5f))
                drawCircle(color, s * 0.08f, Offset(s * 0.5f, s * 0.75f))
            }
        }
    }
}

enum class ThemeIconKind {
    THERMOMETER, BATTERY, GAUGE, AIR, THROTTLE, WAVE, SHIELD, LOAD,
    BLUETOOTH, OBD, ENGINE, MENU, MORE,
}

fun iconKindForMetric(label: String): ThemeIconKind = when {
    label.contains("Coolant", true) -> ThemeIconKind.THERMOMETER
    label.contains("Battery", true) -> ThemeIconKind.BATTERY
    label.contains("Intake", true) || label.contains("Ambient", true) || label.contains("MAF", true) -> ThemeIconKind.AIR
    label.contains("Throttle", true) -> ThemeIconKind.THROTTLE
    label.contains("Load", true) -> ThemeIconKind.LOAD
    label.contains("MAP", true) || label.contains("Timing", true) -> ThemeIconKind.GAUGE
    label.contains("STFT", true) || label.contains("LTFT", true) || label.contains("Fuel", true) -> ThemeIconKind.WAVE
    label.contains("Health", true) -> ThemeIconKind.SHIELD
    else -> ThemeIconKind.GAUGE
}
