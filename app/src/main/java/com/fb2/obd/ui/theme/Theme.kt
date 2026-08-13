package com.fb2.obd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.fb2.obd.obd.DashTheme

val Background = Color(0xFF0B0F14)
val Surface = Color(0xFF141B22)
val Accent = Color(0xFF00E5FF)
val ColdBlue = Color(0xFF4DA3FF)
val GoodGreen = Color(0xFF29D07B)
val WarnAmber = Color(0xFFFFB300)
val HotOrange = Color(0xFFFF8A3D)
val CritRed = Color(0xFFFF4D4D)
val TextPrimary = Color(0xFFEAF2F8)
val TextMuted = Color(0xFF7A8A99)

val LocalThemePalette = staticCompositionLocalOf { ThemePalette.of(DashTheme.CLASSIC) }

@Composable
fun FB2Theme(
    palette: ThemePalette = ThemePalette.of(DashTheme.CLASSIC),
    content: @Composable () -> Unit,
) {
    // Always dark: in-car instrument cluster. Primary/surface track the Dash theme.
    val colors = darkColorScheme(
        primary = palette.accent,
        secondary = palette.accentSoft,
        tertiary = palette.good,
        background = palette.background,
        surface = palette.surface,
        surfaceVariant = palette.surfaceAlt,
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onTertiary = Color.Black,
        onBackground = palette.textPrimary,
        onSurface = palette.textPrimary,
        onSurfaceVariant = palette.textMuted,
        error = palette.critical,
        onError = Color.White,
        outline = palette.accent.copy(alpha = 0.45f),
    )
    CompositionLocalProvider(LocalThemePalette provides palette) {
        MaterialTheme(
            colorScheme = colors,
            content = content,
        )
    }
}
