package com.fb2.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.PerformanceState
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary

private fun fmt(sec: Double?): String = sec?.let { "%.2f s".format(it) } ?: "\u2014"

@Composable
fun PerformanceScreen(
    state: PerformanceState,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
    ) {
        ScreenHeader(title = "Performance", onBack = onBack) {
            Text(
                text = "Reset",
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onReset() }
                    .background(Surface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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

        Text(
            text = "Come to a stop, then accelerate \u2014 timing arms and starts automatically.",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        val c = state.current
        ResultRow("0 \u2013 100 km/h", fmt(c.zeroTo100Kmh))
        ResultRow("0 \u2013 60 mph", fmt(c.zeroTo60Mph))
        ResultRow("0 \u2013 160 km/h", fmt(c.zeroTo160Kmh))
        ResultRow("60 \u2013 100 km/h", fmt(c.sixtyTo100Kmh))
        ResultRow(
            "\u00BC mile",
            c.quarterMileSec?.let {
                "%.2f s @ %d km/h".format(it, (c.quarterMileTrapKmh ?: 0.0).toInt())
            } ?: "\u2014",
        )

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
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = value,
            color = if (highlight) GoodGreen else Accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
