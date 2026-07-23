package com.fb2.obd

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.DashboardScreen
import com.fb2.obd.ui.theme.FB2Theme
import org.junit.Rule
import org.junit.Test

/**
 * Renders the real Compose dashboard to a PNG on the JVM (via layoutlib) so the
 * UI can be reviewed without an emulator/device — required in the cloud VM where
 * no /dev/kvm is available.
 */
class DashboardSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(orientation = ScreenOrientation.LANDSCAPE),
    )

    @Test
    fun dashboard_liveDriving() {
        val speed = 96.0
        val rpm = 2450.0
        val snapshot = VehicleSnapshot(
            rpm = rpm,
            speedKmh = speed,
            coolantC = 92.0,
            intakeC = 34.0,
            ambientC = 28.0,
            engineLoadPct = 47.0,
            throttlePct = 31.0,
            timingAdvance = 14.0,
            mafGps = 12.4,
            mapKpa = 58.0,
            stftPct = 2.3,
            ltftPct = 3.5,
            batteryVolts = 14.2,
            gear = GearEstimator().estimate(speed, rpm),
        )
        val state = DashboardUiState(
            snapshot = snapshot,
            connection = ConnectionState.CONNECTED,
            sourceName = "Demo (simulated)",
        )
        paparazzi.snapshot {
            FB2Theme {
                DashboardScreen(state = state)
            }
        }
    }

    @Test
    fun dashboard_faultConditions() {
        val speed = 42.0
        val rpm = 3200.0
        val snapshot = VehicleSnapshot(
            rpm = rpm,
            speedKmh = speed,
            coolantC = 112.0,   // overheating -> red
            intakeC = 51.0,
            ambientC = 39.0,
            engineLoadPct = 88.0,
            throttlePct = 74.0,
            timingAdvance = 6.0,
            mafGps = 22.0,
            mapKpa = 92.0,
            stftPct = -13.0,    // large trim -> red
            ltftPct = 8.5,      // elevated -> amber
            batteryVolts = 12.1, // low charge -> red
            gear = GearEstimator().estimate(speed, rpm),
        )
        val state = DashboardUiState(
            snapshot = snapshot,
            connection = ConnectionState.CONNECTED,
            sourceName = "Demo (simulated)",
        )
        paparazzi.snapshot {
            FB2Theme {
                DashboardScreen(state = state)
            }
        }
    }
}
