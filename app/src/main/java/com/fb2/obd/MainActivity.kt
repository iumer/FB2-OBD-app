package com.fb2.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.Elm327BluetoothSource
import com.fb2.obd.data.ObdLogger
import com.fb2.obd.data.SavedLogFile
import com.fb2.obd.obd.LiveSnapshotOverlay
import com.fb2.obd.ui.BtDeviceUi
import com.fb2.obd.ui.ConnectDialog
import com.fb2.obd.ui.CustomSensorsScreen
import com.fb2.obd.ui.DashboardScreen
import com.fb2.obd.ui.DebugLogScreen
import com.fb2.obd.ui.DeepSearchDialogs
import com.fb2.obd.ui.DiagnosticsDepthScreen
import com.fb2.obd.ui.DiagnosticsHubScreen
import com.fb2.obd.ui.DiagnosticsNav
import com.fb2.obd.ui.FaultsScreen
import com.fb2.obd.ui.HiddenHondaMenuScreen
import com.fb2.obd.ui.MaintenanceScreen
import com.fb2.obd.ui.SettingsNav
import com.fb2.obd.ui.SettingsScreen
import com.fb2.obd.ui.ValueLogScreen
import com.fb2.obd.ui.VehicleInfoScreen
import com.fb2.obd.ui.theme.FB2Theme
import java.io.File
import kotlinx.coroutines.delay

