package com.fb2.obd

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.GearSource
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
            coolantC = 85.0,
            coolant2C = 82.0,
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
            fuelSystemStatus = "CLOSED LOOP",
            gear = GearEstimator().estimate(speed, rpm),
            gearSource = GearSource.ESTIMATED,
            // Mirror the real FB2: these PIDs aren't supported by the ECU.
            unsupportedPids = setOf(0x67, 0x46, 0x07),
        )
        val state = DashboardUiState(
            snapshot = snapshot,
            connection = ConnectionState.CONNECTED,
            sourceName = "ELM327 (Bluetooth)",
            sourceIsLive = true,
            dtcCount = 0,
        )
        paparazzi.snapshot {
            FB2Theme {
                DashboardScreen(
                    state = state,
                    dtcCount = 0,
                    health = com.fb2.obd.obd.HealthScore(
                        enginePct = 94,
                        transmissionPct = null,
                        engineDataOk = true,
                        transmissionDataOk = false,
                        engineNotes = listOf("Core engine parameters look OK"),
                    ),
                )
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
            coolantC = 105.0,   // overheating -> red (>103)
            coolant2C = 104.0,
            intakeC = 51.0,
            ambientC = 39.0,
            engineLoadPct = 88.0,
            throttlePct = 74.0,
            timingAdvance = -2.0, // yellow retard band
            mafGps = 22.0,
            mapKpa = 92.0,
            stftPct = -13.0,    // large trim -> orange
            ltftPct = 8.5,      // elevated -> amber
            batteryVolts = 12.5, // red alt weak while running
            fuelSystemStatus = "OPEN LOOP",
            gear = GearEstimator().estimate(speed, rpm),
            gearSource = GearSource.ESTIMATED,
        )
        val state = DashboardUiState(
            snapshot = snapshot,
            connection = ConnectionState.CONNECTED,
            sourceName = "Demo (simulated)",
            dtcCount = 2,
        )
        paparazzi.snapshot {
            FB2Theme {
                DashboardScreen(
                    state = state,
                    dtcCount = 2,
                    health = com.fb2.obd.obd.HealthScore(
                        enginePct = 48,
                        transmissionPct = 70,
                        engineDataOk = true,
                        transmissionDataOk = true,
                        engineNotes = listOf("Engine overheating", "2 stored DTC(s)"),
                    ),
                )
            }
        }
    }
}
