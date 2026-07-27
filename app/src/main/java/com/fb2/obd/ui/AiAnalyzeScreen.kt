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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.AiAnalyzeUiState
import com.fb2.obd.data.SavedLogFile
import com.fb2.obd.obd.AiAnalysisPayloadBuilder
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
 * Result is copyable and saved as `.txt` (synced to GitHub with logs).
 */
@Composable
fun AiAnalyzeScreen(
    state: AiAnalyzeUiState,
    savedLogs: List<SavedLogFile>,
    hasApiKey: Boolean,
    onModeLive: (Boolean) -> Unit,
    onWindowMinutes: (Int) -> Unit,
    onSelectLog: (String?) -> Unit,
    onAnalyze: () -> Unit,
    onRefreshLogs: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { onRefreshLogs() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Analyze via AI", onBack = onBack)
        Text(
            text = "One-shot OpenAI report for your FB2 Civic. Read-only — no chat thread. Report is saved as .txt and uploads with GitHub log sync.",
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
                    .background(Surface)
                    .clickable(onClick = onOpenSettings)
                    .padding(14.dp),
            )
            Text(
                text = "Tap to open Settings",
                color = Accent,
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
                "Uses the last ${state.windowMinutes} minutes from the selected saved log."
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
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = TextMuted.copy(alpha = 0.35f),
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        if (!state.modeLive) {
            Text(
                text = "Saved logs",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            if (savedLogs.isEmpty()) {
                Text(
                    text = "No saved logs yet. Connect ELM (auto-LOG) or finish a session first.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            } else {
                savedLogs.take(20).forEach { log ->
                    val selected = log.fileName == state.selectedLogFileName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface)
                            .then(
                                if (selected) Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp))
                                else Modifier,
                            )
                            .clickable { onSelectLog(log.fileName) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(log.displayName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${log.sizeBytes / 1024} KB",
                                color = TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                        if (selected) Text("✓", color = Accent, fontSize = 16.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (state.loading) Surface else Accent)
                .clickable(enabled = !state.loading) { onAnalyze() }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (state.loading) "Analyzing…" else "Analyze",
                color = if (state.loading) TextMuted else Background,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
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

        state.savedReport?.let { saved ->
            Text(
                text = "Saved: ${saved.fileName}",
                color = GoodGreen,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        state.reportText?.let { report ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI report", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "COPY",
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("FB2 AI report", report))
                        }
                        .background(Surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Text(
                text = report,
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .padding(14.dp),
            )
            Text(
                text = "Tip: scroll down in the report for the readings table. COPY includes AI findings + values. Paste into chatgpt.com to continue.",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Background else TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Accent else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
