package com.fb2.obd

import app.cash.paparazzi.Paparazzi
import com.fb2.obd.data.ObdLogger
import com.fb2.obd.obd.Dtc
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.perf.AccelResult
import com.fb2.obd.ui.DebugLogScreen
import com.fb2.obd.ui.FaultsScreen
import com.fb2.obd.ui.PerformanceScreen
import com.fb2.obd.ui.SettingsScreen
import com.fb2.obd.ui.ValueLogScreen
import com.fb2.obd.ui.theme.FB2Theme
import org.junit.Rule
import org.junit.Test

class ScreensSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = CarHuDevices.HU_1024x600)

    @Test
    fun settings_screen() {
        paparazzi.snapshot {
            FB2Theme {
                SettingsScreen(
                    settings = SettingsState(showEstimatedGear = true, voiceAlerts = true),
                    onToggleEstimatedGear = {},
                    onToggleVoiceAlerts = {},
                    onCheckSoundAlert = {},
                    appVersionLabel = "v0.1.21 (21)",
                    updateStatusText = "",
                    updateActionLabel = "CHECK",
                    nav = com.fb2.obd.ui.SettingsNav(),
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun debugLog_screen() {
        val t = 1_700_000_000_000L
        val lines = listOf(
            ObdLogger.DebugLine(t, ObdLogger.Dir.INFO, "Connecting to 00:1D:A5:68:98:8B"),
            ObdLogger.DebugLine(t, ObdLogger.Dir.TX, "ATZ"),
            ObdLogger.DebugLine(t, ObdLogger.Dir.RX, "ELM327 v1.5"),
            ObdLogger.DebugLine(t, ObdLogger.Dir.INFO, "Supported PIDs: 04 05 06 0B 0C 0D 0E 0F 10 11 42"),
            ObdLogger.DebugLine(t, ObdLogger.Dir.TX, "010C"),
            ObdLogger.DebugLine(t, ObdLogger.Dir.RX, "41 0C 0B 20"),
            ObdLogger.DebugLine(t, ObdLogger.Dir.TX, "0167"),
            ObdLogger.DebugLine(t, ObdLogger.Dir.RX, "NO DATA"),
        )
        paparazzi.snapshot {
            FB2Theme {
                DebugLogScreen(lines = lines, onShare = {}, onClear = {}, onBack = {})
            }
        }
    }

    @Test
    fun valueLog_screen() {
        val t = 1_700_000_000_000L
        val rows = listOf(
            ObdLogger.ValueRow(t, VehicleSnapshot(rpm = 712.0, speedKmh = 0.0, coolantC = 86.0, batteryVolts = 13.0, gearSource = GearSource.NONE)),
            ObdLogger.ValueRow(t + 250, VehicleSnapshot(rpm = 1650.0, speedKmh = 28.0, coolantC = 88.0, batteryVolts = 14.1, gear = 2, gearSource = GearSource.ESTIMATED)),
            ObdLogger.ValueRow(t + 500, VehicleSnapshot(rpm = 2100.0, speedKmh = 52.0, coolantC = 90.0, batteryVolts = 14.2, gear = 3, gearSource = GearSource.ESTIMATED)),
        )
        paparazzi.snapshot {
            FB2Theme {
                ValueLogScreen(rows = rows, onShare = {}, onClear = {}, onBack = {})
            }
        }
    }

    @Test
    fun faults_screen() {
        val state = FaultsState(
            hasRead = true,
            stored = listOf(
                Dtc("P0133", "O2 sensor slow response (B1S1)"),
                Dtc("P0420", "Catalyst efficiency below threshold (Bank 1)"),
            ),
            pending = listOf(Dtc("P0300", "Random/multiple cylinder misfire")),
        )
        paparazzi.snapshot {
            FB2Theme {
                FaultsScreen(state = state, onRead = {}, onClear = {}, onBack = {})
            }
        }
    }

    @Test
    fun performance_screen() {
        val state = PerformanceState(
            current = AccelResult(
                zeroTo100Kmh = 10.42,
                zeroTo60Mph = 9.15,
                sixtyTo100Kmh = 4.80,
                quarterMileSec = 17.6,
                quarterMileTrapKmh = 130.0,
            ),
            best = AccelResult(zeroTo100Kmh = 10.42),
            currentSpeedKmh = 48.0,
        )
        paparazzi.snapshot {
            FB2Theme {
                PerformanceScreen(state = state, onReset = {}, onBack = {})
            }
        }
    }
}

