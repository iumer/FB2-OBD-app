package com.fb2.obd.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.data.ObdLogger
import com.fb2.obd.data.SavedLogFile
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.CritRed
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
private val FILE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

@Composable
private fun HeaderAction(text: String, onClick: () -> Unit, color: androidx.compose.ui.graphics.Color = Accent) {
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
fun DebugLogScreen(
    lines: List<ObdLogger.DebugLine>,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
    ) {
        ScreenHeader(title = "Debug log", onBack = onBack) {
            Row {
                HeaderAction("Share", onShare)
                HeaderAction("Clear", onClear)
            }
        }

        if (lines.isEmpty()) {
            Text(
                text = "No traffic yet. Connect to the ELM327 to capture raw commands and responses.",
                color = TextMuted,
                fontSize = 13.sp,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                lines.takeLast(400).forEach { line ->
                    val color = when (line.dir) {
                        ObdLogger.Dir.TX -> Accent
                        ObdLogger.Dir.RX -> GoodGreen
                        ObdLogger.Dir.INFO -> TextMuted
                    }
                    Text(
                        text = "${TIME_FMT.format(line.timestampMs)} ${line.dir}  ${line.text}",
                        color = color,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
fun ValueLogScreen(
    rows: List<ObdLogger.ValueRow>,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    savedFiles: List<SavedLogFile> = emptyList(),
    loggingActive: Boolean = false,
    onShareFile: (SavedLogFile) -> Unit = {},
    onDeleteFile: (SavedLogFile) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
    ) {
        ScreenHeader(title = "Value log", onBack = onBack) {
            Row {
                HeaderAction("Share current", onShare)
                HeaderAction("Clear current", onClear)
            }
        }

        Text(
            text = if (loggingActive) {
                "LOGGING LIVE — ${rows.size} rows in this session. Tap STOP LOG on the dashboard to save a CSV file."
            } else {
                "Current buffer: ${rows.size} rows. Each STOP LOG saves a separate timestamped CSV below."
            },
            color = if (loggingActive) GoodGreen else TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (savedFiles.isNotEmpty()) {
            Text(
                text = "SAVED SESSIONS (${savedFiles.size})",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                savedFiles.take(30).forEach { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.displayName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${FILE_FMT.format(Date(file.startedMs))} · ${file.sizeBytes / 1024} KB",
                                color = TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            text = "Share",
                            color = Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onShareFile(file) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Text(
                            text = "Del",
                            color = CritRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDeleteFile(file) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            Text(
                text = "No live rows yet. Tap LOG on the dashboard (or enable Record value log in Settings), then drive.",
                color = TextMuted,
                fontSize = 13.sp,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                rows.takeLast(300).forEach { row ->
                    val s = row.snapshot
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = TIME_FMT.format(row.timestampMs),
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "  ${s.rpm?.toInt() ?: "--"}rpm ${s.speedKmh?.toInt() ?: "--"}km/h " +
                                "C1=${s.coolantC?.toInt() ?: "--"} V=${s.batteryVolts ?: "--"} g${s.gear ?: "-"}",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
