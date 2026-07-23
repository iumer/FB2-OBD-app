package com.fb2.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.GoodGreen
import com.fb2.obd.ui.theme.Surface
import com.fb2.obd.ui.theme.TextMuted
import com.fb2.obd.ui.theme.TextPrimary

/** UI model for a paired Bluetooth device (decoupled from android.bluetooth). */
data class BtDeviceUi(val name: String, val address: String)

/** Dialog wrapper around [ConnectSheetContent]. */
@Composable
fun ConnectDialog(
    devices: List<BtDeviceUi>,
    onPickDevice: (BtDeviceUi) -> Unit,
    onPickDemo: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        ConnectSheetContent(
            devices = devices,
            onPickDevice = onPickDevice,
            onPickDemo = onPickDemo,
        )
    }
}

@Composable
fun ConnectSheetContent(
    devices: List<BtDeviceUi>,
    onPickDevice: (BtDeviceUi) -> Unit,
    onPickDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Background)
            .padding(20.dp)
            .width(420.dp),
    ) {
        Text(
            text = "Connect data source",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Pick your paired ELM327 adapter for live data.",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        if (devices.isEmpty()) {
            Text(
                text = "No paired Bluetooth devices found.\n\n" +
                    "Pair your ELM327 first: Android Settings \u2192 Bluetooth \u2192 " +
                    "pair the adapter (PIN is usually 1234 or 0000), then reopen this.",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                devices.forEach { d ->
                    DeviceRow(d, onClick = { onPickDevice(d) })
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .clickable { onPickDemo() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(TextMuted)
            Text(
                text = "  Use demo (simulated) instead",
                color = TextMuted,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun DeviceRow(device: BtDeviceUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(GoodGreen)
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = device.name.ifBlank { "(unnamed device)" },
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = device.address, color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Row(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color),
    ) {}
}
