package com.fb2.obd.ui.dash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.obd.DashTheme
import com.fb2.obd.ui.theme.LocalThemePalette

/**
 * Connection / logging status for themed Dash chrome.
 * [elmLive] = real ELM Bluetooth live link.
 * [demo] = simulated source with updating values.
 */
data class DashLinkStatus(
    val elmLive: Boolean,
    val demo: Boolean,
    val logging: Boolean,
    val online: Boolean,
)

@Composable
fun ThemedTopBar(
    theme: DashTheme,
    link: DashLinkStatus,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onToggleLogging: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (theme) {
        DashTheme.OPT_A -> OptAHeader(link, onOpenSettings, onOpenDiag, onOpenMin, onToggleLogging, onConnect, modifier)
        DashTheme.OPT_B -> OptBHeader(link, onOpenSettings, onOpenDiag, onOpenMin, onToggleLogging, onConnect, modifier)
        DashTheme.OPT_C -> OptCHeader(link, onOpenSettings, onOpenDiag, onOpenMin, onToggleLogging, onConnect, modifier)
        DashTheme.CLASSIC -> Unit
    }
}

@Composable
private fun StatusPills(link: DashLinkStatus, accent: Color) {
    val elmLabel = when {
        link.elmLive -> "ELM · LINKED"
        link.demo -> "DEMO"
        else -> "ELM · OFFLINE"
    }
    val elmColor = when {
        link.elmLive -> Color(0xFF5EEBA0)
        link.demo -> Color(0xFFFFB74D)
        else -> Color(0xFFFF6B6B)
    }
    val logLabel = if (link.logging) "LOGGING" else "NOT LOGGING"
    val logColor = if (link.logging) Color(0xFF5EEBA0) else Color(0xFF888888)
    val netLabel = if (link.online) "INET" else "NO NET"
    val netColor = if (link.online) Color(0xFF5EEBA0) else Color(0xFF888888)

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusChip(elmLabel, elmColor)
        StatusChip(logLabel, logColor)
        StatusChip(netLabel, netColor)
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun OptAHeader(
    link: DashLinkStatus,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onToggleLogging: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier,
) {
    val p = LocalThemePalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeIcon(
                ThemeIconKind.BLUETOOTH,
                if (link.elmLive || link.demo) Color(0xFF4AD8FF) else Color(0xFF666666),
                size = 16.dp,
            )
            Column {
                Text("FB2 DIAG", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                StatusPills(link, p.accent)
            }
        }
        Spacer(Modifier.weight(1f))
        ThemeMenuButton(
            accent = p.accent,
            border = p.accent.copy(alpha = 0.45f),
            logging = link.logging,
            onOpenSettings = onOpenSettings,
            onOpenDiag = onOpenDiag,
            onOpenMin = onOpenMin,
            onToggleLogging = onToggleLogging,
            onConnect = onConnect,
        )
    }
}

@Composable
private fun OptBHeader(
    link: DashLinkStatus,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onToggleLogging: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier,
) {
    val p = LocalThemePalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF050505))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "FB2 DIAG",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.SansSerif,
            )
            StatusPills(link, p.accent)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // MIL only for real offline — not Demo
            MilLamp(on = !link.elmLive && !link.demo)
            OverflowMenu(
                accent = p.accent,
                logging = link.logging,
                onOpenSettings = onOpenSettings,
                onOpenDiag = onOpenDiag,
                onOpenMin = onOpenMin,
                onToggleLogging = onToggleLogging,
                onConnect = onConnect,
            )
        }
    }
}

@Composable
private fun OptCHeader(
    link: DashLinkStatus,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onToggleLogging: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier,
) {
    val p = LocalThemePalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF070707))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) { append("FB2") }
                    append(" ")
                    withStyle(SpanStyle(color = Color(0xFFFF6A00), fontWeight = FontWeight.Black)) { append("DIAG") }
                },
                fontSize = 18.sp,
                letterSpacing = 1.5.sp,
            )
            StatusPills(link, p.accent)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeIcon(
                ThemeIconKind.BLUETOOTH,
                if (link.elmLive || link.demo) Color(0xFFFF8A3D) else Color(0xFF666666),
                size = 16.dp,
            )
            ThemeIcon(
                ThemeIconKind.OBD,
                if (link.elmLive) Color(0xFFFF8A3D) else Color(0xFF666666),
                size = 18.dp,
            )
            ThemeMenuButton(
                accent = p.accent,
                border = p.accent.copy(alpha = 0.45f),
                logging = link.logging,
                onOpenSettings = onOpenSettings,
                onOpenDiag = onOpenDiag,
                onOpenMin = onOpenMin,
                onToggleLogging = onToggleLogging,
                onConnect = onConnect,
            )
        }
    }
}

@Composable
private fun MilLamp(on: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (on) Color(0xFF3A1010) else Color(0xFF1A1A1A))
            .border(1.5.dp, if (on) Color(0xFFFF3B3B) else Color(0xFF444444), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (on) Color(0xFFFF2A2A) else Color(0xFF2A2A2A)),
        )
    }
}

@Composable
private fun OverflowMenu(
    accent: Color,
    logging: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onToggleLogging: () -> Unit,
    onConnect: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "⋮",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { open = true }
                .padding(4.dp),
        )
        ThemeDropdown(
            open = open,
            onDismiss = { open = false },
            accent = accent,
            logging = logging,
            onOpenSettings = onOpenSettings,
            onOpenDiag = onOpenDiag,
            onOpenMin = onOpenMin,
            onToggleLogging = onToggleLogging,
            onConnect = onConnect,
        )
    }
}

@Composable
private fun ThemeMenuButton(
    accent: Color,
    border: Color,
    logging: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onToggleLogging: () -> Unit,
    onConnect: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141414))
                .border(1.dp, border, RoundedCornerShape(10.dp))
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("☰", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        ThemeDropdown(
            open = open,
            onDismiss = { open = false },
            accent = accent,
            logging = logging,
            onOpenSettings = onOpenSettings,
            onOpenDiag = onOpenDiag,
            onOpenMin = onOpenMin,
            onToggleLogging = onToggleLogging,
            onConnect = onConnect,
        )
    }
}

@Composable
private fun ThemeDropdown(
    open: Boolean,
    onDismiss: () -> Unit,
    accent: Color,
    logging: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onToggleLogging: () -> Unit,
    onConnect: () -> Unit,
) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Settings") }, onClick = { onDismiss(); onOpenSettings() })
        DropdownMenuItem(text = { Text("DIAG") }, onClick = { onDismiss(); onOpenDiag() })
        DropdownMenuItem(text = { Text("MIN") }, onClick = { onDismiss(); onOpenMin() })
        DropdownMenuItem(
            text = {
                Text(
                    if (logging) "STOP LOG" else "LOG",
                    color = if (logging) Color(0xFFFF5252) else accent,
                )
            },
            onClick = { onDismiss(); onToggleLogging() },
        )
        DropdownMenuItem(
            text = { Text("Connect", color = accent) },
            onClick = { onDismiss(); onConnect() },
        )
    }
}
