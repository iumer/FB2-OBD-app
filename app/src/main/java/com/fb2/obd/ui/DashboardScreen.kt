package com.fb2.obd.ui

import androidx.compose.foundation.background
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
import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthEvaluator
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.CritRed
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.TextMuted
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
fun DashboardScreen(state: DashboardUiState, modifier: Modifier = Modifier) {
    val s = state.snapshot
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
    ) {
        TopBar(state)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularGauge(
                label = "RPM",
                value = s.rpm,
                maxValue = 7000.0,
                unit = "rpm",
                arcColor = Accent,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GearIndicator(gear = s.gear)
            }
            CircularGauge(
                label = "SPEED",
                value = s.speedKmh,
                maxValue = 200.0,
                unit = "km/h",
                arcColor = GoodGreen,
            )
        }

        MetricGrid(s)
    }
}

@Composable
private fun TopBar(state: DashboardUiState) {
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
                ConnectionState.CONNECTED -> GoodGreen to "LIVE \u00B7 ${state.sourceName}"
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
        }
    }
}

@Composable
private fun MetricGrid(s: VehicleSnapshot) {
    val tiles: List<TileData> = listOf(
        TileData("Coolant", s.coolantC.fmt(), "\u00B0C", HealthEvaluator.coolant(s.coolantC)),
        TileData("Battery", s.batteryVolts.fmt(1), "V", HealthEvaluator.battery(s.batteryVolts)),
        TileData("Intake", s.intakeC.fmt(), "\u00B0C"),
        TileData("Ambient", s.ambientC.fmt(), "\u00B0C"),
        TileData("Load", s.engineLoadPct.fmt(), "%"),
        TileData("Throttle", s.throttlePct.fmt(), "%"),
        TileData("STFT", s.stftPct.fmt(1), "%", HealthEvaluator.fuelTrim(s.stftPct)),
        TileData("LTFT", s.ltftPct.fmt(1), "%", HealthEvaluator.fuelTrim(s.ltftPct)),
        TileData("MAF", s.mafGps.fmt(1), "g/s"),
        TileData("MAP", s.mapKpa.fmt(), "kPa"),
        TileData("Timing", s.timingAdvance.fmt(), "\u00B0"),
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
                    StatTile(
                        label = t.label,
                        value = t.value,
                        unit = t.unit,
                        health = t.health,
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
    val health: Health = Health.UNKNOWN,
)
