package com.fb2.obd

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.DashTheme
import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.SnapshotFreshness
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.DashboardScreen
import com.fb2.obd.ui.theme.FB2Theme
import com.fb2.obd.ui.theme.ThemePalette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Landscape smoke renders for every Dash theme.
 * Catches theme crashes / empty layouts before sideload.
 */
@RunWith(Parameterized::class)
class ThemeDashboardSnapshotTest(private val theme: DashTheme) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(orientation = ScreenOrientation.LANDSCAPE),
    )

    @Test
    fun dashboard_theme_driving() {
        val speed = 96.0
        val rpm = 2450.0
        val base = VehicleSnapshot(
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
            gearConfidencePct = 88,
            unsupportedPids = setOf(0x67, 0x46, 0x07),
        )
        val snapshot = base.copy(
            freshAtMs = SnapshotFreshness.mapForPresentFields(base, System.currentTimeMillis()),
        )
        val state = DashboardUiState(
            snapshot = snapshot,
            connection = ConnectionState.CONNECTED,
            sourceName = "ELM327 (Bluetooth)",
            sourceIsLive = true,
            dtcCount = 0,
        )
        val palette = ThemePalette.of(theme)
        paparazzi.snapshot(name = "dash_${theme.id}") {
            FB2Theme(palette = palette) {
                DashboardScreen(
                    state = state,
                    dashTheme = theme,
                    showEstimatedGear = true,
                    loggingActive = true,
                    networkOnline = true,
                    dtcCount = 0,
                    health = HealthScore(
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

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun themes(): List<DashTheme> = DashTheme.entries
    }
}
