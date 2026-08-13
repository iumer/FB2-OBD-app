package com.fb2.obd.ui.theme

import androidx.compose.ui.graphics.Color
import com.fb2.obd.obd.DashTheme

/** Full-app colour / chrome tokens for the selected [DashTheme]. */
data class ThemePalette(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val accent: Color,
    val accentSoft: Color,
    val brand: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val good: Color,
    val warn: Color,
    val critical: Color,
    val track: Color,
    val glow: Color,
) {
    companion object {
        fun of(theme: DashTheme): ThemePalette = when (theme) {
            DashTheme.CLASSIC -> ThemePalette(
                background = Background,
                surface = Surface,
                surfaceAlt = Color(0xFF1A222C),
                accent = Accent,
                accentSoft = Accent.copy(alpha = 0.7f),
                brand = Accent,
                textPrimary = TextPrimary,
                textMuted = TextMuted,
                good = GoodGreen,
                warn = WarnAmber,
                critical = CritRed,
                track = Color(0xFF22303C),
                glow = Accent.copy(alpha = 0.35f),
            )
            DashTheme.OPT_A -> ThemePalette(
                background = Color(0xFF050505),
                surface = Color(0xFF14080A),
                surfaceAlt = Color(0xFF1E0C10),
                accent = Color(0xFFE53935),
                accentSoft = Color(0xFFFF6B63),
                brand = Color(0xFFFFF5F5),
                textPrimary = Color(0xFFF5F5F5),
                textMuted = Color(0xFF9A7A7A),
                good = Color(0xFF29D07B),
                warn = Color(0xFFFFB300),
                critical = Color(0xFFFF4D4D),
                track = Color(0xFF3A151C),
                glow = Color(0x66E53935),
            )
            DashTheme.OPT_B -> ThemePalette(
                background = Color(0xFF05070B),
                surface = Color(0xFF0E141C),
                surfaceAlt = Color(0xFF15202C),
                accent = Color(0xFF00B8FF),
                accentSoft = Color(0xFF4DD2FF),
                brand = Color(0xFF00E5FF),
                textPrimary = Color(0xFFEAF2F8),
                textMuted = Color(0xFF7A8A99),
                good = Color(0xFF29D07B),
                warn = Color(0xFFFFB300),
                critical = Color(0xFFFF4D4D),
                track = Color(0xFF1A2836),
                glow = Color(0x5500B8FF),
            )
            DashTheme.OPT_C -> ThemePalette(
                background = Color(0xFF07090E),
                surface = Color(0xFF101820),
                surfaceAlt = Color(0xFF182430),
                accent = Color(0xFFFF8A3D),
                accentSoft = Color(0xFFFFB07A),
                brand = Color(0xFFFF8A3D),
                textPrimary = Color(0xFFF2F4F8),
                textMuted = Color(0xFF8A93A0),
                good = Color(0xFF29D07B),
                warn = Color(0xFFFF8A3D),
                critical = Color(0xFFFF4D4D),
                track = Color(0xFF243040),
                glow = Color(0x55FF8A3D),
            )
        }
    }
}
