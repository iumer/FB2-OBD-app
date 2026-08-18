package com.fb2.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.fb2.obd.ui.theme.Accent
import com.fb2.obd.ui.theme.Background
import com.fb2.obd.ui.theme.TextPrimary
import com.fb2.obd.data.AppUpdateManager
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.Elm327BluetoothSource
import com.fb2.obd.data.LogExportHelper
import com.fb2.obd.data.ObdLogger
import com.fb2.obd.data.SavedLogFile
import com.fb2.obd.obd.LiveSnapshotOverlay
import com.fb2.obd.obd.ConnectActionPolicy
import com.fb2.obd.service.FloatingDashOverlayService
import com.fb2.obd.ui.BtDeviceUi
import com.fb2.obd.ui.ConnectDialog
import com.fb2.obd.ui.CustomSensorsScreen
import com.fb2.obd.ui.DashboardScreen
import com.fb2.obd.ui.DebugLogScreen
import com.fb2.obd.ui.DeepSearchDialogs
import com.fb2.obd.ui.DiagnosticsDepthScreen
import com.fb2.obd.ui.AiAnalyzeScreen
import java.util.concurrent.atomic.AtomicBoolean
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
import com.fb2.obd.ui.theme.ThemePalette
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Screen {
    DASHBOARD, SETTINGS, DIAG_HUB, FAULTS, DEBUG_LOG, VALUE_LOG,
    CUSTOM, VEHICLE, DEEP_DIAG, MAINTENANCE, HONDA, AI_ANALYZE,
}

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by lazy {
        ViewModelProvider(application as Fb2App)[DashboardViewModel::class.java]
    }
    private var batteryPrompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val settings by viewModel.settings.collectAsState()
            FB2Theme(palette = ThemePalette.of(settings.dashTheme)) {
                val state by viewModel.uiState.collectAsState()
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
                val dashTileOverrides by viewModel.dashTileOverrides.collectAsState()
                val savedLogs by viewModel.savedLogs.collectAsState()
                val uploadStatus by viewModel.uploadStatus.collectAsState()
                val deepSearch by viewModel.deepSearch.collectAsState()
                val pickerScan by viewModel.pickerScan.collectAsState()
                val deepFoundValues by viewModel.deepFoundValues.collectAsState()
                val healthThresholds by viewModel.healthThresholds.collectAsState()

                var batteryUnrestricted by remember { mutableStateOf(isBatteryUnrestricted()) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            batteryUnrestricted = isBatteryUnrestricted()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val appUpdateManager = remember { AppUpdateManager(applicationContext) }
                val appUpdateUi by appUpdateManager.state.collectAsState()
                val updateScope = rememberCoroutineScope()
                val updateBusy = appUpdateUi is AppUpdateManager.UiState.Checking ||
                    appUpdateUi is AppUpdateManager.UiState.Downloading
                val updateStatusText = when (val s = appUpdateUi) {
                    is AppUpdateManager.UiState.Idle -> ""
                    is AppUpdateManager.UiState.Checking -> "Checking for update…"
                    is AppUpdateManager.UiState.UpToDate -> s.message
                    is AppUpdateManager.UiState.Available ->
                        if (s.newer.size == 1) {
                            "Update available: v${s.newer.first().versionName}"
                        } else {
                            "${s.newer.size} updates available (v${s.newer.first().versionName} – v${s.newer.last().versionName})"
                        }
                    is AppUpdateManager.UiState.Downloading ->
                        "Downloading v${s.remote.versionName}… ${s.percent}%"
                    is AppUpdateManager.UiState.ReadyToInstall ->
                        "Ready to install v${s.remote.versionName}"
                    is AppUpdateManager.UiState.Error -> s.message
                }
                val availableUpdates = when (val s = appUpdateUi) {
                    is AppUpdateManager.UiState.Available -> s.newer
                    is AppUpdateManager.UiState.Downloading -> s.newer
                    is AppUpdateManager.UiState.ReadyToInstall -> s.newer
                    else -> emptyList()
                }
                val downloadingName = (appUpdateUi as? AppUpdateManager.UiState.Downloading)?.remote?.versionName
                val downloadPercent = (appUpdateUi as? AppUpdateManager.UiState.Downloading)?.percent ?: 0
                val readyToInstallName = (appUpdateUi as? AppUpdateManager.UiState.ReadyToInstall)?.remote?.versionName

                var screen by remember { mutableStateOf(Screen.DASHBOARD) }
                // Lives above the screen switch so Settings scroll is kept when opening a sub-page.
                val settingsScrollState = rememberScrollState()
                var showConnect by remember { mutableStateOf(false) }
                var showExitConfirm by remember { mutableStateOf(false) }
                var devices by remember { mutableStateOf(emptyList<BtDeviceUi>()) }
                var ax by remember { mutableFloatStateOf(0f) }
                var ay by remember { mutableFloatStateOf(0f) }
                var az by remember { mutableFloatStateOf(9.81f) }
                // Only push accel into Compose ~2 Hz — SENSOR_DELAY_UI was recomposing
                // the whole Dash ~16 Hz even when G-force page is not visible.
                val lastAccelUiMs = remember { java.util.concurrent.atomic.AtomicLong(0L) }

                var tick by remember { mutableIntStateOf(0) }
                LaunchedEffect(screen) {
                    while (screen == Screen.DEBUG_LOG || screen == Screen.VALUE_LOG) {
                        delay(1000L)
                        tick++
                    }
                }
                LaunchedEffect(state.connection, state.sourceIsLive) {
                    if (state.sourceIsLive && state.connection == ConnectionState.CONNECTED) {
                        maybeRequestUnrestrictedBattery()
                    }
                }

                DisposableEffect(Unit) {
                    val sm = getSystemService(SENSOR_SERVICE) as SensorManager
                    val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            val x = event.values[0]
                            val y = event.values[1]
                            val z = event.values[2]
                            viewModel.updatePhoneSensors(x, y, z)
                            val now = android.os.SystemClock.elapsedRealtime()
                            val prev = lastAccelUiMs.get()
                            if (now - prev >= 500L && lastAccelUiMs.compareAndSet(prev, now)) {
                                ax = x
                                ay = y
                                az = z
                            }
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                    }
                    if (sensor != null) {
                        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
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
                        toast("Bluetooth and notification permissions are required for ELM327 monitoring")
                    }
                }

                val overlayPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    if (FloatingDashOverlayService.isOverlayAllowed(this@MainActivity)) {
                        startFloatingDashBubble()
                    } else {
                        toast("Overlay permission needed for the floating Dash bubble")
                    }
                }

                fun requestMinimizeToBubble() {
                    if (FloatingDashOverlayService.isOverlayAllowed(this@MainActivity)) {
                        startFloatingDashBubble()
                    } else {
                        toast("Allow “Display over other apps” for FB2 Diag, then tap MIN again")
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        )
                        overlayPermLauncher.launch(intent)
                    }
                }

                BackHandler {
                    when (screen) {
                        Screen.DASHBOARD -> showExitConfirm = true
                        Screen.SETTINGS, Screen.DIAG_HUB, Screen.CUSTOM -> screen = Screen.DASHBOARD
                        Screen.DEBUG_LOG, Screen.VALUE_LOG -> screen = Screen.SETTINGS
                        Screen.FAULTS, Screen.DEEP_DIAG, Screen.VEHICLE, Screen.HONDA,
                        Screen.MAINTENANCE, Screen.AI_ANALYZE ->
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
                    onAiAnalyze = {
                        viewModel.refreshSavedLogs()
                        screen = Screen.AI_ANALYZE
                    },
                )

                when (screen) {
                    Screen.DASHBOARD -> DashboardScreen(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        showEstimatedGear = settings.showEstimatedGear,
                        dashTheme = settings.dashTheme,
                        loggingActive = settings.valueLogging,
                        networkOnline = uploadStatus.online,
                        pageTitles = viewModel.dashPageTitles,
                        profileBadge = settings.vehicleProfile.badge,
                        onConnectClick = {
                            if (ConnectActionPolicy.isDisconnectAction(
                                    state.connection,
                                    state.sourceIsLive,
                                    state.reconnecting,
                                )
                            ) {
                                viewModel.disconnect()
                            } else {
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
                                toast(
                                    if (!state.sourceIsLive) {
                                        "Logging started (DEMO — simulated, not live)"
                                    } else {
                                        "Logging started"
                                    },
                                )
                            }
                        },
                        onMinimizeClick = { requestMinimizeToBubble() },
                        catalog = viewModel.pidCatalog,
                        extraPidIds = dashExtraPidIds,
                        extraValues = dashExtraValues,
                        tileOverrides = dashTileOverrides,
                        pickerProbe = pickerScan.results,
                        pickerScanning = pickerScan.running,
                        onPickerOpen = viewModel::startSensorPickerScan,
                        onPickerClose = viewModel::stopSensorPickerScan,
                        onSetExtraPid = viewModel::setDashExtraPid,
                        onSetTileOverride = viewModel::setDashTileOverride,
                        onClearTileOverride = viewModel::clearDashTileOverride,
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
                        onLatchHealth = viewModel::latchHealth,
                    )

                    Screen.SETTINGS -> {
                        SettingsScreen(
                            settings = settings,
                            onVehicleProfileChange = viewModel::setVehicleProfile,
                            onDashThemeChange = viewModel::setDashTheme,
                            onToggleEstimatedGear = viewModel::setShowEstimatedGear,
                            onToggleAllowDemo = viewModel::setAllowDemo,
                            onToggleVoiceAlerts = viewModel::setVoiceAlerts,
                            onToggleDuckMedia = viewModel::setDuckMediaDuringAlerts,
                            onCheckSoundAlert = {
                                viewModel.testSoundAlert()
                                toast("Playing test alarm — CarPlay volume should stay up afterward")
                            },
                            onKeepAliveBattery = {
                                maybeRequestUnrestrictedBattery(force = true)
                            },
                            batteryUnrestricted = batteryUnrestricted,
                            uploadStatus = uploadStatus,
                            githubToken = viewModel.githubUploadToken(),
                            onGithubTokenChange = viewModel::setGithubUploadToken,
                            onUploadLogs = {
                                viewModel.uploadSavedLogs()
                                toast("Uploading saved logs…")
                            },
                            openAiApiKey = viewModel.openAiApiKey(),
                            onOpenAiApiKeyChange = viewModel::setOpenAiApiKey,
                            appVersionLabel = appUpdateManager.localLabel,
                            updateStatusText = updateStatusText,
                            updateBusy = updateBusy,
                            availableUpdates = availableUpdates,
                            downloadingName = downloadingName,
                            downloadPercent = downloadPercent,
                            readyToInstallName = readyToInstallName,
                            onCheckForUpdate = {
                                updateScope.launch { appUpdateManager.checkForUpdate() }
                            },
                            onDownloadVersion = { remote ->
                                updateScope.launch { appUpdateManager.downloadUpdate(remote) }
                            },
                            onInstallUpdate = {
                                val ready = appUpdateUi as? AppUpdateManager.UiState.ReadyToInstall
                                if (ready != null) {
                                    appUpdateManager.installApk(this@MainActivity, ready.apk)
                                }
                            },
                            nav = settingsNav,
                            onBack = { screen = Screen.DASHBOARD },
                            modifier = Modifier.fillMaxSize(),
                            scrollState = settingsScrollState,
                            demoRunning = state.connection == ConnectionState.CONNECTED &&
                                !state.sourceIsLive,
                        )
                    }

                    Screen.DIAG_HUB -> DiagnosticsHubScreen(
                        nav = diagnosticsNav,
                        onBack = { screen = Screen.DASHBOARD },
                        modifier = Modifier.fillMaxSize(),
                        showHondaModules = viewModel.showHondaModules,
                        blurb = com.fb2.obd.obd.VehicleProfileConfig.diagHubBlurb(settings.vehicleProfile),
                    )

                    Screen.AI_ANALYZE -> {
                        val aiState by viewModel.aiAnalyze.collectAsState()
                        val logs by viewModel.savedLogs.collectAsState()
                        AiAnalyzeScreen(
                            state = aiState,
                            savedLogs = logs,
                            hasApiKey = viewModel.openAiApiKey().isNotBlank(),
                            onModeLive = viewModel::setAiAnalyzeModeLive,
                            onWindowMinutes = viewModel::setAiAnalyzeWindowMinutes,
                            onSelectLog = viewModel::setAiAnalyzeSelectedLog,
                            onAnalyze = viewModel::runAiAnalysis,
                            onClearReport = viewModel::clearAiAnalyzeResult,
                            onRefreshLogs = viewModel::refreshSavedLogs,
                            onOpenSettings = { screen = Screen.SETTINGS },
                            onBack = { screen = Screen.DIAG_HUB },
                            modifier = Modifier.fillMaxSize(),
                            liveSourceIsDemo = !state.sourceIsLive,
                        )
                    }

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
                        val loggingDemo = settings.valueLogging && !state.sourceIsLive
                        ValueLogScreen(
                            rows = rows,
                            onShare = {
                                shareCsvContent(
                                    if (loggingDemo || !state.sourceIsLive) {
                                        "FB2-Diag-current-demo.csv"
                                    } else {
                                        "FB2-Diag-current.csv"
                                    },
                                    ObdLogger.valuesCsv(isDemo = loggingDemo || !state.sourceIsLive),
                                )
                            },
                            onClear = { ObdLogger.clearValues(); tick++ },
                            onBack = { screen = Screen.SETTINGS },
                            modifier = Modifier.fillMaxSize(),
                            savedFiles = savedLogs,
                            loggingActive = settings.valueLogging,
                            sourceIsDemo = !state.sourceIsLive,
                            onShareFile = { file ->
                                shareSavedLogFile(file)
                            },
                            onDeleteFile = { file ->
                                viewModel.deleteSavedLog(file.fileName)
                                toast("Deleted ${file.fileName}")
                                tick++
                            },
                            uploadEnabled = !uploadStatus.uploading,
                            onUpload = {
                                viewModel.uploadSavedLogs()
                                toast(if (uploadStatus.online) "Uploading…" else "No internet — will retry when online")
                            },
                            uploadStatusLine = buildString {
                                append(if (uploadStatus.online) "Online" else "Offline")
                                append(" · ${uploadStatus.pendingCount} pending · ${uploadStatus.syncedCount} synced")
                                if (uploadStatus.lastMessage.isNotBlank()) {
                                    append(" · ${uploadStatus.lastMessage}")
                                }
                            },
                        )
                    }
                }

                if (showConnect) {
                    ConnectDialog(
                        devices = devices,
                        onPickDevice = { connectTo(it); showConnect = false },
                        onPickDemo = {
                            viewModel.setAllowDemo(true)
                            viewModel.useSource(DemoObdSource())
                            showConnect = false
                        },
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
                                text = "Disconnect the ELM adapter, close the floating bubble, and quit the app?",
                                color = TextPrimary,
                                fontSize = 14.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExitConfirm = false
                                    // Full quit: tear down overlay first so the bubble cannot
                                    // linger after finish() (service is independent of the Activity).
                                    FloatingDashOverlayService.stop(this@MainActivity)
                                    viewModel.disconnect()
                                    finishAndRemoveTask()
                                },
                            ) {
                                Text("Exit & disconnect", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitConfirm = false }) {
                                Text("Stay", color = TextPrimary)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.background,
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
            val body = text.ifBlank { "(empty log)" }
            out.writeText(body)
            shareOrExportFile(out, "${safeName}.txt", "text/plain", subject)
        } catch (e: Exception) {
            toast("Export failed: ${e.message ?: "error"}")
        }
    }

    /** Share a saved session CSV as a real file (WhatsApp / Drive / email). */
    private fun shareSavedLogFile(file: SavedLogFile) {
        val disk = File(file.absolutePath)
        if (!disk.isFile) {
            toast("Could not find ${file.fileName}")
            return
        }
        shareOrExportFile(disk, file.fileName, "text/csv", file.fileName)
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
            shareOrExportFile(out, fileName, "text/csv", fileName)
        } catch (e: Exception) {
            toast("Export failed: ${e.message ?: "write error"}")
        }
    }

    /**
     * Always save into Downloads/FB2-Diag (file manager). Never open a bare
     * Bluetooth-only share sheet — that just starts BT search on car HUs.
     * Optional "Share…" only appears when a useful non-BT app exists.
     */
    private fun shareOrExportFile(
        file: File,
        displayName: String,
        mime: String,
        subject: String = displayName,
    ) {
        exportFileToDownloads(file, displayName, mime, subject)
    }

    private fun exportFileToDownloads(
        file: File,
        displayName: String,
        mime: String,
        subject: String,
    ) {
        try {
            val result = LogExportHelper.exportFile(this, file, displayName, mime)
            val clip = result.absolutePath ?: result.displayPath
            LogExportHelper.copyToClipboard(this, "FB2 log path", clip)

            var shareIntent: Intent? = null
            runCatching {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, "FB2 Diag log: $displayName")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newUri(contentResolver, displayName, uri)
                }
                if (LogExportHelper.hasUsefulShareTargets(this, intent)) {
                    packageManager.queryIntentActivities(intent, 0).forEach { ri ->
                        val pkg = ri.activityInfo.packageName
                        if (LogExportHelper.isUsefulSharePackage(pkg)) {
                            grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    shareIntent = intent
                }
            }

            val builder = android.app.AlertDialog.Builder(this)
                .setTitle("Log saved")
                .setMessage(
                    "Saved for the file manager:\n\n${result.displayPath}\n\n" +
                        "Open Files / Downloads → FB2-Diag on this unit, or pull via USB.\n" +
                        "Path copied to clipboard.",
                )
                .setPositiveButton("OK", null)
                .setNeutralButton("Open file") { _, _ ->
                    val opened = LogExportHelper.openInFileManager(this, result, mime)
                    if (!opened) toast("No file manager found — check Downloads/FB2-Diag")
                }
            if (shareIntent != null) {
                val toShare = shareIntent!!
                builder.setNegativeButton("Share…") { _, _ ->
                    // Strip Bluetooth / Nearby from the chooser so HU never
                    // lands on a BT search sheet when the user taps Share….
                    @Suppress("DEPRECATION")
                    val exclude = packageManager.queryIntentActivities(toShare, 0)
                        .filter {
                            !LogExportHelper.isUsefulSharePackage(
                                it.activityInfo?.packageName.orEmpty(),
                            )
                        }
                        .map {
                            android.content.ComponentName(
                                it.activityInfo.packageName,
                                it.activityInfo.name,
                            )
                        }
                        .toTypedArray()
                    val chooser = Intent.createChooser(toShare, "Share $displayName").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (exclude.isNotEmpty() &&
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
                        ) {
                            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, exclude)
                        }
                    }
                    startActivity(chooser)
                }
            }
            builder.show()
            toast("Saved to Downloads/FB2-Diag")
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Log exported: ${result.displayPath}")
        } catch (e: Exception) {
            toast("Save failed: ${e.message ?: "error"}")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** Start floating Dash bubble, wait until it is attached, then background. */
    private fun startFloatingDashBubble() {
        if (!FloatingDashOverlayService.isOverlayAllowed(this)) {
            toast("Allow “Display over other apps” for FB2 Diag, then tap MIN again")
            return
        }
        val done = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        lateinit var receiver: BroadcastReceiver
        fun finishMinimize() {
            if (!done.compareAndSet(false, true)) return
            runCatching { unregisterReceiver(receiver) }
            mainHandler.removeCallbacksAndMessages(null)
            moveTaskToBack(true)
            toast("Floating Dash on — look for the circle (notification keeps it alive)")
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == FloatingDashOverlayService.ACTION_READY) {
                    finishMinimize()
                }
            }
        }
        try {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(FloatingDashOverlayService.ACTION_READY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            FloatingDashOverlayService.startOverlay(this)
            // Fallback if READY is missed (service already running / OEM quirk).
            mainHandler.postDelayed({ finishMinimize() }, 900L)
        } catch (e: Exception) {
            runCatching { unregisterReceiver(receiver) }
            toast("Bubble failed: ${e.message ?: "overlay error"}")
        }
    }

    private fun requiredBtPermissions(): List<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_CONNECT
            perms += Manifest.permission.BLUETOOTH_SCAN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Needed for the ELM connected-device foreground notification.
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms
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

    private fun isBatteryUnrestricted(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    @SuppressLint("BatteryLife")
    private fun maybeRequestUnrestrictedBattery(force: Boolean = false) {
        if (batteryPrompted && !force) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        batteryPrompted = true
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                },
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(device: BtDeviceUi) {
        val adapter = bluetoothAdapter() ?: return
        val remote = adapter.getRemoteDevice(device.address)
        viewModel.useSource(Elm327BluetoothSource(remote))
    }
}
