package com.fb2.obd

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import com.fb2.obd.car.CarDashBuilder
import com.fb2.obd.car.FloatingDashMetrics
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.DashboardScreen
import com.fb2.obd.ui.FloatingDashBubblePreview
import com.fb2.obd.ui.theme.FB2Theme
import org.junit.Rule
import org.junit.Test

/**
 * Car Android head-unit aspect ratios (Dellson-class 7–10" units).
 * Used to catch overflow/underflow before sideloading onto the HU.
 */
object CarHuDevices {
    /** Common 7" 1024×600 (~16:10). */
    val HU_1024x600 = DeviceConfig(
        screenHeight = 600,
        screenWidth = 1024,
        xdpi = 160,
        ydpi = 160,
        orientation = ScreenOrientation.LANDSCAPE,
        density = Density.MEDIUM,
        ratio = ScreenRatio.NOTLONG,
        size = ScreenSize.LARGE,
        softButtons = false,
    )

    /** Common 1280×720 16:9. */
    val HU_1280x720 = DeviceConfig(
        screenHeight = 720,
        screenWidth = 1280,
        xdpi = 180,
        ydpi = 180,
        orientation = ScreenOrientation.LANDSCAPE,
        density = Density.HIGH,
        ratio = ScreenRatio.LONG,
        size = ScreenSize.LARGE,
        softButtons = false,
    )

    /** Ultrawide double-DIN ~1920×720. */
    val HU_1920x720 = DeviceConfig(
        screenHeight = 720,
        screenWidth = 1920,
        xdpi = 200,
        ydpi = 200,
        orientation = ScreenOrientation.LANDSCAPE,
        density = Density.XHIGH,
        ratio = ScreenRatio.LONG,
        size = ScreenSize.XLARGE,
        softButtons = false,
    )
}

class CarHuDashboardSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = CarHuDevices.HU_1024x600)

    @Test
    fun dash_live_1024x600() = snapDash(paparazzi, fault = false)

    @Test
    fun dash_fault_1024x600() = snapDash(paparazzi, fault = true)
}

class CarHuDashboard720SnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = CarHuDevices.HU_1280x720)

    @Test
    fun dash_live_1280x720() = snapDash(paparazzi, fault = false)
}

class CarHuUltrawideSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = CarHuDevices.HU_1920x720)

    @Test
    fun dash_live_1920x720() = snapDash(paparazzi, fault = false)
}

class CarHuBubbleSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = CarHuDevices.HU_1024x600)

    @Test
    fun bubble_collapsed_over_map() {
        val metrics = sampleMetrics()
        paparazzi.snapshot {
            FB2Theme {
                FloatingDashBubblePreview(
                    metrics = metrics,
                    index = 0,
                    expanded = false,
                    statusLine = "LIVE · ELM327",
                )
            }
        }
    }

    @Test
    fun bubble_expanded_critical_battery() {
        val metrics = sampleMetrics()
        val battIdx = metrics.indexOfFirst { it.label.contains("Battery", true) }.coerceAtLeast(0)
        paparazzi.snapshot {
            FB2Theme {
                FloatingDashBubblePreview(
                    metrics = metrics,
                    index = battIdx,
                    expanded = true,
                    statusLine = "LIVE · ELM327",
                )
            }
        }
    }

    @Test
    fun bubble_expanded_maf() {
        val metrics = sampleMetrics()
        val mafIdx = metrics.indexOfFirst { it.label.equals("MAF", true) }.coerceAtLeast(0)
        paparazzi.snapshot {
            FB2Theme {
                FloatingDashBubblePreview(
                    metrics = metrics,
                    index = mafIdx,
                    expanded = true,
                    statusLine = "LIVE · ELM327",
                )
            }
        }
    }
}

private fun snapDash(paparazzi: Paparazzi, fault: Boolean) {
    val speed = if (fault) 42.0 else 96.0
    val rpm = if (fault) 3200.0 else 2450.0
    val snapshot = if (fault) {
        VehicleSnapshot(
            rpm = rpm,
            speedKmh = speed,
            coolantC = 105.0,
            intakeC = 51.0,
            engineLoadPct = 88.0,
            throttlePct = 74.0,
            timingAdvance = -2.0,
            mafGps = 22.0,
            mapKpa = 92.0,
            stftPct = -13.0,
            batteryVolts = 12.5,
            fuelSystemStatus = "OPEN LOOP",
            gear = GearEstimator().estimate(speed, rpm),
            gearSource = GearSource.ESTIMATED,
            unsupportedPids = setOf(0x67, 0x46, 0x07),
        )
    } else {
        VehicleSnapshot(
            rpm = rpm,
            speedKmh = speed,
            coolantC = 85.0,
            intakeC = 34.0,
            engineLoadPct = 47.0,
            throttlePct = 31.0,
            timingAdvance = 14.0,
            mafGps = 12.4,
            mapKpa = 58.0,
            stftPct = 2.3,
            batteryVolts = 14.2,
            fuelSystemStatus = "CLOSED LOOP",
            gear = GearEstimator().estimate(speed, rpm),
            gearSource = GearSource.ESTIMATED,
            unsupportedPids = setOf(0x67, 0x46, 0x07),
        )
    }
    val state = DashboardUiState(
        snapshot = snapshot,
        connection = ConnectionState.CONNECTED,
        sourceName = "ELM327 (Bluetooth)",
        sourceIsLive = true,
        dtcCount = if (fault) 2 else 0,
    )
    paparazzi.snapshot {
        FB2Theme {
            DashboardScreen(
                state = state,
                dtcCount = state.dtcCount,
                health = HealthScore(
                    enginePct = if (fault) 48 else 94,
                    transmissionPct = null,
                    engineNotes = if (fault) listOf("Engine overheating") else listOf("OK"),
                    engineDataOk = true,
                    transmissionDataOk = false,
                ),
            )
        }
    }
}

private fun sampleMetrics(): List<FloatingDashMetrics.Metric> {
    val snap = VehicleSnapshot(
        rpm = 2450.0,
        speedKmh = 96.0,
        coolantC = 85.0,
        batteryVolts = 12.4, // warn/crit while running for color check
        mafGps = 3.8,
        throttlePct = 14.0,
        mapKpa = 32.0,
        stftPct = -1.5,
        fuelSystemStatus = "CLOSED LOOP",
        gear = 4,
        gearSource = GearSource.ESTIMATED,
        unsupportedPids = setOf(0x67, 0x46, 0x07),
    )
    val state = CarDashBuilder.build(
        snapshot = snap,
        thresholds = HealthThresholds.DEFAULT,
        extraPidIds = emptyList(),
        extraValues = emptyMap(),
        deepFoundValues = emptyMap(),
        catalog = StandardPidCatalog.all,
        connection = ConnectionState.CONNECTED,
        sourceIsLive = true,
        sourceName = "ELM327",
        logging = false,
        showEstimatedGear = true,
        dtcCount = 0,
        healthScore = HealthScore(
            enginePct = 94,
            engineNotes = emptyList(),
            engineDataOk = true,
            transmissionDataOk = false,
        ),
    )
    return FloatingDashMetrics.from(state)
}
