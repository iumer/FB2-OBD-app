package com.fb2.obd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.fb2.obd.ui.BtDeviceUi
import com.fb2.obd.ui.ConnectSheetContent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.FB2Theme
import org.junit.Rule
import org.junit.Test

class ConnectSheetSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(orientation = ScreenOrientation.LANDSCAPE),
    )

    @Test
    fun connectSheet_withPairedDevices() {
        val devices = listOf(
            BtDeviceUi(name = "OBDII", address = "00:1D:A5:68:98:8B"),
            BtDeviceUi(name = "ELM327 v1.5", address = "AA:BB:CC:11:22:33"),
            BtDeviceUi(name = "Car Kit", address = "12:34:56:78:9A:BC"),
        )
        paparazzi.snapshot {
            FB2Theme {
                Box(
                    modifier = Modifier.fillMaxSize().background(Background),
                    contentAlignment = Alignment.Center,
                ) {
                    ConnectSheetContent(
                        devices = devices,
                        onPickDevice = {},
                        onPickDemo = {},
                    )
                }
            }
        }
    }
}
