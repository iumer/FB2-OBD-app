package com.fb2.obd.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.AiAnalyzeUiState
import com.fb2.obd.data.SavedAiReport
import com.fb2.obd.data.SavedLogFile
import com.fb2.obd.obd.AiAnalysisPayloadBuilder
import com.fb2.obd.obd.VehicleProfile
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.CritRed
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.WarnAmber
import kotlin.math.roundToInt

/**
 * One-shot Analyze via AI — live lookback window or a saved session CSV.
 * History: Analyze sits on the selected log row (no bottom hunt / no time slider).
 */
@Composable
fun AiAnalyzeScreen(
    state: AiAnalyzeUiState,
    savedLogs: List<SavedLogFile>,
    savedAiReports: List<SavedAiReport> = emptyList(),
    hasApiKey: Boolean,
    vehicleProfile: VehicleProfile = VehicleProfile.DEFAULT,
    onModeLive: (Boolean) -> Unit,
    onWindowMinutes: (Int) -> Unit,
    onSelectLog: (String?) -> Unit,
    onDriverNotes: (String) -> Unit = {},
    onAnalyze: () -> Unit,
    onClearReport: () -> Unit,
    onRefreshLogs: () -> Unit,
    onRefreshAiReports: () -> Unit = {},
    onOpenAiReport: (String) -> Unit = {},
    onCloseAiReport: () -> Unit = {},
    onShareAiReport: (String) -> Unit = {},
    onDeleteAiReport: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** True when the connected source is Demo (not live ELM). */
    liveSourceIsDemo: Boolean = false,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onRefreshLogs()
        onRefreshAiReports()
    }

    // Full-report viewer takes over the screen when open.
    val viewing = state.viewingReportText
    if (viewing != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            ScreenHeader(
                title = state.viewingReportName ?: "AI report",
                onBack = onCloseAiReport,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "COPY",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("FB2 AI full report", viewing))
                        }
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                state.viewingReportName?.let { name ->
                    Text(
                        text = "SHARE",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onShareAiReport(name) }
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Text(
                text = viewing,
                color = TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp),
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Analyze via AI", onBack = onBack)
        Text(
            text = when (vehicleProfile) {
                VehicleProfile.FB2 ->
                    "One-shot OpenAI report for Honda Civic FB2. Read-only — no chat. Full .txt is saved and listed below; uploads with GitHub log sync."
                VehicleProfile.GENERIC_OBD2 ->
                    "One-shot OpenAI report for Generic OBD2 (any SAE OBD-II car). Do not expect Honda FB2 wording. Full .txt is saved and listed below."
            },
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (!hasApiKey) {
            Text(
                text = "OpenAI API key missing. Add it in Settings → AI analysis (platform.openai.com — Plus does not include API).",
                color = WarnAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onOpenSettings)
                    .padding(14.dp),
            )
            Text(
                text = "Tap to open Settings",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeChip("Live data", selected = state.modeLive) { onModeLive(true) }
            ModeChip("From history", selected = !state.modeLive) { onModeLive(false) }
        }

        if (liveSourceIsDemo && state.modeLive) {
            Text(
                text = "DEMO mode — these readings are simulated, not from a live vehicle/ELM. Saved report filename will include \"demo\".",
                color = WarnAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
            )
        }

        Text(
            text = "Time window: ${state.windowMinutes} min",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (state.modeLive) {
                "Uses the last ${state.windowMinutes} minutes of the current auto-LOG (look back)."
            } else {
                "Uses the last ${state.windowMinutes} minutes of the selected saved log (from file end). Size-capped if dense."
            },
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Slider(
            value = state.windowMinutes.toFloat(),
            onValueChange = { onWindowMinutes(it.roundToInt()) },
            valueRange = AiAnalysisPayloadBuilder.MIN_WINDOW_MINUTES.toFloat()..
                AiAnalysisPayloadBuilder.MAX_WINDOW_MINUTES.toFloat(),
            steps = AiAnalysisPayloadBuilder.MAX_WINDOW_MINUTES -
                AiAnalysisPayloadBuilder.MIN_WINDOW_MINUTES - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = TextMuted.copy(alpha = 0.35f),
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        Text(
            text = "Driver notes for this analysis",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Optional. Sent with the prompt (e.g. “Daihatsu Mira 2015, MAP-only, 6-injector FWD”). Max ${AiAnalysisPayloadBuilder.MAX_DRIVER_NOTES_CHARS} chars.",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        BasicTextField(
            value = state.driverNotes,
            onValueChange = onDriverNotes,
            enabled = !state.loading,
            textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            decorationBox = { inner ->
                if (state.driverNotes.isBlank()) {
                    Text(
                        text = "Vehicle / engine context for the AI…",
                        color = TextMuted,
                        fontSize = 13.sp,
                    )
                }
                inner()
            },
        )

        if (state.modeLive) {
            LiveAnalyzeBar(
                loading = state.loading,
                canClear = !state.loading &&
                    (state.reportText != null || state.error != null || state.savedReport != null),
                onAnalyze = onAnalyze,
                onClear = onClearReport,
            )
        } else {
            Text(
                text = "Saved logs",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Tap a log to select it, then tap Analyze on that row.",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (savedLogs.isEmpty()) {
                Text(
                    text = "No saved logs yet. Connect ELM (auto-LOG) or finish a session first.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            } else {
                savedLogs.forEach { log ->
                    val selected = log.fileName == state.selectedLogFileName
                    val logIsDemo = log.fileName.contains("demo", ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                else Modifier,
                            )
                            .clickable(enabled = !state.loading) { onSelectLog(log.fileName) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.displayName,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = buildString {
                                    append("${log.sizeBytes / 1024} KB")
                                    if (logIsDemo) append(" · DEMO (simulated)")
                                },
                                color = if (logIsDemo) WarnAmber else TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                        if (selected) {
                            Text(
                                text = if (state.loading) "…" else "Analyze",
                                color = if (state.loading) TextMuted else MaterialTheme.colorScheme.background,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (state.loading) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primary)
                                    .clickable(enabled = !state.loading) { onAnalyze() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            val canClear = !state.loading &&
                (state.reportText != null || state.error != null || state.savedReport != null)
            if (canClear) {
                Text(
                    text = "Clear report",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClearReport() }
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (state.loading) {
            Text(
                text = "Generating a fresh report… previous results cleared.",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        state.error?.let { err ->
            Text(
                text = err,
                color = CritRed,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (state.limitedData && state.reportText != null) {
            Text(
                text = "Limited data window — treat findings cautiously.",
                color = WarnAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        // Filename is appended inside reportText ("Full report saved to:") — avoid a duplicate line.

        state.reportText?.let { report ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI brief", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "CLEAR",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClearReport() }
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Text(
                        text = "COPY",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("FB2 AI report", report))
                            }
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Text(
                text = report,
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp),
            )
            state.savedReport?.let { saved ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "OPEN FULL",
                        color = MaterialTheme.colorScheme.background,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onOpenAiReport(saved.fileName) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    Text(
                        text = "SHARE",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onShareAiReport(saved.fileName) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Text(
                text = "Tip: OPEN FULL shows the complete .txt in the app (findings + readings table).",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (savedAiReports.isNotEmpty()) {
            Text(
                text = "Saved AI reports",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            savedAiReports.take(20).forEach { rep ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                ) {
                    Text(rep.fileName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${rep.sizeBytes / 1024} KB",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "OPEN",
                            color = GoodGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenAiReport(rep.fileName) }
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Text(
                            text = "SHARE",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onShareAiReport(rep.fileName) }
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Text(
                            text = "DEL",
                            color = CritRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDeleteAiReport(rep.fileName) }
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAnalyzeBar(
    loading: Boolean,
    canClear: Boolean,
    onAnalyze: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (loading) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary)
                .clickable(enabled = !loading) { onAnalyze() }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (loading) "Analyzing…" else "Analyze",
                color = if (loading) TextMuted else MaterialTheme.colorScheme.background,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = canClear) { onClear() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Clear",
                color = if (canClear) TextPrimary else TextMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) MaterialTheme.colorScheme.background else TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
