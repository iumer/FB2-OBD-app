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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.PerformanceState
import com.fb2.obd.perf.AccelPhase
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.WarnAmber

private fun fmt(sec: Double?): String = sec?.let { "%.2f s".format(it) } ?: "\u2014"

@Composable
fun PerformanceScreen(
    state: PerformanceState,
    onReset: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    phase: AccelPhase = AccelPhase.NEED_STOP,
    /** When true (dashboard swipe embed), hide the Back header. */
    embedded: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(if (embedded) 4.dp else 16.dp)
            .padding(bottom = 16.dp),
    ) {
        if (embedded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Performance", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Reset",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onReset() }
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        } else {
            ScreenHeader(title = "Performance", onBack = onBack) {
                Text(
                    text = "Reset",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onReset() }
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = state.currentSpeedKmh?.toInt()?.toString() ?: "0",
                color = TextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = " km/h", color = TextMuted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
        }

        val (status, statusColor) = when (phase) {
            AccelPhase.NEED_STOP ->
                "Drive, then come to a COMPLETE STOP. Timing will arm only after that stop." to WarnAmber
            AccelPhase.ARMED ->
                "ARMED \u2014 accelerate hard. Timer starts when speed leaves 0." to GoodGreen
            AccelPhase.RUNNING ->
                "TIMING \u2026 keep accelerating through the milestones." to MaterialTheme.colorScheme.primary
        }
        Text(
            text = status,
            color = statusColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Text(
            text = "Reset does not start a run. Casual driving after Reset is ignored until you stop, then launch.",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        val c = state.current
        ResultRow("0 \u2013 100 km/h", fmt(c.zeroTo100Kmh))
        ResultRow("0 \u2013 160 km/h", fmt(c.zeroTo160Kmh))
        ResultRow("60 \u2013 100 km/h", fmt(c.sixtyTo100Kmh))

        Text(
            text = "BEST",
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        ResultRow("Best 0 \u2013 100 km/h", fmt(state.best.zeroTo100Kmh), highlight = true)
    }
}

@Composable
private fun ResultRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = value,
            color = if (highlight) GoodGreen else MaterialTheme.colorScheme.primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
