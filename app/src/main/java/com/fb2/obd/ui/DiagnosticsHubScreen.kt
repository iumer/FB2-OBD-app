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
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary

data class DiagnosticsNav(
    val onFaults: () -> Unit = {},
    val onDeepDiag: () -> Unit = {},
    val onVehicle: () -> Unit = {},
    val onHonda: () -> Unit = {},
    val onMaintenance: () -> Unit = {},
)

/**
 * Hub opened from the dashboard DIAGNOSTICS button — fault codes, deep diag,
 * vehicle info, Honda modules, maintenance (live sensor pages live on swipes).
 */
@Composable
fun DiagnosticsHubScreen(
    nav: DiagnosticsNav,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Diagnostics", onBack = onBack)
        Text(
            text = "Read / clear codes, deep scans, VIN, Honda modules, and maintenance. Live sensor pages are on the dashboard swipe tabs.",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        DiagRow("Fault codes (read / clear + tips)", nav.onFaults)
        DiagRow("Deep diagnostics (freeze / readiness / Mode 05+06)", nav.onDeepDiag)
        DiagRow("Vehicle info (VIN / Mode 09)", nav.onVehicle)
        DiagRow("Honda modules / full-system probe", nav.onHonda)
        DiagRow("Maintenance log book", nav.onMaintenance)
    }
}

@Composable
private fun DiagRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(text = "\u203A", color = Accent, fontSize = 20.sp)
    }
}
