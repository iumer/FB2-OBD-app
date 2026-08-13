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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.FaultsState
import com.fb2.obd.obd.Dtc
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.CritRed
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.ui.theme.WarnAmber

@Composable
fun FaultsScreen(
    state: FaultsState,
    onRead: () -> Unit,
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
        ScreenHeader(title = "Fault codes", onBack = onBack) {
            Row {
                ActionButton(if (state.loading) "Reading\u2026" else "Read", Accent, onRead)
                ActionButton("Clear", CritRed, onClear)
            }
        }

        state.message?.let {
            Text(text = it, color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (!state.hasRead && state.message == null) {
            Text(
                text = "Tap Read to scan the ECU for stored (Mode 03), pending (Mode 07), and permanent (Mode 0A) trouble codes.",
                color = TextMuted,
                fontSize = 13.sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.stored.isNotEmpty()) {
                SectionTitle("Stored (${state.stored.size})", CritRed)
                state.stored.forEach { DtcRow(it, CritRed) }
            }
            if (state.pending.isNotEmpty()) {
                SectionTitle("Pending (${state.pending.size})", WarnAmber)
                state.pending.forEach { DtcRow(it, WarnAmber) }
            }
            if (state.permanent.isNotEmpty()) {
                SectionTitle("Permanent (${state.permanent.size})", TextMuted)
                state.permanent.forEach { DtcRow(it, TextMuted) }
            }
            if (state.hasRead && state.stored.isEmpty() && state.pending.isEmpty() && state.permanent.isEmpty()) {
                Text(text = "\u2713 No codes.", color = GoodGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun DtcRow(dtc: Dtc, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Text(text = dtc.code, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "   ${com.fb2.obd.obd.DtcCatalog.explain(dtc.code)}",
            color = TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ActionButton(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
