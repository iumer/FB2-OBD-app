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

@Composable
fun ThemedTopBar(
    theme: DashTheme,
    connected: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onOpenLogs: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (theme) {
        DashTheme.OPT_A -> OptAHeader(connected, onOpenSettings, onOpenDiag, onOpenMin, onOpenLogs, onConnect, modifier)
        DashTheme.OPT_B -> OptBHeader(connected, onOpenSettings, onOpenDiag, onOpenMin, onOpenLogs, onConnect, modifier)
        DashTheme.OPT_C -> OptCHeader(connected, onOpenSettings, onOpenDiag, onOpenMin, onOpenLogs, onConnect, modifier)
        DashTheme.CLASSIC -> Unit
    }
}

@Composable
private fun OptAHeader(
    connected: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onOpenLogs: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier,
) {
    val p = LocalThemePalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeIcon(
                ThemeIconKind.BLUETOOTH,
                if (connected) Color(0xFF4AD8FF) else Color(0xFF666666),
                size = 18.dp,
            )
            Column {
                Text("FB2 DIAG", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(
                    if (connected) "ELM327 · LINKED" else "ELM327 · OFFLINE",
                    color = if (connected) Color(0xFF5EEBA0) else Color(0xFFFF6B6B),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        ThemeMenuButton(
            accent = p.accent,
            border = p.accent.copy(alpha = 0.45f),
            onOpenSettings = onOpenSettings,
            onOpenDiag = onOpenDiag,
            onOpenMin = onOpenMin,
            onOpenLogs = onOpenLogs,
            onConnect = onConnect,
        )
    }
}

@Composable
private fun OptBHeader(
    connected: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onOpenLogs: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier,
) {
    val p = LocalThemePalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF050505))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "FB2 DIAG",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.SansSerif,
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MilLamp(on = !connected)
            OverflowMenu(
                accent = p.accent,
                onOpenSettings = onOpenSettings,
                onOpenDiag = onOpenDiag,
                onOpenMin = onOpenMin,
                onOpenLogs = onOpenLogs,
                onConnect = onConnect,
            )
        }
    }
}

@Composable
private fun OptCHeader(
    connected: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onOpenLogs: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier,
) {
    val p = LocalThemePalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF070707))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) { append("FB2") }
                append(" ")
                withStyle(SpanStyle(color = Color(0xFFFF6A00), fontWeight = FontWeight.Black)) { append("DIAG") }
            },
            fontSize = 20.sp,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeIcon(
                ThemeIconKind.BLUETOOTH,
                if (connected) Color(0xFFFF8A3D) else Color(0xFF666666),
                size = 18.dp,
            )
            ThemeIcon(
                ThemeIconKind.OBD,
                if (connected) Color(0xFFFF8A3D) else Color(0xFF666666),
                size = 20.dp,
            )
            ThemeMenuButton(
                accent = p.accent,
                border = p.accent.copy(alpha = 0.45f),
                onOpenSettings = onOpenSettings,
                onOpenDiag = onOpenDiag,
                onOpenMin = onOpenMin,
                onOpenLogs = onOpenLogs,
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
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onOpenLogs: () -> Unit,
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
            onOpenSettings = onOpenSettings,
            onOpenDiag = onOpenDiag,
            onOpenMin = onOpenMin,
            onOpenLogs = onOpenLogs,
            onConnect = onConnect,
        )
    }
}

@Composable
private fun ThemeMenuButton(
    accent: Color,
    border: Color,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onOpenLogs: () -> Unit,
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
            onOpenSettings = onOpenSettings,
            onOpenDiag = onOpenDiag,
            onOpenMin = onOpenMin,
            onOpenLogs = onOpenLogs,
            onConnect = onConnect,
        )
    }
}

@Composable
private fun ThemeDropdown(
    open: Boolean,
    onDismiss: () -> Unit,
    accent: Color,
    onOpenSettings: () -> Unit,
    onOpenDiag: () -> Unit,
    onOpenMin: () -> Unit,
    onOpenLogs: () -> Unit,
    onConnect: () -> Unit,
) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Settings") }, onClick = { onDismiss(); onOpenSettings() })
        DropdownMenuItem(text = { Text("DIAG") }, onClick = { onDismiss(); onOpenDiag() })
        DropdownMenuItem(text = { Text("MIN") }, onClick = { onDismiss(); onOpenMin() })
        DropdownMenuItem(text = { Text("LOG") }, onClick = { onDismiss(); onOpenLogs() })
        DropdownMenuItem(
            text = { Text("Connect", color = accent) },
            onClick = { onDismiss(); onConnect() },
        )
    }
}
