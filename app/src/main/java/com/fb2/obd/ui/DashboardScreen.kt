package com.fb2.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fb2.obd.DashboardUiState
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthEvaluator
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.CritRed
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.WarnAmber
import kotlin.math.roundToInt

private fun Double?.fmt(digits: Int = 0): String = this?.let {
    if (digits == 0) it.roundToInt().toString() else "%.${digits}f".format(it)
} ?: "--"

/**
 * Single-screen, landscape instrument cluster: RPM and Speed as large sweep
 * gauges flanking a gear indicator, with a grid of health-coloured metric tiles
 * below (temps, voltage, trims, load, throttle).
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    showEstimatedGear: Boolean = true,
    onConnectClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val s = state.snapshot
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
    ) {
        TopBar(state, onConnectClick, onSettingsClick)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularGauge(
                label = "RPM",
                value = s.rpm,
                maxValue = 7000.0,
                unit = "rpm",
                size = 190.dp,
                arcColor = Accent,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val gearSource = if (!showEstimatedGear && s.gearSource == GearSource.ESTIMATED) {
                    GearSource.NONE
                } else {
                    s.gearSource
                }
                val gear = if (gearSource == GearSource.NONE) null else s.gear
                GearIndicator(gear = gear, source = gearSource)
            }
            CircularGauge(
                label = "SPEED",
                value = s.speedKmh,
                maxValue = 200.0,
                unit = "km/h",
                size = 190.dp,
                arcColor = GoodGreen,
            )
        }

        MetricGrid(s)
    }
}

@Composable
private fun TopBar(
    state: DashboardUiState,
    onConnectClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FB2 DIAG",
            color = Accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (dot, text) = when (state.connection) {
                ConnectionState.CONNECTED ->
                    if (state.sourceIsLive) {
                        GoodGreen to "LIVE \u00B7 ${state.sourceName}"
                    } else {
                        WarnAmber to "DEMO \u00B7 simulated"
                    }
                ConnectionState.CONNECTING -> Accent to "CONNECTING \u00B7 ${state.sourceName}"
                ConnectionState.ERROR -> CritRed to "ERROR \u00B7 ${state.sourceName}"
                ConnectionState.DISCONNECTED -> TextMuted to "OFFLINE"
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(dot),
            )
            Text(text = "  $text", color = TextMuted, fontSize = 13.sp)

            Text(
                text = "SETTINGS",
                color = TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSettingsClick() }
                    .background(Surface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )

            Text(
                text = "CONNECT",
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onConnectClick() }
                    .background(Surface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun MetricGrid(s: VehicleSnapshot) {
    val engineRunning = (s.rpm ?: 0.0) > 0.0
    val tiles: List<TileData> = listOf(
        TileData("Coolant 1", s.coolantC.fmt(), "\u00B0C", HealthEvaluator.coolant(s.coolantC), ObdPid.COOLANT_TEMP),
        TileData("Coolant 2", s.coolant2C.fmt(), "\u00B0C", HealthEvaluator.coolant(s.coolant2C), ObdPid.COOLANT_TEMP_2),
        TileData("Battery", s.batteryVolts.fmt(1), "V", HealthEvaluator.battery(s.batteryVolts, engineRunning), ObdPid.CONTROL_MODULE_VOLTAGE),
        TileData("Intake", s.intakeC.fmt(), "\u00B0C", pid = ObdPid.INTAKE_TEMP),
        TileData("Ambient", s.ambientC.fmt(), "\u00B0C", pid = ObdPid.AMBIENT_TEMP),
        TileData("Load", s.engineLoadPct.fmt(), "%", pid = ObdPid.ENGINE_LOAD),
        TileData("Throttle", s.throttlePct.fmt(), "%", pid = ObdPid.THROTTLE),
        TileData("STFT", s.stftPct.fmt(1), "%", HealthEvaluator.fuelTrim(s.stftPct), ObdPid.STFT_B1),
        TileData("LTFT", s.ltftPct.fmt(1), "%", HealthEvaluator.fuelTrim(s.ltftPct), ObdPid.LTFT_B1),
        TileData("MAF", s.mafGps.fmt(1), "g/s", pid = ObdPid.MAF),
        TileData("MAP", s.mapKpa.fmt(), "kPa", pid = ObdPid.INTAKE_MAP),
        TileData("Timing", s.timingAdvance.fmt(), "\u00B0", pid = ObdPid.TIMING_ADVANCE),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.chunked(6).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowTiles.forEach { t ->
                    val unsupported = t.pid != null && t.pid.number in s.unsupportedPids
                    StatTile(
                        label = t.label,
                        value = if (unsupported) "n/s" else t.value,
                        unit = if (unsupported) "" else t.unit,
                        health = if (unsupported) null else t.health,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private data class TileData(
    val label: String,
    val value: String,
    val unit: String,
    val health: Health? = null,
    val pid: ObdPid? = null,
)