private enum class Screen {
    DASHBOARD, SETTINGS, DIAG_HUB, FAULTS, DEBUG_LOG, VALUE_LOG,
    CUSTOM, VEHICLE, DEEP_DIAG, MAINTENANCE, HONDA,
}

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            FB2Theme {
                val state by viewModel.uiState.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val faults by viewModel.faults.collectAsState()
                val performance by viewModel.performance.collectAsState()
                val trip by viewModel.trip.collectAsState()
                val custom by viewModel.custom.collectAsState()
                val fuelValues by viewModel.fuelValues.collectAsState()
                val transValues by viewModel.transValues.collectAsState()
                val vehicleInfo by viewModel.vehicleInfo.collectAsState()
                val vehicleInfoLoading by viewModel.vehicleInfoLoading.collectAsState()
                val deepDiag by viewModel.deepDiag.collectAsState()
                val idleDiag by viewModel.idleDiag.collectAsState()
                val health by viewModel.health.collectAsState()
                val hondaScan by viewModel.hondaScan.collectAsState()
                val hondaScanning by viewModel.hondaScanning.collectAsState()
                val maintenance by viewModel.maintenance.collectAsState()
                val dashExtraPidIds by viewModel.dashExtraPidIds.collectAsState()
                val dashExtraValues by viewModel.dashExtraValues.collectAsState()
                val savedLogs by viewModel.savedLogs.collectAsState()
                val deepSearch by viewModel.deepSearch.collectAsState()
                val deepFoundValues by viewModel.deepFoundValues.collectAsState()
                val healthThresholds by viewModel.healthThresholds.collectAsState()

                var screen by remember { mutableStateOf(Screen.DASHBOARD) }
                // Lives above the screen switch so Settings scroll is kept when opening a sub-page.
                val settingsScrollState = rememberScrollState()
                var showConnect by remember { mutableStateOf(false) }
                var showExitConfirm by remember { mutableStateOf(false) }
                var devices by remember { mutableStateOf(emptyList<BtDeviceUi>()) }
                var ax by remember { mutableFloatStateOf(0f) }
                var ay by remember { mutableFloatStateOf(0f) }
                var az by remember { mutableFloatStateOf(9.81f) }

                var tick by remember { mutableIntStateOf(0) }
                LaunchedEffect(screen) {
                    while (screen == Screen.DEBUG_LOG || screen == Screen.VALUE_LOG) {
                        delay(1000L)
                        tick++
                    }
                }

                DisposableEffect(Unit) {
                    val sm = getSystemService(SENSOR_SERVICE) as SensorManager
                    val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            ax = event.values[0]
                            ay = event.values[1]
                            az = event.values[2]
                            viewModel.updatePhoneSensors(ax, ay, az)
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                    }
                    if (sensor != null) {
                        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                    }
                    onDispose { sm.unregisterListener(listener) }
                }

                val enableBtLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        devices = loadBondedDevices()
                        showConnect = true
                    } else {
                        toast("Bluetooth must be on to connect to the adapter")
                    }
                }

                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    if (result.values.all { it }) {
                        openConnect(
                            onNeedEnable = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                            onReady = { devices = it; showConnect = true },
                        )
                    } else {
                        toast("Bluetooth permission is required to connect to the ELM327")
                    }
                }

                BackHandler {
                    when (screen) {
                        Screen.DASHBOARD -> showExitConfirm = true
                        Screen.SETTINGS, Screen.DIAG_HUB, Screen.CUSTOM -> screen = Screen.DASHBOARD
                        Screen.DEBUG_LOG, Screen.VALUE_LOG -> screen = Screen.SETTINGS
                        Screen.FAULTS, Screen.DEEP_DIAG, Screen.VEHICLE, Screen.HONDA, Screen.MAINTENANCE ->
                            screen = Screen.DIAG_HUB
                    }
                }

                val settingsNav = SettingsNav(
                    onDebug = { screen = Screen.DEBUG_LOG },
                    onValues = {
                        viewModel.refreshSavedLogs()
                        screen = Screen.VALUE_LOG
                    },
                )

                val diagnosticsNav = DiagnosticsNav(
                    onFaults = { screen = Screen.FAULTS },
                    onDeepDiag = { screen = Screen.DEEP_DIAG },
                    onVehicle = {
                        viewModel.readVehicleInfo()
                        screen = Screen.VEHICLE
                    },
                    onHonda = { screen = Screen.HONDA },
                    onMaintenance = { screen = Screen.MAINTENANCE },
                )

                when (screen) {
                    Screen.DASHBOARD -> DashboardScreen(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        showEstimatedGear = settings.showEstimatedGear,
                        loggingActive = settings.valueLogging,
                        onConnectClick = {
                            val needed = requiredBtPermissions().filter {
                                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (needed.isEmpty()) {
                                openConnect(
                                    onNeedEnable = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                                    onReady = { devices = it; showConnect = true },
                                )
                            } else {
                                permLauncher.launch(needed.toTypedArray())
                            }
                        },
                        onSettingsClick = { screen = Screen.SETTINGS },
                        onDiagnosticsClick = { screen = Screen.DIAG_HUB },
                        onToggleLogging = {
                            if (settings.valueLogging) {
                                val saved = viewModel.stopValueLogging()
                                toast(
                                    if (saved != null) "Log saved: ${saved.fileName}"
                                    else "Logging stopped (empty session)",
                                )
                            } else {
                                viewModel.startValueLogging()
                                toast("Logging started")
                            }
                        },
                        catalog = viewModel.pidCatalog,
                        extraPidIds = dashExtraPidIds,
                        extraValues = dashExtraValues,
                        onSetExtraPid = viewModel::setDashExtraPid,
                        customValues = buildMap {
                            custom.selectedIds.forEach { id ->
                                val pid = viewModel.pidCatalog.find { it.id == id } ?: return@forEach
                                put(
                                    pid.label,
                                    custom.liveValues[id]
                                        ?: LiveSnapshotOverlay.formatLiveOrNs(
                                            pid,
                                            state.snapshot,
                                            custom.liveValues[pid.label],
                                        ),
                                )
                            }
                        },
                        fuelValues = fuelValues,
                        idleValues = idleDiag.values,
                        idleTips = idleDiag.tips,
                        transValues = transValues,
                        trip = trip,
                        performance = performance,
                        health = health,
                        dtcCount = state.dtcCount,
                        gForceAx = ax,
                        gForceAy = ay,
                        gForceAz = az,
                        onRefreshCustom = viewModel::probeCustomSelected,
                        onRefreshIdle = viewModel::refreshIdleDiagnostics,
                        onRefreshFuel = viewModel::refreshFuelPage,
                        onRefreshTrans = viewModel::refreshTransmission,
                        onManageCustom = { screen = Screen.CUSTOM },
                        onResetTrip = viewModel::resetTrip,
                        onSetFuelPrice = viewModel::setFuelPricePerLiter,
                        onResetPerformance = viewModel::resetPerformance,
                        onRefreshHealth = viewModel::recalcHealth,
                        deepFoundValues = deepFoundValues,
                        onDeepSearch = viewModel::requestDeepSearch,
                        healthThresholds = healthThresholds,
                        onThresholdFieldChange = viewModel::updateHealthThresholdField,
                        onResetThresholds = viewModel::resetHealthThresholds,
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onToggleEstimatedGear = viewModel::setShowEstimatedGear,
                        onToggleVoiceAlerts = viewModel::setVoiceAlerts,
                        nav = settingsNav,
                        onBack = { screen = Screen.DASHBOARD },
                        modifier = Modifier.fillMaxSize(),
                        scrollState = settingsScrollState,
                    )

                    Screen.DIAG_HUB -> DiagnosticsHubScreen(
                        nav = diagnosticsNav,
                        onBack = { screen = Screen.DASHBOARD },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.CUSTOM -> CustomSensorsScreen(
                        catalog = viewModel.pidCatalog,
                        selectedIds = custom.selectedIds,
                        liveValues = custom.liveValues,
                        filter = custom.filter,
                        probing = custom.probing,
                        onFilter = viewModel::setCustomFilter,
                        onToggle = viewModel::toggleCustomPid,
                        onProbeSelected = viewModel::probeCustomSelected,
                        onBack = { screen = Screen.DASHBOARD },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.VEHICLE -> VehicleInfoScreen(
                        info = vehicleInfo,
                        loading = vehicleInfoLoading,
                        onRefresh = viewModel::readVehicleInfo,
                        onBack = { screen = Screen.DIAG_HUB },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.DEEP_DIAG -> DiagnosticsDepthScreen(
                        readiness = deepDiag.readiness,
                        freeze = deepDiag.freeze,
                        o2 = deepDiag.o2,
                        mode06 = deepDiag.mode06,
                        loading = deepDiag.loading,
                        onScan = viewModel::scanDeepDiagnostics,
                        onBack = { screen = Screen.DIAG_HUB },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.MAINTENANCE -> MaintenanceScreen(
                        entries = maintenance,
                        onBack = { screen = Screen.DIAG_HUB },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.HONDA -> HiddenHondaMenuScreen(
                        results = hondaScan,
                        loading = hondaScanning,
                        onScan = viewModel::scanHondaModules,
                        onBack = { screen = Screen.DIAG_HUB },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.FAULTS -> FaultsScreen(
                        state = faults,
                        onRead = { viewModel.readFaults() },
                        onClear = { viewModel.clearFaults() },
                        onBack = { screen = Screen.DIAG_HUB },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.DEBUG_LOG -> {
                        val lines = remember(tick) { ObdLogger.debugLines() }
                        DebugLogScreen(
                            lines = lines,
                            onShare = { shareText("FB2 Diag debug log", ObdLogger.debugText()) },
                            onClear = { ObdLogger.clearDebug(); tick++ },
                            onBack = { screen = Screen.SETTINGS },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Screen.VALUE_LOG -> {
                        val rows = remember(tick) { ObdLogger.valueRows() }
                        ValueLogScreen(
                            rows = rows,
                            onShare = {
                                shareCsvContent("FB2-Diag-current.csv", ObdLogger.valuesCsv())
                            },
                            onClear = { ObdLogger.clearValues(); tick++ },
                            onBack = { screen = Screen.SETTINGS },
                            modifier = Modifier.fillMaxSize(),
                            savedFiles = savedLogs,
                            loggingActive = settings.valueLogging,
                            onShareFile = { file ->
                                shareSavedLogFile(file)
                            },
                            onDeleteFile = { file ->
                                viewModel.deleteSavedLog(file.fileName)
                                toast("Deleted ${file.fileName}")
                                tick++
                            },
                        )
                    }
                }

                if (showConnect) {
                    ConnectDialog(
                        devices = devices,
                        onPickDevice = { connectTo(it); showConnect = false },
                        onPickDemo = { viewModel.useSource(DemoObdSource()); showConnect = false },
                        onDismiss = { showConnect = false },
                    )
                }

                if (showExitConfirm) {
                    AlertDialog(
                        onDismissRequest = { showExitConfirm = false },
                        title = {
                            Text("Exit FB2 Diag?", color = TextPrimary, fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Text(
                                text = "Disconnect the ELM adapter and close the app?",
                                color = TextPrimary,
                                fontSize = 14.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExitConfirm = false
                                    viewModel.disconnect()
                                    finish()
                                },
                            ) {
                                Text("Exit & disconnect", color = Accent, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitConfirm = false }) {
                                Text("Stay", color = TextPrimary)
                            }
                        },
                        containerColor = Background,
                    )
                }

                DeepSearchDialogs(
                    state = deepSearch,
                    onConfirm = viewModel::confirmDeepSearch,
                    onDismiss = viewModel::cancelDeepSearch,
                )
            }
        }
    }

    private fun shareText(subject: String, text: String) {
        try {
            val safeName = subject.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40)
            val dir = File(cacheDir, "share").also { it.mkdirs() }
            val out = File(dir, "${safeName}.txt")
            out.writeText(text.ifBlank { "(empty log)" })
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                out,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(contentResolver, subject, uri)
            }
            startActivity(Intent.createChooser(intent, subject))
        } catch (e: Exception) {
            toast("Share failed: ${e.message ?: "no app"}")
        }
    }

    /** Share a saved session CSV as a real file (WhatsApp / Drive / email). */
    private fun shareSavedLogFile(file: SavedLogFile) {
        val disk = File(file.absolutePath)
        if (!disk.isFile) {
            toast("Could not find ${file.fileName}")
            return
        }
        shareFileUri(disk, file.fileName)
    }

    /**
     * Write [csv] to cache and open the system share sheet.
     * Avoids Intent size limits that break EXTRA_TEXT for larger logs.
     */
    private fun shareCsvContent(fileName: String, csv: String) {
        try {
            val dir = File(cacheDir, "share").also { it.mkdirs() }
            val out = File(dir, fileName)
            out.writeText(csv)
            shareFileUri(out, fileName)
        } catch (e: Exception) {
            toast("Share failed: ${e.message ?: "write error"}")
        }
    }

    private fun shareFileUri(file: File, displayName: String) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, displayName)
                putExtra(Intent.EXTRA_TEXT, "FB2 Diag log: $displayName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(contentResolver, displayName, uri)
            }
            startActivity(Intent.createChooser(intent, "Share $displayName"))
        } catch (e: Exception) {
            toast("Share failed: ${e.message ?: "no app"}")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun requiredBtPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            emptyList()
        }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun openConnect(onNeedEnable: () -> Unit, onReady: (List<BtDeviceUi>) -> Unit) {
        val adapter = bluetoothAdapter()
        if (adapter != null && !adapter.isEnabled) {
            onNeedEnable()
        } else {
            onReady(loadBondedDevices())
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadBondedDevices(): List<BtDeviceUi> {
        val adapter = bluetoothAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices
            ?.map { BtDeviceUi(runCatching { it.name }.getOrNull().orEmpty(), it.address) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(device: BtDeviceUi) {
        val adapter = bluetoothAdapter() ?: return
        val remote = adapter.getRemoteDevice(device.address)
        viewModel.useSource(Elm327BluetoothSource(remote))
    }
}
