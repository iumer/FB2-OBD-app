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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.SettingsState
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary

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

data class SettingsNav(
    val onFaults: () -> Unit = {},
    val onPerformance: () -> Unit = {},
    val onCustom: () -> Unit = {},
    val onFuel: () -> Unit = {},
    val onIdle: () -> Unit = {},
    val onTrip: () -> Unit = {},
    val onVehicle: () -> Unit = {},
    val onDeepDiag: () -> Unit = {},
    val onTrans: () -> Unit = {},
    val onHealth: () -> Unit = {},
    val onMaintenance: () -> Unit = {},
    val onHonda: () -> Unit = {},
    val onGForce: () -> Unit = {},
    val onDebug: () -> Unit = {},
    val onValues: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    settings: SettingsState,
    onToggleValueLogging: (Boolean) -> Unit,
    onToggleEstimatedGear: (Boolean) -> Unit,
    nav: SettingsNav,
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
        ScreenHeader(title = "Settings", onBack = onBack)

        SectionLabel("Gear")
        ToggleRow(
            title = "Show estimated gear",
            subtitle = "When the ECU can't report the actual gear, estimate from speed & RPM (EST).",
            checked = settings.showEstimatedGear,
            onCheckedChange = onToggleEstimatedGear,
        )

        SectionLabel("Logging")
        ToggleRow(
            title = "Record value log",
            subtitle = "Dashboard snapshots + every page Probe (custom/fuel/idle/trans/Honda) into CSV export. Debug Share always includes PROBE lines too.",
            checked = settings.valueLogging,
            onCheckedChange = onToggleValueLogging,
        )

        SectionLabel("Live pages")
        NavRow("Custom sensors (+ full catalog)", nav.onCustom)
        NavRow("Cold start / rough idle (misfire + fuel)", nav.onIdle)
        NavRow("Fuel system page", nav.onFuel)
        NavRow("Trip computer / economy", nav.onTrip)
        NavRow("Transmission dashboard", nav.onTrans)
        NavRow("Performance (0\u2013100 / \u00BC mile)", nav.onPerformance)
        NavRow("G-force meter", nav.onGForce)
        NavRow("Health scores", nav.onHealth)

        SectionLabel("Diagnostics")
        NavRow("Fault codes (read / clear + AI tips)", nav.onFaults)
        NavRow("Deep diagnostics (freeze / readiness / Mode 05+06)", nav.onDeepDiag)
        NavRow("Vehicle info (VIN / Mode 09)", nav.onVehicle)
        NavRow("Honda modules / full-system probe", nav.onHonda)
        NavRow("Maintenance log book", nav.onMaintenance)
        NavRow("Debug log (raw ELM327)", nav.onDebug)
        NavRow("Value log (CSV)", nav.onValues)
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
