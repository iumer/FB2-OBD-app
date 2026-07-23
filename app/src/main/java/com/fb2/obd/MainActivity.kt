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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.core.content.ContextCompat
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.Elm327BluetoothSource
import com.fb2.obd.data.ObdLogger
import com.fb2.obd.ui.BtDeviceUi
import com.fb2.obd.ui.ConnectDialog
import com.fb2.obd.ui.CustomSensorsScreen
import com.fb2.obd.ui.DashboardScreen
import com.fb2.obd.ui.DebugLogScreen
import com.fb2.obd.ui.DiagnosticsDepthScreen
import com.fb2.obd.ui.FaultsScreen
import com.fb2.obd.ui.FuelPageScreen
import com.fb2.obd.ui.GForceScreen
import com.fb2.obd.ui.HealthScoresScreen
import com.fb2.obd.ui.HiddenHondaMenuScreen
import com.fb2.obd.ui.MaintenanceScreen
import com.fb2.obd.ui.PerformanceScreen
import com.fb2.obd.ui.SettingsNav
import com.fb2.obd.ui.SettingsScreen
import com.fb2.obd.ui.TransmissionDashScreen
import com.fb2.obd.ui.TripScreen
import com.fb2.obd.ui.ValueLogScreen
import com.fb2.obd.ui.VehicleInfoScreen
import com.fb2.obd.ui.theme.FB2Theme
import kotlinx.coroutines.delay

private enum class Screen {
    DASHBOARD, SETTINGS, FAULTS, PERFORMANCE, DEBUG_LOG, VALUE_LOG,
    CUSTOM, FUEL, TRIP, VEHICLE, DEEP_DIAG, TRANS, HEALTH, MAINTENANCE, HONDA, GFORCE,
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
                val health by viewModel.health.collectAsState()
                val hondaScan by viewModel.hondaScan.collectAsState()
                val hondaScanning by viewModel.hondaScanning.collectAsState()
                val maintenance by viewModel.maintenance.collectAsState()

                var screen by remember { mutableStateOf(Screen.DASHBOARD) }
                var showConnect by remember { mutableStateOf(false) }
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

                val settingsNav = SettingsNav(
                    onFaults = { screen = Screen.FAULTS },
                    onPerformance = { screen = Screen.PERFORMANCE },
                    onCustom = { screen = Screen.CUSTOM },
                    onFuel = {
                        viewModel.refreshFuelPage()
                        screen = Screen.FUEL
                    },
                    onTrip = { screen = Screen.TRIP },
                    onVehicle = {
                        viewModel.readVehicleInfo()
                        screen = Screen.VEHICLE
                    },
                    onDeepDiag = { screen = Screen.DEEP_DIAG },
                    onTrans = {
                        viewModel.refreshTransmission()
                        screen = Screen.TRANS
                    },
                    onHealth = {
                        viewModel.recalcHealth()
                        screen = Screen.HEALTH
                    },
                    onMaintenance = { screen = Screen.MAINTENANCE },
                    onHonda = { screen = Screen.HONDA },
                    onGForce = { screen = Screen.GFORCE },
                    onDebug = { screen = Screen.DEBUG_LOG },
                    onValues = { screen = Screen.VALUE_LOG },
                )

                when (screen) {
                    Screen.DASHBOARD -> DashboardScreen(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        showEstimatedGear = settings.showEstimatedGear,
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
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onToggleValueLogging = viewModel::setValueLogging,
                        onToggleEstimatedGear = viewModel::setShowEstimatedGear,
                        nav = settingsNav,
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
                        onBack = { screen = Screen.SETTINGS },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.FUEL -> FuelPageScreen(
                        values = fuelValues,
                        onRefresh = viewModel::refreshFuelPage,
                        onBack = { screen = Screen.SETTINGS },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.TRIP -> TripScreen(
                        distanceKm = trip.distanceKm,
                        kmPerL = trip.kmPerLiter,
                        lPer100 = trip.litersPer100,
                        cost = trip.cost,
                        idleSec = trip.idleSeconds,
                        fuelPrice = trip.fuelPrice,
                        onReset = viewModel::resetTrip,
                        onBack = { screen = Screen.SETTINGS },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.VEHICLE -> VehicleInfoScreen(
                        info = vehicleInfo,
                        loading = vehicleInfoLoading,
                        onRefresh = viewModel::readVehicleInfo,
                        onBack = { screen = Screen.SETTINGS },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.DEEP_DIAG -> DiagnosticsDepthScreen(
                        readiness = deepDiag.readiness,
                        freeze = deepDiag.freeze,
                        o2 = deepDiag.o2,
                        mode06 = deepDiag.mode06,
                        loading = deepDiag.loading,
                        onScan = viewModel::scanDeepDiagnostics,
                        onBack = { screen = Screen.SETTINGS },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.TRANS -> TransmissionDashScreen(
                        values = transValues,
                        health = health,
                        onRefresh = viewModel::refreshTransmission,
                        onBack = { screen = Screen.SETTINGS },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.HEALTH -> HealthScoresScreen(
                        score = health,
                        onRefresh = viewModel::recalcHealth,
                        onBack = { screen = Screen.SETTINGS },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.MAINTENANCE -> MaintenanceScreen(
                        entries = maintenance,
                        onBack = { screen = Screen.SETTINGS },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.HONDA -> HiddenHondaMenuScreen(
                        results = hondaScan,
                        loading = hondaScanning,
                        onScan = viewModel::scanHondaModules,
                        onBack = { screen = Screen.SETTINGS },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.GFORCE -> GForceScreen(
                        ax = ax,
                        ay = ay,
                        az = az,
                        onBack = { screen = Screen.SETTINGS },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.FAULTS -> FaultsScreen(
                        state = faults,
                        onRead = { viewModel.readFaults() },
                        onClear = { viewModel.clearFaults() },
                        onBack = { screen = Screen.SETTINGS },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.PERFORMANCE -> PerformanceScreen(
                        state = performance,
                        onReset = { viewModel.resetPerformance() },
                        onBack = { screen = Screen.SETTINGS },
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
                            onShare = { shareText("FB2 Diag value log (CSV)", ObdLogger.valuesCsv()) },
                            onClear = { ObdLogger.clearValues(); tick++ },
                            onBack = { screen = Screen.SETTINGS },
                            modifier = Modifier.fillMaxSize(),
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
            }
        }
    }

    private fun shareText(subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, subject))
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
