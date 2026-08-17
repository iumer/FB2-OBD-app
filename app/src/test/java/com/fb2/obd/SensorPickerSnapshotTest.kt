package com.fb2.obd

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.SensorPickerContent
import com.fb2.obd.ui.theme.FB2Theme
import org.junit.Rule
import org.junit.Test

class SensorPickerSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenHeight = 1280,
            screenWidth = 576,
            xdpi = 320,
            ydpi = 320,
            orientation = ScreenOrientation.PORTRAIT,
            density = Density.XHIGH,
            ratio = ScreenRatio.NOTLONG,
            size = ScreenSize.NORMAL,
            softButtons = false,
        ),
    )

    @Test
    fun selectSensor_liveGreenAndNoData() {
        val catalog = listOf(
            pid("010C"),
            pid("0105"),
            pid("010D"),
            pid("0110"),
            pid("010B"),
            pid("0146"),
            pid("0107"),
            pid("0167"),
            pid("0114"),
            pid("0115"),
        )
        val snap = VehicleSnapshot(
            rpm = 711.0,
            coolantC = 73.0,
            speedKmh = 0.0,
            mafGps = 4.2,
            mapKpa = 32.0,
            unsupportedPids = setOf(0x46, 0x07, 0x67),
        )
        val o2 = pid("0114")
        val probe = mapOf(
            o2.id to PidProbeResult(o2, supported = true, sample = 0.45, raw = "41 14 5A"),
        )
        paparazzi.snapshot {
            FB2Theme {
                SensorPickerContent(
                    catalog = catalog,
                    snapshot = snap,
                    probeById = probe,
                    scanning = false,
                    onPick = {},
                    onDismiss = {},
                )
            }
        }
    }

    private fun pid(request: String) =
        StandardPidCatalog.all.first { it.request.equals(request, true) }
}
