package com.fb2.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.fb2.obd.obd.EditableMetric
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.fieldsFor
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import kotlin.math.roundToInt

/**
 * Long-press editor: adjust colour-band thresholds for one metric.
 * Step buttons are car-mount friendly (no soft keyboard required).
 */
@Composable
fun ThresholdEditorDialog(
    metric: EditableMetric,
    thresholds: HealthThresholds,
    onChangeField: (id: String, value: Double) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fields = remember(metric, thresholds) { thresholds.fieldsFor(metric) }
    var confirmReset by remember { mutableStateOf(false) }
    val step = when (metric) {
        EditableMetric.BATTERY -> 0.1
        EditableMetric.FUEL_TRIM -> 0.5
        EditableMetric.RPM, EditableMetric.TC_SLIP -> 50.0
        else -> 1.0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Edit ${metric.title}",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Long-press any coloured tile to tune its bands. Units: ${metric.unit}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                fields.forEach { field ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = field.label,
                                color = field.band.color(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(text = field.hint, color = TextMuted, fontSize = 10.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "−",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onChangeField(field.id, (field.value - step).roundToStep(step))
                                    }
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            Text(
                                text = formatThreshold(field.value, step),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp),
                            )
                            Text(
                                text = "+",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onChangeField(field.id, (field.value + step).roundToStep(step))
                                    }
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmReset = true }) {
                Text("Reset all", color = TextMuted)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    )

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset thresholds?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Restore every colour band (all metrics) to FB2 factory defaults.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onResetAll()
                    confirmReset = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        )
    }
}

private fun formatThreshold(value: Double, step: Double): String =
    if (step < 1.0) "%.1f".format(value) else value.roundToInt().toString()

private fun Double.roundToStep(step: Double): Double {
    if (step <= 0) return this
    val n = (this / step).roundToInt()
    return n * step
}
