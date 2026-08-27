package com.fb2.obd.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.SettingsState
import com.fb2.obd.data.LogUploadManager
import com.fb2.obd.obd.AppUpdateChecker
import com.fb2.obd.obd.DashTheme
import com.fb2.obd.obd.KeepAlivePolicy
import com.fb2.obd.obd.VehicleProfile
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.LocalThemePalette
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.ThemePalette
import com.fb2.obd.ui.theme.WarnAmber

@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    accent: androidx.compose.ui.graphics.Color? = null,
    surface: androidx.compose.ui.graphics.Color? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val palette = LocalThemePalette.current
    val accentColor = accent ?: palette.accent
    val surfaceColor = surface ?: palette.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\u2190 Back",
                color = accentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .background(surfaceColor)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Text(
                text = "   $title",
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        action?.invoke()
    }
}

/** Settings only keeps toggles + log viewers. Live pages → dashboard swipes; diag → DIAG button. */
data class SettingsNav(
    val onDebug: () -> Unit = {},
    val onValues: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    settings: SettingsState,
    onVehicleProfileChange: (VehicleProfile) -> Unit = {},
    onDashThemeChange: (DashTheme) -> Unit = {},
    onToggleEstimatedGear: (Boolean) -> Unit,
    onToggleAllowDemo: (Boolean) -> Unit = {},
    onToggleVoiceAlerts: (Boolean) -> Unit = {},
    onToggleDuckMedia: (Boolean) -> Unit = {},
    onCheckSoundAlert: () -> Unit = {},
    onKeepAliveBattery: () -> Unit = {},
    batteryUnrestricted: Boolean = false,
    uploadStatus: LogUploadManager.Status = LogUploadManager.Status(),
    githubToken: String = "",
    onGithubTokenChange: (String) -> Unit = {},
    onUploadLogs: () -> Unit = {},
    openAiApiKey: String = "",
    onOpenAiApiKeyChange: (String) -> Unit = {},
    appVersionLabel: String = "",
    updateStatusText: String = "",
    updateBusy: Boolean = false,
    availableUpdates: List<AppUpdateChecker.RemoteVersion> = emptyList(),
    downloadingName: String? = null,
    downloadPercent: Int = 0,
    readyToInstallName: String? = null,
    onCheckForUpdate: () -> Unit = {},
    onDownloadVersion: (AppUpdateChecker.RemoteVersion) -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    nav: SettingsNav,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    val palette = LocalThemePalette.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(16.dp)
            .verticalScroll(scrollState),
    ) {
        ScreenHeader(
            title = "Settings",
            onBack = onBack,
            accent = palette.accent,
            surface = palette.surface,
        )

        // —— App update (no section heading) ——
        ActionRow(
            title = if (updateStatusText.isNotBlank()) updateStatusText else "Check for update",
            subtitle = if (appVersionLabel.isNotBlank()) {
                "Currently installed version is $appVersionLabel"
            } else {
                "Currently installed version unknown"
            },
            actionLabel = if (updateBusy) "…" else "CHECK",
            onClick = { if (!updateBusy) onCheckForUpdate() },
        )
        if (availableUpdates.isNotEmpty()) {
            Text(
                text = "${availableUpdates.size} newer " +
                    if (availableUpdates.size == 1) "version" else "versions",
                color = palette.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            val latestCode = availableUpdates.maxOf { it.versionCode }
            availableUpdates.forEach { remote ->
                val isDownloading = downloadingName == remote.versionName
                val isReady = readyToInstallName == remote.versionName
                val label = when {
                    isReady -> "INSTALL"
                    isDownloading -> "${downloadPercent}%"
                    else -> "GET"
                }
                val title = buildString {
                    append("v${remote.versionName}")
                    if (remote.versionCode == latestCode) append("  ·  latest")
                }
                val subtitle = when {
                    isReady -> "Downloaded — tap Install"
                    isDownloading -> "Downloading…"
                    remote.notes.isNotBlank() -> remote.notes
                    else -> "Tap GET to download"
                }
                ActionRow(
                    title = title,
                    subtitle = subtitle,
                    actionLabel = label,
                    onClick = {
                        when {
                            updateBusy && !isReady -> Unit
                            isReady -> onInstallUpdate()
                            else -> onDownloadVersion(remote)
                        }
                    },
                )
            }
        }

        // —— Vehicle ——
        SectionLabel("Vehicle")
        ToggleRow(
            title = "Simulation",
            subtitle = "Simulated Dash when no ELM adapter is connected.",
            checked = settings.allowDemo,
            onCheckedChange = onToggleAllowDemo,
        )
        SettingDropdown(
            label = settings.vehicleProfile.displayName,
            subtitle = settings.vehicleProfile.subtitle,
            accent = palette.accent,
            options = VehicleProfile.entries.map { it.displayName to it.subtitle },
            onSelectIndex = { onVehicleProfileChange(VehicleProfile.entries[it]) },
        )
        ToggleRow(
            title = "Show estimated gear",
            subtitle = if (settings.vehicleProfile.isGeneric) {
                "Off by default on Generic OBD2 (ratios are FB2-specific). Enable only if you accept approximate gears."
            } else {
                "When the ECU can't report the actual gear, estimate from speed & RPM (EST)."
            },
            checked = settings.showEstimatedGear,
            onCheckedChange = onToggleEstimatedGear,
        )

        // —— Appearance ——
        SectionLabel("Appearance")
        SettingDropdown(
            label = settings.dashTheme.displayName,
            subtitle = settings.dashTheme.subtitle,
            accent = palette.accent,
            options = DashTheme.entries.map { it.displayName to it.subtitle },
            onSelectIndex = { onDashThemeChange(DashTheme.entries[it]) },
        )

        // —— Background / power ——
        SectionLabel("Background / power")
        ActionRow(
            title = "Unrestricted battery",
            subtitle = if (batteryUnrestricted) {
                "Allowed — background ELM session and logging can keep running."
            } else {
                "Allow unrestricted battery so live ELM and logging keep running in the background."
            },
            actionLabel = KeepAlivePolicy.batteryExemptionActionLabel(batteryUnrestricted),
            onClick = { if (!batteryUnrestricted) onKeepAliveBattery() },
        )

        // —— Alerts ——
        SectionLabel("Alerts")
        ToggleRow(
            title = "Voice alerts",
            subtitle = "Beep + speak on critical (battery also warns when orange/weak).",
            checked = settings.voiceAlerts,
            onCheckedChange = onToggleVoiceAlerts,
        )
        ToggleRow(
            title = "CarPlay / Android Auto connected",
            subtitle = "Yes (recommended with Z-Link): play alerts without ducking media. No: briefly lower media during alerts — some HUs never restore volume.",
            // UI is inverted vs duckMedia: connected=Yes → do NOT duck.
            checked = !settings.duckMediaDuringAlerts,
            onCheckedChange = { connected -> onToggleDuckMedia(!connected) },
        )
        ActionRow(
            title = "Check sound alert",
            subtitle = "Play battery-critical beep + voice now — verify media volume stays up afterward.",
            actionLabel = "PLAY",
            onClick = onCheckSoundAlert,
        )

        // —— Private keys ——
        SectionLabel("Private keys")
        var openAiDraft by remember(openAiApiKey) { mutableStateOf(openAiApiKey) }
        var tokenDraft by remember(githubToken) { mutableStateOf(githubToken) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(14.dp),
        ) {
            Text("OpenAI key", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Used for DIAG → Analyze via AI (gpt-4o-mini). Paste from platform.openai.com.",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            BasicTextField(
                value = openAiDraft,
                onValueChange = {
                    openAiDraft = it
                    onOpenAiApiKeyChange(it)
                },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (openAiDraft.isBlank()) {
                        Text("sk-… paste key", color = TextMuted, fontSize = 13.sp)
                    }
                    inner()
                },
            )

            Text(
                text = "GitHub key",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Fine-grained PAT with Contents: Read and write on this repo — uploads logs and AI reports.",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            BasicTextField(
                value = tokenDraft,
                onValueChange = {
                    tokenDraft = it
                    onGithubTokenChange(it)
                },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (tokenDraft.isBlank()) {
                        Text("github_pat_… / ghp_… paste token", color = TextMuted, fontSize = 13.sp)
                    }
                    inner()
                },
            )
        }

        // —— Logging ——
        SectionLabel("Logging")
        NavRow("Debug log (raw ELM327)", nav.onDebug)
        NavRow("Saved logs & AI reports", nav.onValues)
        ActionRow(
            title = "Upload saved logs + AI reports",
            subtitle = buildString {
                append(if (uploadStatus.online) "Online" else "Offline")
                append(" · ${uploadStatus.pendingCount} pending · ${uploadStatus.syncedCount} synced")
                if (uploadStatus.lastMessage.isNotBlank()) append(" · ${uploadStatus.lastMessage}")
            },
            actionLabel = if (uploadStatus.uploading) "…" else "UPLOAD",
            onClick = onUploadLogs,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = LocalThemePalette.current.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}


@Composable
private fun SettingDropdown(
    label: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    options: List<Pair<String, String>>,
    onSelectIndex: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { expanded = true }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextMuted, fontSize = 12.sp)
            }
            Text("▾", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, (title, sub) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(title, fontWeight = FontWeight.SemiBold)
                            Text(sub, color = TextMuted, fontSize = 12.sp)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectIndex(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: VehicleProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayName,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = profile.subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Text(
            text = if (selected) "SELECTED" else "SELECT",
            color = if (selected) GoodGreen else MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ThemeRow(
    theme: DashTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.displayName,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = theme.subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Text(
            text = if (selected) "SELECTED" else "SELECT",
            color = if (selected) GoodGreen else MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onCheckedChange(!checked) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = GoodGreen,
            ),
        )
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(text = "\u203A", color = TextMuted, fontSize = 20.sp)
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Text(
            text = actionLabel,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
