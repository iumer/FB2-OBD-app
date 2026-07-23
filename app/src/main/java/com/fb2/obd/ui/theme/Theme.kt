package com.fb2.obd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0B0F14)
val Surface = Color(0xFF141B22)
val Accent = Color(0xFF00E5FF)
val GoodGreen = Color(0xFF29D07B)
val WarnAmber = Color(0xFFFFB300)
val CritRed = Color(0xFFFF4D4D)
val TextPrimary = Color(0xFFEAF2F8)
val TextMuted = Color(0xFF7A8A99)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Background,
    surface = Surface,
    onPrimary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun FB2Theme(content: @Composable () -> Unit) {
    // Always dark: this is an in-car instrument cluster.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
