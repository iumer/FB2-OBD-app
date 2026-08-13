package com.fb2.obd.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
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
import com.fb2.obd.obd.VehicleProfile
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.WarnAmber

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
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
                color = Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .background(Surface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Text(
                text = "   $title",
                color = TextPrimary,
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
    onToggleEstimatedGear: (Boolean) -> Unit,
    onToggleVoiceAlerts: (Boolean) -> Unit = {},
    onToggleDuckMedia: (Boolean) -> Unit = {},
    onCheckSoundAlert: () -> Unit = {},
    uploadStatus: LogUploadManager.Status = LogUploadManager.Status(),
    githubToken: String = "",
    onGithubTokenChange: (String) -> Unit = {},
    onUploadLogs: () -> Unit = {},
    openAiApiKey: String = "",
    onOpenAiApiKeyChange: (String) -> Unit = {},
    nav: SettingsNav,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp)
            .verticalScroll(scrollState),
    ) {
        ScreenHeader(title = "Settings", onBack = onBack)

        Text(
            text = "Live pages are on Dash swipe tabs. Faults / deep scan / VIN open from DIAGNOSTICS.",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        SectionLabel("Vehicle profile")
        Text(
            text = "FB2 keeps Honda Mode 22 / Transmission. Generic OBD2 is SAE-only for any OBD-II car.",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        VehicleProfile.entries.forEach { profile ->
            ProfileRow(
                profile = profile,
                selected = settings.vehicleProfile == profile,
                onClick = { onVehicleProfileChange(profile) },
            )
        }

        SectionLabel("Gear")
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

        SectionLabel("AI analysis")
        Text(
            text = "OpenAI API key for DIAGNOSTICS → Analyze via AI. ChatGPT Plus does not include API access — use platform.openai.com billing. Model: gpt-4o-mini (pay per use).",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        var openAiDraft by remember(openAiApiKey) { mutableStateOf(openAiApiKey) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .padding(14.dp),
        ) {
            Text("OpenAI API key", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = openAiDraft,
                onValueChange = {
                    openAiDraft = it
                    onOpenAiApiKeyChange(it)
                },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Background)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (openAiDraft.isBlank()) {
                        Text("sk-… paste key", color = TextMuted, fontSize = 13.sp)
                    }
                    inner()
                },
            )
        }

        SectionLabel("Logging")
        Text(
            text = "Real ELM connect auto-starts Dash value LOG until you tap STOP LOG. Sessions save as FB2-log-yyyyMMdd-HHmmss.csv (demo sessions: FB2-log-demo-…).",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        NavRow("Debug log (raw ELM327)", nav.onDebug)
        NavRow("Saved value logs (CSV)", nav.onValues)

        SectionLabel("Log upload")
        Text(
            text = if (uploadStatus.online) "Internet: online" else "Internet: offline",
            color = if (uploadStatus.online) GoodGreen else WarnAmber,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = "Uploads finished drives to …/${LogUploadManager.REMOTE_DIR}/ and AI reports to …/${LogUploadManager.REMOTE_AI_DIR}/. Needs a fine-grained PAT with Contents: Write.",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        var tokenDraft by remember(githubToken) { mutableStateOf(githubToken) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .padding(14.dp),
        ) {
            Text("GitHub token", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = tokenDraft,
                onValueChange = {
                    tokenDraft = it
                    onGithubTokenChange(it)
                },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Background)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (tokenDraft.isBlank()) {
                        Text("ghp_… paste token", color = TextMuted, fontSize = 13.sp)
                    }
                    inner()
                },
            )
        }
        ActionRow(
            title = "Upload saved logs + AI reports",
            subtitle = buildString {
                append("${uploadStatus.pendingCount} pending · ${uploadStatus.syncedCount} synced")
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
        color = TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
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
            .background(Surface)
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
            color = if (selected) GoodGreen else Accent,
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
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
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
                checkedThumbColor = Background,
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
            .background(Surface)
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
            .background(Surface)
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
            color = Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
