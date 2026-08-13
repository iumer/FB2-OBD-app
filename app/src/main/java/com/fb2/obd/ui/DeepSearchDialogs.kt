package com.fb2.obd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.DeepSearchUiState
import com.fb2.obd.obd.DeepSearchKnowledgeBase
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.WarnAmber

@Composable
private fun DialogScrollBody(content: @Composable () -> Unit) {
    // Landscape dialogs clip long text; force a bounded, scrollable body.
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.45f).dp.coerceAtLeast(120.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxH)
            .verticalScroll(rememberScrollState()),
    ) {
        content()
    }
}

@Composable
fun DeepSearchDialogs(
    state: DeepSearchUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.active) return

    val report = state.report
    when {
        state.running -> {
            AlertDialog(
                onDismissRequest = { /* block cancel mid-run — teardown must finish */ },
                title = { Text("Deep research running…", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    DialogScrollBody {
                        Text(
                            text = state.progress.ifBlank { "Searching protocol library (modes, headers, Honda IDs)…" },
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    }
                },
                confirmButton = {},
                containerColor = MaterialTheme.colorScheme.background,
            )
        }

        report != null -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = if (report.success) "Sensor found" else "Still unable to find",
                        color = if (report.success) GoodGreen else WarnAmber,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    DialogScrollBody {
                        Text(
                            text = report.targetLabel,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        if (report.success) {
                            val hit = report.hit!!
                            Text(
                                text = "%.2f %s".format(hit.value, hit.strategy.unit).trim(),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "via ${hit.strategy.title}",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                            )
                        }
                        report.notes.forEach { note ->
                            Text(
                                text = "• $note",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Text(
                            text = "Tried ${report.attempts} strategies.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("OK", color = MaterialTheme.colorScheme.primary) }
                },
                containerColor = MaterialTheme.colorScheme.background,
            )
        }

        state.confirmLabel != null -> {
            val label = state.confirmLabel
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text("Deep research this sensor?", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    DialogScrollBody {
                        Text(
                            text = "\"$label\" is showing n/s. Run a deep analysis to try fetching this value?",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            text = "Searches the protocol library: force Mode 01, ISO/CAN switches, ECM/TCM headers (ATSH), and alternate Honda Mode 22 IDs — not just one request.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            text = DeepSearchKnowledgeBase.explainLikelyCause(label, null),
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "Scroll for full text. Demo: try Coolant 2, Ambient, or LTFT.",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onConfirm) {
                        Text("Yes — deep research", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("No", color = TextPrimary) }
                },
                containerColor = MaterialTheme.colorScheme.background,
            )
        }
    }
}
