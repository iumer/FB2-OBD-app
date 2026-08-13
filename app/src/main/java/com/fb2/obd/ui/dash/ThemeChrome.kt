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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.DashboardUiState
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.DashTheme
import com.fb2.obd.ui.theme.ThemePalette

/**
 * Immersive theme chrome matching the OptA/B/C samples.
 * Hamburger opens actions (Settings / DIAG / MIN / LOG / Connect).
 */
@Composable
fun ThemedTopBar(
    theme: DashTheme,
    palette: ThemePalette,
    state: DashboardUiState,
    loggingActive: Boolean,
    onConnectClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onToggleLogging: () -> Unit,
    onMinimizeClick: () -> Unit,
) {
    val status = connectionLabel(state)
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Text(
                    text = "☰",
                    color = palette.accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { menuOpen = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { menuOpen = false; onSettingsClick() },
                    )
                    DropdownMenuItem(
                        text = { Text("Diagnostics") },
                        onClick = { menuOpen = false; onDiagnosticsClick() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (loggingActive) "Stop log" else "Start log") },
                        onClick = { menuOpen = false; onToggleLogging() },
                    )
                    DropdownMenuItem(
                        text = { Text("Minimize") },
                        onClick = { menuOpen = false; onMinimizeClick() },
                    )
                    DropdownMenuItem(
                        text = { Text("Connect / devices") },
                        onClick = { menuOpen = false; onConnectClick() },
                    )
                }
            }
            if (theme == DashTheme.OPT_B || theme == DashTheme.OPT_A) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(status.dot),
                )
                Text(
                    text = "  ${status.label}",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Text(
            text = "FB2 DIAG",
            color = palette.brand,
            fontSize = when (theme) {
                DashTheme.OPT_C -> 20.sp
                else -> 18.sp
            },
            fontWeight = FontWeight.Black,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onConnectClick)
                .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            when (theme) {
                DashTheme.OPT_A -> {
                    Text("⬡", color = palette.accent, fontSize = 12.sp)
                    Text(" ELM327", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                DashTheme.OPT_B -> {
                    Text("⚙", color = palette.accent, fontSize = 13.sp)
                    Text("  ⋮", color = palette.textMuted, fontSize = 14.sp)
                }
                else -> {
                    Text("◉", color = palette.accent, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(status.dot),
                    )
                    Text(
                        text = " ${status.label}",
                        color = palette.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private data class StatusChip(val label: String, val dot: androidx.compose.ui.graphics.Color)

private fun connectionLabel(state: DashboardUiState): StatusChip {
    val live = state.connection == ConnectionState.CONNECTED && state.sourceIsLive && !state.reconnecting
    return when {
        state.reconnecting ||
            (state.connection == ConnectionState.CONNECTING && state.sourceIsLive) ->
            StatusChip("RETRY", ThemePalette.of(DashTheme.CLASSIC).warn)
        live -> StatusChip("CONNECTED", ThemePalette.of(DashTheme.CLASSIC).good)
        state.connection == ConnectionState.CONNECTED && !state.sourceIsLive ->
            StatusChip("DEMO", ThemePalette.of(DashTheme.CLASSIC).warn)
        state.connection == ConnectionState.CONNECTING ->
            StatusChip("…", ThemePalette.of(DashTheme.CLASSIC).accent)
        state.connection == ConnectionState.ERROR ->
            StatusChip("ERR", ThemePalette.of(DashTheme.CLASSIC).critical)
        else -> StatusChip("OFF", ThemePalette.of(DashTheme.CLASSIC).textMuted)
    }
}
