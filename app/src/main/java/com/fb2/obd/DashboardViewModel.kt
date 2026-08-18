package com.fb2.obd

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fb2.obd.car.CarDashBuilder
import com.fb2.obd.car.VehicleLiveStore
import com.fb2.obd.data.AiAnalysisErrors
import com.fb2.obd.data.AiAnalysisStore
import com.fb2.obd.data.AiReportStore
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.data.DashExtraPidStore
import com.fb2.obd.data.DashTileOverrideStore
import com.fb2.obd.data.DemoFlavour
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.Elm327BluetoothSource
import com.fb2.obd.data.HealthThresholdStore
import com.fb2.obd.data.MaintenanceEntry
import com.fb2.obd.data.MaintenanceStore
import com.fb2.obd.data.LastElmStore
import com.fb2.obd.data.LogExportHelper
import com.fb2.obd.data.LogUploadManager
import com.fb2.obd.data.ObdLogger
import com.fb2.obd.data.ObdSource
import com.fb2.obd.data.OpenAiClient
import com.fb2.obd.data.SavedAiReport
import com.fb2.obd.data.SavedLogFile
import com.fb2.obd.data.SessionLogStore
import com.fb2.obd.data.DashThemeStore
import com.fb2.obd.data.VehicleProfileStore
import com.fb2.obd.obd.KeepAlivePolicy
import com.fb2.obd.obd.DashTheme
import com.fb2.obd.obd.ConnectActionPolicy
import com.fb2.obd.obd.DemoAllowPolicy
import com.fb2.obd.data.VoiceAlerter
import com.fb2.obd.obd.AiAnalysisPayloadBuilder
import com.fb2.obd.obd.ColdStartIdleCatalog
import com.fb2.obd.obd.DeepSearchReport
import com.fb2.obd.obd.DeepSensorSearch
import com.fb2.obd.obd.DiagnosticBrain
import com.fb2.obd.obd.VehicleProfile
import com.fb2.obd.obd.VehicleProfileConfig
import com.fb2.obd.obd.DiagnosticEventTracker
import com.fb2.obd.obd.Dtc
import com.fb2.obd.obd.FreezeFrame
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.HealthScoreCalculator
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.HondaPidCatalog
import com.fb2.obd.obd.LiveSnapshotOverlay
import com.fb2.obd.obd.MetricStatus
import com.fb2.obd.obd.Mode06Result
import com.fb2.obd.obd.ModuleScanResult
import com.fb2.obd.obd.O2TestResult
import com.fb2.obd.obd.PidCategory
import com.fb2.obd.obd.PidDefinition
import com.fb2.obd.obd.SnapshotFreshness
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.obd.isEffectivelyBlank
import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.ReadinessStatus
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.TripComputer
import com.fb2.obd.obd.VehicleInfo
import com.fb2.obd.obd.withField
import com.fb2.obd.perf.AccelResult
import com.fb2.obd.perf.AccelerationTimer
import com.fb2.obd.service.ObdMonitorForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** UI state for the dashboard screen. */
data class DashboardUiState(
    val snapshot: VehicleSnapshot = VehicleSnapshot.EMPTY,
    /** EMA-smoothed snapshot used for health/voice decisions (UI still shows [snapshot]). */
    val decisionSnapshot: VehicleSnapshot = VehicleSnapshot.EMPTY,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val sourceName: String = "",
    val sourceIsLive: Boolean = false,
    /** MIL / readiness DTC count for the Dash tile (null = not read yet). */
    val dtcCount: Int? = null,
    /** True while ELM RFCOMM is retrying after a drop — Dash keeps last-good values. */
    val reconnecting: Boolean = false,
    /** Last connection failure reason for the ERR chip / toast. */
    val lastError: String? = null,
)

/** User-adjustable settings. */
data class SettingsState(
    val valueLogging: Boolean = false,
    val showEstimatedGear: Boolean = true,
    val fuelPricePerLiter: Double = 280.0,
    val voiceAlerts: Boolean = true,
    /**
     * When true, alerts request Android audio focus and briefly duck media.
     * Default false — safe when CarPlay/Z-Link is connected (Settings shows the
     * inverse as “CarPlay / Android Auto connected” = Yes).
     */
    val duckMediaDuringAlerts: Boolean = false,
    /**
     * When true, simulated Demo may run without an ELM (launch + Connect sheet).
     * When false, Dash shows disconnected / `--` instead of fake numbers.
     */
    val allowDemo: Boolean = true,
    val vehicleProfile: VehicleProfile = VehicleProfile.DEFAULT,
    /** Phone Dash presentation theme (Classic / OptA / OptB / OptC). */
    val dashTheme: DashTheme = DashTheme.DEFAULT,
)

/** UI state for one-shot Analyze via AI. */
data class AiAnalyzeUiState(
    val modeLive: Boolean = true,
    val windowMinutes: Int = AiAnalysisPayloadBuilder.DEFAULT_WINDOW_MINUTES,
    val selectedLogFileName: String? = null,
    val loading: Boolean = false,
    val reportText: String? = null,
    val savedReport: SavedAiReport? = null,
    val error: String? = null,
    val limitedData: Boolean = false,
)

/** Diagnostic trouble code state for the Faults screen. */
data class FaultsState(
    val loading: Boolean = false,
    val stored: List<Dtc> = emptyList(),
    val pending: List<Dtc> = emptyList(),
    val permanent: List<Dtc> = emptyList(),
    val message: String? = null,
    val hasRead: Boolean = false,
)

/** Acceleration/performance results. */
data class PerformanceState(
    val current: AccelResult = AccelResult(),
    val best: AccelResult = AccelResult(),
    val currentSpeedKmh: Double? = null,
    val phase: com.fb2.obd.perf.AccelPhase = com.fb2.obd.perf.AccelPhase.NEED_STOP,
)

data class TripState(
    val distanceKm: Double = 0.0,
    val kmPerLiter: Double? = null,
    val litersPer100: Double? = null,
    val cost: Double = 0.0,
    val idleSeconds: Double = 0.0,
    val fuelPrice: Double = 280.0,
)

data class CustomSensorsState(
    val selectedIds: Set<String> = emptySet(),
    val filter: PidCategory? = null,
    val liveValues: Map<String, String> = emptyMap(),
    val probing: Boolean = false,
)

data class DeepDiagState(
    val loading: Boolean = false,
    val readiness: ReadinessStatus? = null,
    val freeze: FreezeFrame? = null,
    val o2: List<O2TestResult> = emptyList(),
    val mode06: List<Mode06Result> = emptyList(),
)

data class IdleDiagState(
    val loading: Boolean = false,
    val values: Map<String, String> = emptyMap(),
    val tips: List<String> = emptyList(),
)

/** UI state for the double-tap deep sensor search flow. */
data class DeepSearchUiState(
    val active: Boolean = false,
    val confirmLabel: String? = null,
    val confirmPidId: String? = null,
    val running: Boolean = false,
    val progress: String = "",
    val report: DeepSearchReport? = null,
)

/**
 * Collects snapshots from the active [ObdSource] and exposes them as UI state.
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val filesDir: File = app.filesDir

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(SettingsState())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    private val profileStore = VehicleProfileStore(app)
    private val dashThemeStore = DashThemeStore(app)
    val vehicleProfile: VehicleProfile get() = _settings.value.vehicleProfile
    val dashPageTitles: List<String> get() = VehicleProfileConfig.dashPageTitles(vehicleProfile)
    val showHondaModules: Boolean get() = VehicleProfileConfig.showHondaModules(vehicleProfile)

    private val _faults = MutableStateFlow(FaultsState())
    val faults: StateFlow<FaultsState> = _faults.asStateFlow()

    private val _performance = MutableStateFlow(PerformanceState())
    val performance: StateFlow<PerformanceState> = _performance.asStateFlow()

    private val _trip = MutableStateFlow(TripState())
    val trip: StateFlow<TripState> = _trip.asStateFlow()

    private val _custom = MutableStateFlow(CustomSensorsState())
    val custom: StateFlow<CustomSensorsState> = _custom.asStateFlow()

    private val _fuelValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val fuelValues: StateFlow<Map<String, String>> = _fuelValues.asStateFlow()

    private val _transValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val transValues: StateFlow<Map<String, String>> = _transValues.asStateFlow()

    private val _vehicleInfo = MutableStateFlow(VehicleInfo())
    val vehicleInfo: StateFlow<VehicleInfo> = _vehicleInfo.asStateFlow()

    private val _vehicleInfoLoading = MutableStateFlow(false)
    val vehicleInfoLoading: StateFlow<Boolean> = _vehicleInfoLoading.asStateFlow()

    private val _deepDiag = MutableStateFlow(DeepDiagState())
    val deepDiag: StateFlow<DeepDiagState> = _deepDiag.asStateFlow()

    private val _idleDiag = MutableStateFlow(IdleDiagState())
    val idleDiag: StateFlow<IdleDiagState> = _idleDiag.asStateFlow()

    private val _health = MutableStateFlow<HealthScore?>(null)
    val health: StateFlow<HealthScore?> = _health.asStateFlow()

    private val lastElmStore = LastElmStore(File(filesDir, "last_elm.json"))
    private val thresholdStore = HealthThresholdStore(File(filesDir, "health_thresholds.json"))
    private val _healthThresholds = MutableStateFlow(HealthThresholds.DEFAULT)
    val healthThresholds: StateFlow<HealthThresholds> = _healthThresholds.asStateFlow()

    private val voiceAlerter = VoiceAlerter(app)
    private val diagnosticBrain = DiagnosticBrain()
    private var lastAtfForVoice: Double? = null
    private var lastSlipForVoice: Double? = null
    private var lastLiveSnapshotMs: Long = 0L
    private var staleWatchJob: Job? = null
    /** True while [ObdMonitorForegroundService] is expected to be running for a live ELM session. */
    private var elmMonitorActive: Boolean = false
    private var elmMonitorStatus: String? = null

    private val _hondaScan = MutableStateFlow<List<ModuleScanResult>>(emptyList())
    val hondaScan: StateFlow<List<ModuleScanResult>> = _hondaScan.asStateFlow()

    private val _hondaScanning = MutableStateFlow(false)
    val hondaScanning: StateFlow<Boolean> = _hondaScanning.asStateFlow()

    private val _maintenance = MutableStateFlow(MaintenanceStore.defaultTemplate())
    val maintenance: StateFlow<List<MaintenanceEntry>> = _maintenance.asStateFlow()

    private val extraPidStore = DashExtraPidStore(File(filesDir, "dash_extra_pids.json"))
    private val _dashExtraPidIds = MutableStateFlow(extraPidStore.load())
    val dashExtraPidIds: StateFlow<List<String>> = _dashExtraPidIds.asStateFlow()

    private val _dashExtraValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val dashExtraValues: StateFlow<Map<String, String>> = _dashExtraValues.asStateFlow()

    /** Built-in Dash tile label → catalog PID id (double-tap remap). */
    private val tileOverrideStore = DashTileOverrideStore(File(filesDir, "dash_tile_overrides.json"))
    private val _dashTileOverrides = MutableStateFlow(tileOverrideStore.load())
    val dashTileOverrides: StateFlow<Map<String, String>> = _dashTileOverrides.asStateFlow()

    private val sessionLogStore = SessionLogStore(File(filesDir, "value_logs"))
    private val _savedLogs = MutableStateFlow(sessionLogStore.list())
    val savedLogs: StateFlow<List<SavedLogFile>> = _savedLogs.asStateFlow()
    private var sessionStartedMs: Long = 0L
    /** True when the current LOG session was started while on Demo (simulated). */
    private var sessionLoggingIsDemo: Boolean = false

    private val aiReportStore = AiReportStore(File(filesDir, "ai_reports"), app)
    private val _savedAiReports = MutableStateFlow(aiReportStore.list())
    val savedAiReports: StateFlow<List<SavedAiReport>> = _savedAiReports.asStateFlow()

    private val aiAnalysisStore = AiAnalysisStore(app)
    private val openAiClient = OpenAiClient(apiKeyProvider = { aiAnalysisStore.apiKey })

    private val _aiAnalyze = MutableStateFlow(AiAnalyzeUiState())
    val aiAnalyze: StateFlow<AiAnalyzeUiState> = _aiAnalyze.asStateFlow()

    private val logUploadManager = LogUploadManager(app, sessionLogStore, aiReportStore)
    val uploadStatus = logUploadManager.status
    private var autoUploadJob: Job? = null

    private val _deepSearch = MutableStateFlow(DeepSearchUiState())
    val deepSearch: StateFlow<DeepSearchUiState> = _deepSearch.asStateFlow()

    /** Values recovered by deep search, keyed by tile label / pid id. */
    private val _deepFoundValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val deepFoundValues: StateFlow<Map<String, String>> = _deepFoundValues.asStateFlow()
    /** Wall-clock ms when each deepFound entry was first recovered (honest LEDs). */
    private val _deepFoundAtMs = MutableStateFlow<Map<String, Long>>(emptyMap())

    val pidCatalog: List<PidDefinition>
        get() = VehicleProfileConfig.pidCatalog(vehicleProfile)

    fun requestDeepSearch(label: String, pidId: String? = null) {
        _deepSearch.value = DeepSearchUiState(
            active = true,
            confirmLabel = label,
            confirmPidId = pidId,
        )
    }

    fun cancelDeepSearch() {
        _deepSearch.value = DeepSearchUiState()
    }

    fun confirmDeepSearch() {
        val label = _deepSearch.value.confirmLabel ?: return
        val pidId = _deepSearch.value.confirmPidId
        val source = currentSource ?: run {
            _deepSearch.value = DeepSearchUiState(
                active = true,
                report = DeepSearchReport(
                    targetLabel = label,
                    targetId = pidId ?: label,
                    attempts = 0,
                    notes = listOf("Not connected — connect an ELM327 (or Demo) first."),
                ),
            )
            return
        }
        val pid = pidId?.let { id -> pidCatalog.find { it.id.equals(id, true) || it.request.equals(id, true) } }
            ?: pidCatalog.find { it.label.equals(label, true) }
        viewModelScope.launch {
            _deepSearch.update {
                it.copy(running = true, progress = "Starting deep search…", report = null, confirmLabel = label)
            }
            val report = DeepSensorSearch.run(
                source = source,
                label = label,
                pid = pid,
                requestHint = pidId,
                profile = vehicleProfile,
            ) { i, total, title ->
                _deepSearch.update { st ->
                    st.copy(progress = "Trying $i / $total — $title")
                }
            }
            if (report.success) {
                val hit = report.hit!!
                val text = "%.2f %s".format(hit.value, hit.strategy.unit).trim()
                val hitAt = System.currentTimeMillis()
                _deepFoundValues.update {
                    it + (label to text) + (report.targetId to text)
                }
                _deepFoundAtMs.update {
                    it + (label to hitAt) + (report.targetId to hitAt)
                }
                // Feed recovered sensors into the live snapshot so Opt themes,
                // health, voice, and CSV see them — not just Classic overlays.
                _uiState.update { st ->
                    val applied = applyDeepSearchHit(
                        snapshot = st.snapshot,
                        label = label,
                        targetId = report.targetId,
                        value = hit.value,
                        nowMs = hitAt,
                    )
                    st.copy(snapshot = applied)
                }
            }
            _deepSearch.update {
                it.copy(running = false, progress = "", report = report, confirmLabel = null)
            }
        }
    }

    fun setDashExtraPid(slot: Int, pid: PidDefinition) {
        _dashExtraPidIds.update { cur ->
            val next = cur.toMutableList()
            while (next.size <= slot) next.add("")
            next[slot] = pid.id
            next.filter { it.isNotBlank() }
        }
        extraPidStore.save(_dashExtraPidIds.value)
        // One-shot probe so tiles outside the main poll set still show a value.
        probeAndCachePid(pid)
    }

    fun clearDashExtraPid(pidId: String) {
        _dashExtraPidIds.update { it.filter { id -> id != pidId } }
        _dashExtraValues.update { it - pidId }
        extraPidStore.save(_dashExtraPidIds.value)
        publishCarDash()
    }

    /** Replace a built-in Dash tile (e.g. Coolant 1) with any catalog sensor. */
    fun setDashTileOverride(baseLabel: String, pid: PidDefinition) {
        val key = baseLabel.trim()
        if (key.isEmpty()) return
        _dashTileOverrides.update { it + (key to pid.id) }
        tileOverrideStore.save(_dashTileOverrides.value)
        probeAndCachePid(pid)
        publishCarDash()
    }

    fun clearDashTileOverride(baseLabel: String) {
        val key = baseLabel.trim()
        val removedId = _dashTileOverrides.value[key]
        _dashTileOverrides.update { it - key }
        tileOverrideStore.save(_dashTileOverrides.value)
        // Drop cached value only if unused by + extras / other overrides.
        if (removedId != null) {
            val stillNeeded = removedId in _dashExtraPidIds.value ||
                _dashTileOverrides.value.values.any { it.equals(removedId, true) }
            if (!stillNeeded) {
                _dashExtraValues.update { it - removedId }
            }
        }
        publishCarDash()
    }

    private fun probeAndCachePid(pid: PidDefinition) {
        val source = currentSource ?: return
        viewModelScope.launch {
            val probed = source.probePids(listOf(pid))
            val results = LiveSnapshotOverlay.apply(probed, _uiState.value.snapshot)
            val text = results.firstOrNull()?.let { LiveSnapshotOverlay.formatDisplay(it) } ?: "—"
            _dashExtraValues.update { it + (pid.id to text) }
            publishCarDash()
        }
    }

    private val accelTimer = AccelerationTimer()
    private val tripComputer = TripComputer()
    private var collectJob: Job? = null
    private var loggingJob: Job? = null
    private var readinessJob: Job? = null
    /** Throttle Dash CSV samples (~1 Hz). */
    private var lastDashLogMs: Long = 0L
    /** Throttle dashboard_snapshots section (~1 Hz) so long trips don't ring-evict early. */
    private var lastSnapshotLogMs: Long = 0L
    /** Throttle trip / perf / health / AA publish so Demo doesn't fan-out 4 StateFlows every tick. */
    private var lastHeavyUiMs: Long = 0L
    /** Active on-disk checkpoint for the current LOG session (null when not logging). */
    private var activeCheckpointPath: String? = null
    private var lastCheckpointMs: Long = 0L
    private var currentSource: ObdSource? = null
    /** Last TCM probe hit count — used so Health doesn't claim 100% with zero TCM data. */
    private var lastTcmSupportedCount: Int = 0
    private var phoneAx = 0f
    private var phoneAy = 0f
    private var phoneAz = 9.81f
    private val eventTracker = DiagnosticEventTracker()

    init {
        ObdLogger.valueLoggingEnabled = false
        val loadedProfile = profileStore.load()
        val profileGearDefault = VehicleProfileConfig.defaultShowEstimatedGear(loadedProfile)
        _settings.value = SettingsState(
            showEstimatedGear = dashThemeStore.loadShowEstimatedGear(profileGearDefault),
            allowDemo = dashThemeStore.loadAllowDemo(default = true),
            vehicleProfile = loadedProfile,
            dashTheme = dashThemeStore.load(),
        )
        tripComputer.fuelPricePerLiter = _settings.value.fuelPricePerLiter
        _maintenance.value = MaintenanceStore(File(filesDir, "maintenance.json")).load()
        // Keep user-edited thresholds if present; otherwise profile defaults.
        val storedThresholds = thresholdStore.load()
        _healthThresholds.value = if (thresholdStore.hasUserEdits()) {
            storedThresholds
        } else {
            VehicleProfileConfig.healthDefaults(loadedProfile)
        }
        voiceAlerter.enabled = _settings.value.voiceAlerts
        // Re-probe persisted extras + tile overrides so tiles show values after restart.
        viewModelScope.launch {
            _dashExtraPidIds.value.forEach { id ->
                pidCatalog.find { it.id.equals(id, true) }?.let { probeAndCachePid(it) }
            }
            _dashTileOverrides.value.values.distinct().forEach { id ->
                pidCatalog.find { it.id.equals(id, true) }?.let { probeAndCachePid(it) }
            }
        }
        voiceAlerter.duckMediaDuringAlerts = _settings.value.duckMediaDuringAlerts
        voiceAlerter.start()
        logUploadManager.start()
        autoUploadJob = viewModelScope.launch {
            var lastAttemptMs = 0L
            logUploadManager.status.collect { st ->
                val now = System.currentTimeMillis()
                if (st.online &&
                    logUploadManager.githubToken.isNotBlank() &&
                    st.pendingCount > 0 &&
                    !st.uploading &&
                    now - lastAttemptMs > 30_000L
                ) {
                    lastAttemptMs = now
                    logUploadManager.uploadPending(skipFileName = null)
                }
            }
        }
        VehicleLiveStore.onToggleLogging = {
            if (_settings.value.valueLogging) stopValueLogging() else startValueLogging()
            publishCarDash()
        }
        VehicleLiveStore.onConnectRequest = {
            val ui = _uiState.value
            when {
                ConnectActionPolicy.isDisconnectAction(ui.connection, ui.sourceIsLive, ui.reconnecting) ->
                    disconnect()
                !ui.sourceIsLive && _settings.value.allowDemo ->
                    useSource(demoSourceForProfile())
            }
            publishCarDash()
        }
        _custom.update {
            it.copy(selectedIds = StandardPidCatalog.fuelPageDefaults().map { p -> p.id }.toSet())
        }
        if (_settings.value.allowDemo) {
            useSource(demoSourceForProfile())
        } else {
            publishCarDash()
        }
    }

    private fun demoSourceForProfile(): DemoObdSource = DemoObdSource(
        flavour = when (vehicleProfile) {
            VehicleProfile.FB2 -> DemoFlavour.FB2
            VehicleProfile.GENERIC_OBD2 -> DemoFlavour.GENERIC
        },
    )

    fun setVehicleProfile(profile: VehicleProfile) {
        if (profile == vehicleProfile) return
        profileStore.save(profile)
        val gearDefault = VehicleProfileConfig.defaultShowEstimatedGear(profile)
        dashThemeStore.saveShowEstimatedGear(gearDefault)
        _settings.update {
            it.copy(
                vehicleProfile = profile,
                showEstimatedGear = gearDefault,
            )
        }
        // Honda-only extras stay on disk so switching back to FB2 restores them.
        // Generic Dash already hides ids that are not in the SAE catalog.
        if (profile.isGeneric) {
            _transValues.value = emptyMap()
            _hondaScan.value = emptyList()
            _deepFoundValues.value = _deepFoundValues.value.filterKeys { key ->
                pidCatalog.any { it.label.equals(key, true) || it.id.equals(key, true) }
            }
            _deepFoundAtMs.value = _deepFoundAtMs.value.filterKeys { key ->
                _deepFoundValues.value.containsKey(key)
            }
        }
        if (!thresholdStore.hasUserEdits()) {
            _healthThresholds.value = VehicleProfileConfig.healthDefaults(profile)
        }
        // Restart Demo under the new flavour when not on a live ELM (and Demo is allowed).
        if (!_uiState.value.sourceIsLive && _settings.value.allowDemo) {
            useSource(demoSourceForProfile())
        }
        publishCarDash()
    }

    /** Push the phone main Dash (tiles + hero) to Android Auto. */
    fun publishCarDash() {
        val ui = _uiState.value
        val decision = ui.decisionSnapshot.takeUnless { it.isEffectivelyBlank() } ?: ui.snapshot
        VehicleLiveStore.publish(
            CarDashBuilder.build(
                snapshot = ui.snapshot,
                thresholds = _healthThresholds.value,
                extraPidIds = _dashExtraPidIds.value,
                extraValues = _dashExtraValues.value,
                deepFoundValues = _deepFoundValues.value,
                catalog = pidCatalog,
                connection = ui.connection,
                sourceIsLive = ui.sourceIsLive,
                sourceName = ui.sourceName,
                logging = _settings.value.valueLogging,
                showEstimatedGear = _settings.value.showEstimatedGear,
                dtcCount = ui.dtcCount,
                healthScore = _health.value,
                healthSnapshot = decision,
                latch = diagnosticBrain::latch,
            ),
        )
    }

    /** Zone hysteresis shared by phone Dash + Android Auto tiles. */
    fun latchHealth(key: String, status: MetricStatus): MetricStatus =
        diagnosticBrain.latch(key, status)

    fun updateHealthThresholdField(id: String, value: Double) {
        val next = _healthThresholds.value.withField(id, value)
        _healthThresholds.value = next
        thresholdStore.save(next)
        recalcHealth()
    }

    fun resetHealthThresholds() {
        val defaults = VehicleProfileConfig.healthDefaults(vehicleProfile)
        _healthThresholds.value = defaults
        thresholdStore.save(defaults)
        recalcHealth()
    }

    fun updatePhoneSensors(ax: Float, ay: Float, az: Float) {
        phoneAx = ax
        phoneAy = ay
        phoneAz = az
    }

    /** Begin a fresh in-memory session (does not erase previously saved CSV files). */
    fun startValueLogging() {
        loggingJob?.cancel()
        ObdLogger.clearValues()
        sessionStartedMs = System.currentTimeMillis()
        sessionLoggingIsDemo = !_uiState.value.sourceIsLive
        ObdLogger.sessionStartMs = sessionStartedMs
        ObdLogger.valueLoggingEnabled = true
        _settings.update { it.copy(valueLogging = true) }
        if (sessionLoggingIsDemo) {
            ObdLogger.logDebug(
                ObdLogger.Dir.INFO,
                "VALUE LOG session start — DEMO (simulated readings, not live ELM)",
            )
            ObdLogger.logEvent("LOG", "Demo mode — readings are simulated, not from a live vehicle")
        } else {
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "VALUE LOG session start — main Dash only")
        }
        lastDashLogMs = 0L
        lastSnapshotLogMs = 0L
        lastCheckpointMs = 0L
        // Stable on-disk file from the first second — crash mid-trip still leaves a CSV.
        val checkpoint = sessionLogStore.beginCheckpointFile(sessionStartedMs, sessionLoggingIsDemo)
        activeCheckpointPath = checkpoint.absolutePath
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "VALUE LOG checkpoint file ${checkpoint.fileName}")
        logDashValues(_uiState.value.snapshot, force = true)
        checkpointLogToDisk(force = true)
        // Lightly refresh user-added Dash (+) tiles so extras stay current in the CSV.
        // Also flush the session CSV to disk on a long-haul-safe interval.
        loggingJob = viewModelScope.launch {
            while (isActive && ObdLogger.valueLoggingEnabled) {
                refreshDashExtrasForLog()
                checkpointLogToDisk(force = false)
                delay(5_000L)
            }
        }
    }

    /**
     * Stop logging and persist the current buffer as its own timestamped CSV
     * under app files (`value_logs/`). Returns the saved file, or null if empty.
     */
    fun stopValueLogging(): SavedLogFile? {
        loggingJob?.cancel()
        loggingJob = null
        val wasOn = ObdLogger.valueLoggingEnabled
        val isDemo = sessionLoggingIsDemo
        // Final snapshot while logging is still enabled.
        logDashValues(_uiState.value.snapshot, force = true)
        ObdLogger.valueLoggingEnabled = false
        _settings.update { it.copy(valueLogging = false) }
        val empty = ObdLogger.valueRows().isEmpty() &&
            ObdLogger.tabValueRows().none { it.tab.equals("Dash", true) }
        if (!wasOn && empty) {
            sessionLoggingIsDemo = false
            activeCheckpointPath = null
            return null
        }
        val csv = ObdLogger.valuesCsv(isDemo = isDemo)
        val started = if (sessionStartedMs > 0L) sessionStartedMs else System.currentTimeMillis()
        val path = activeCheckpointPath
        val saved = if (path != null) {
            sessionLogStore.writeCheckpoint(path, csv)
                ?: sessionLogStore.saveSession(csv, started, isDemo = isDemo)
        } else {
            sessionLogStore.saveSession(csv, started, isDemo = isDemo)
        }
        activeCheckpointPath = null
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "VALUE LOG saved ${saved.fileName}")
        ObdLogger.sessionStartMs = 0L
        sessionLoggingIsDemo = false
        _savedLogs.value = sessionLogStore.list()
        // Mirror into Downloads for USB pull, then try cloud upload if online.
        runCatching {
            LogExportHelper.exportFile(
                getApplication(),
                File(saved.absolutePath),
                saved.fileName,
                "text/csv",
            )
        }
        viewModelScope.launch {
            if (logUploadManager.githubToken.isNotBlank() && logUploadManager.isOnline()) {
                logUploadManager.uploadPending()
            } else {
                logUploadManager.refreshCounts()
            }
        }
        return saved
    }

    /** Flush in-memory LOG buffer to the session CSV (every ~60s, or [force]). */
    private fun checkpointLogToDisk(force: Boolean) {
        if (!ObdLogger.valueLoggingEnabled) return
        val path = activeCheckpointPath ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckpointMs < CHECKPOINT_INTERVAL_MS) return
        lastCheckpointMs = now
        val isDemo = sessionLoggingIsDemo
        runCatching {
            val csv = ObdLogger.valuesCsv(isDemo = isDemo)
            sessionLogStore.writeCheckpoint(path, csv)
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "VALUE LOG checkpoint ${csv.length} chars")
        }.onFailure {
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "VALUE LOG checkpoint failed: ${it.message}")
        }
    }

    fun setGithubUploadToken(token: String) {
        logUploadManager.githubToken = token
    }

    fun githubUploadToken(): String = logUploadManager.githubToken

    fun setOpenAiApiKey(key: String) {
        aiAnalysisStore.apiKey = key
    }

    fun openAiApiKey(): String = aiAnalysisStore.apiKey

    fun setAiAnalyzeModeLive(live: Boolean) {
        _aiAnalyze.update { it.copy(modeLive = live, error = null) }
    }

    fun setAiAnalyzeWindowMinutes(minutes: Int) {
        _aiAnalyze.update {
            it.copy(windowMinutes = AiAnalysisPayloadBuilder.clampWindowMinutes(minutes), error = null)
        }
    }

    fun setAiAnalyzeSelectedLog(fileName: String?) {
        _aiAnalyze.update { it.copy(selectedLogFileName = fileName, error = null) }
    }

    fun clearAiAnalyzeResult() {
        _aiAnalyze.update { it.copy(reportText = null, savedReport = null, error = null, limitedData = false) }
    }

    fun refreshSavedAiReports() {
        _savedAiReports.value = aiReportStore.list()
    }

    /**
     * One-shot OpenAI analysis: live lookback window or a saved session CSV slice.
     * Saves the reply as `.txt` under ai_reports/ (GitHub sync picks it up).
     */
    fun runAiAnalysis() {
        val st = _aiAnalyze.value
        if (st.loading) return
        if (aiAnalysisStore.apiKey.isBlank()) {
            _aiAnalyze.update {
                it.copy(error = "Add an OpenAI API key in Settings → AI analysis")
            }
            return
        }
        if (!logUploadManager.isOnline()) {
            _aiAnalyze.update {
                it.copy(error = AiAnalysisErrors.NO_INTERNET)
            }
            return
        }
        viewModelScope.launch {
            _aiAnalyze.update {
                it.copy(loading = true, error = null, reportText = null, savedReport = null)
            }
            try {
                val minutes = AiAnalysisPayloadBuilder.clampWindowMinutes(st.windowMinutes)
                val now = System.currentTimeMillis()
                val snapshot = _uiState.value.snapshot
                val health = _health.value
                val dtcText = buildString {
                    val stored = _faults.value.stored
                    val pending = _faults.value.pending
                    if (stored.isEmpty() && pending.isEmpty()) {
                        appendLine("(no DTCs loaded — open Faults to refresh if needed)")
                    } else {
                        if (stored.isNotEmpty()) {
                            appendLine("Stored:")
                            stored.forEach { appendLine("- ${it.code}: ${it.description}") }
                        }
                        if (pending.isNotEmpty()) {
                            appendLine("Pending:")
                            pending.forEach { appendLine("- ${it.code}: ${it.description}") }
                        }
                    }
                    _uiState.value.dtcCount?.let { appendLine("Readiness DTC count: $it") }
                }
                val truncated = if (st.modeLive) {
                    val snaps = ObdLogger.valueRows().map { row ->
                        row.timestampMs to AiAnalysisPayloadBuilder.snapshotCsvLine(row.timestampMs, row.snapshot)
                    }
                    val evs = ObdLogger.eventRows().map { e ->
                        val msg = e.message.replace(",", " ").replace("\n", " ")
                        e.timestampMs to "${e.timestampMs},${e.category},$msg"
                    }
                    AiAnalysisPayloadBuilder.truncateByTime(snaps, evs, minutes, now)
                } else {
                    val name = st.selectedLogFileName
                        ?: error("Pick a saved log file first")
                    val csv = sessionLogStore.read(name)
                        ?: error("Could not read $name")
                    AiAnalysisPayloadBuilder.truncateSavedCsv(csv, minutes, now)
                }
                val sourceLabel = if (st.modeLive) {
                    if (!_uiState.value.sourceIsLive) {
                        "demo_live_window_${minutes}min"
                    } else {
                        "live_window_${minutes}min"
                    }
                } else {
                    "saved:${st.selectedLogFileName}"
                }
                val isDemo = if (st.modeLive) {
                    !_uiState.value.sourceIsLive
                } else {
                    st.selectedLogFileName?.contains("demo", ignoreCase = true) == true
                }
                val payload = AiAnalysisPayloadBuilder.buildUserMessage(
                    sourceLabel = sourceLabel,
                    windowMinutes = minutes,
                    snapshotText = AiAnalysisPayloadBuilder.formatSnapshot(snapshot),
                    healthText = AiAnalysisPayloadBuilder.formatHealth(health),
                    dtcText = dtcText,
                    log = truncated,
                    isDemo = isDemo,
                    vehicleLabel = when (vehicleProfile) {
                        VehicleProfile.FB2 -> "Honda Civic FB2"
                        VehicleProfile.GENERIC_OBD2 -> "generic OBD-II vehicle"
                    },
                    includeHondaEldHint = vehicleProfile.isFb2,
                )
                val result = openAiClient.complete(payload.systemPrompt, payload.userMessage)
                val parsed = AiAnalysisPayloadBuilder.parseModelResponse(result.text)
                val readingsAppendix = buildString {
                    if (isDemo) {
                        appendLine("NOTE: These readings are from DEMO (simulated), not a live ELM/vehicle connection.")
                        appendLine()
                    }
                    appendLine("--- Window metadata (app-computed) ---")
                    appendLine("requested_window_minutes=${payload.windowMinutes}")
                    appendLine("actual_window_seconds=${payload.actualDurationSeconds}")
                    payload.firstTimestampMs?.let {
                        appendLine("window_start_utc=${AiAnalysisPayloadBuilder.formatIsoUtc(it)}")
                    }
                    payload.lastTimestampMs?.let {
                        appendLine("window_end_utc=${AiAnalysisPayloadBuilder.formatIsoUtc(it)}")
                    }
                    appendLine("snapshot_rows=${payload.sampleCount}")
                    appendLine("unique_timestamps=${payload.uniqueTimestampCount}")
                    appendLine()
                    appendLine("--- Latest snapshot ---")
                    appendLine(AiAnalysisPayloadBuilder.formatSnapshot(snapshot).trim())
                    appendLine()
                    appendLine("--- App health notes ---")
                    appendLine(AiAnalysisPayloadBuilder.formatHealth(health).trim())
                    appendLine()
                    appendLine("--- DTCs ---")
                    appendLine(dtcText.trim())
                    appendLine()
                    appendLine(
                        "--- Time-window CSV (requested ${payload.windowMinutes} min, " +
                            "actual ${payload.actualDurationSeconds}s, ${truncated.rowCount} rows) ---",
                    )
                    appendLine(truncated.csvText.trim())
                }
                // Persist the detailed FULL_REPORT (+ readings audit), not the short brief.
                val saved = aiReportStore.saveReport(
                    body = parsed.fullReport,
                    sourceLabel = sourceLabel,
                    windowMinutes = payload.windowMinutes,
                    model = result.model,
                    readingsAppendix = readingsAppendix,
                    isDemo = isDemo,
                    actualDurationSeconds = payload.actualDurationSeconds,
                    windowStartUtc = payload.firstTimestampMs?.let {
                        AiAnalysisPayloadBuilder.formatIsoUtc(it)
                    },
                    windowEndUtc = payload.lastTimestampMs?.let {
                        AiAnalysisPayloadBuilder.formatIsoUtc(it)
                    },
                    snapshotRows = payload.sampleCount,
                    uniqueTimestamps = payload.uniqueTimestampCount,
                )
                val screenText = buildString {
                    append(parsed.screenBrief.trim())
                    appendLine()
                    appendLine()
                    appendLine("Full report saved to:")
                    appendLine(saved.fileName)
                }
                _savedAiReports.value = aiReportStore.list()
                logUploadManager.refreshCounts()
                _aiAnalyze.update {
                    it.copy(
                        loading = false,
                        reportText = screenText,
                        savedReport = saved,
                        limitedData = payload.limited,
                        error = null,
                    )
                }
                // Best-effort sync when online (same as value logs).
                if (logUploadManager.isOnline() && logUploadManager.githubToken.isNotBlank()) {
                    logUploadManager.uploadPending()
                }
            } catch (e: Exception) {
                _aiAnalyze.update {
                    it.copy(
                        loading = false,
                        error = AiAnalysisErrors.friendlyMessage(e),
                    )
                }
            }
        }
    }

    fun uploadSavedLogs() {
        viewModelScope.launch {
            logUploadManager.uploadPending()
        }
    }

    /** Re-probe user-added (+) sensors and remapped built-in tiles. */
    private suspend fun refreshDashExtrasForLog() {
        val source = currentSource ?: return
        val ids = (
            _dashExtraPidIds.value + _dashTileOverrides.value.values
            ).distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return
        val pids = ids.mapNotNull { id -> pidCatalog.find { it.id.equals(id, true) } }
        if (pids.isEmpty()) return
        val results = LiveSnapshotOverlay.apply(
            source.probePids(pids, recoverFirst = false),
            _uiState.value.snapshot,
        )
        val live = results.associate { r -> r.pid.id to LiveSnapshotOverlay.formatDisplay(r) }
        _dashExtraValues.update { it + live }
        logDashValues(_uiState.value.snapshot, force = true)
    }

    /**
     * Log main Dash only: hero (RPM/Speed/Gear) + built-in tiles + any + extras.
     * Fuel / Trip / Trans / Perf / etc. are NOT written to the value CSV.
     */
    private fun logDashValues(snapshot: VehicleSnapshot, force: Boolean = false) {
        if (!ObdLogger.valueLoggingEnabled) return
        val now = System.currentTimeMillis()
        if (!force && now - lastDashLogMs < 1_000L) return
        lastDashLogMs = now

        val health = _health.value
        val dtc = _uiState.value.dtcCount
        val extras = LinkedHashMap<String, String>()
        _dashExtraPidIds.value.forEach { id ->
            val pid = pidCatalog.find { it.id.equals(id, true) } ?: return@forEach
            val text = _dashExtraValues.value[id]
                ?: _deepFoundValues.value[pid.label]
                ?: _deepFoundValues.value[id]
                ?: LiveSnapshotOverlay.formatLiveOrNs(pid, snapshot)
            extras[pid.label] = text
        }
        _deepFoundValues.value.forEach { (label, value) ->
            // Recovered n/s tiles that belong to the main Dash layout.
            if (label !in extras) extras["Deep:$label"] = value
        }

        ObdLogger.logTabMap(
            "Dash",
            mapOf(
                "RPM" to (snapshot.rpm?.toString() ?: "n/s"),
                "Speed" to (snapshot.speedKmh?.toString() ?: "n/s"),
                "Gear" to (snapshot.gear?.toString() ?: "n/s"),
                "GearSource" to snapshot.gearSource.name,
                "Coolant1" to (snapshot.coolantC?.toString() ?: "n/s"),
                "Coolant2" to (snapshot.coolant2C?.toString() ?: "n/s"),
                "Battery" to (snapshot.batteryVolts?.toString() ?: "n/s"),
                "Intake" to (snapshot.intakeC?.toString() ?: "n/s"),
                "Ambient" to (snapshot.ambientC?.toString() ?: "n/s"),
                "Load" to (snapshot.engineLoadPct?.toString() ?: "n/s"),
                "Throttle" to (snapshot.throttlePct?.toString() ?: "n/s"),
                "STFT" to (snapshot.stftPct?.toString() ?: "n/s"),
                "LTFT" to (snapshot.ltftPct?.toString() ?: "n/s"),
                "MAF" to (snapshot.mafGps?.toString() ?: "n/s"),
                "MAP" to (snapshot.mapKpa?.toString() ?: "n/s"),
                "Timing" to (snapshot.timingAdvance?.toString() ?: "n/s"),
                "FuelLoop" to (snapshot.fuelSystemStatus ?: "n/s"),
                "DTCs" to (dtc?.toString() ?: "n/s"),
                "HealthPct" to (health?.vehiclePct?.toString() ?: "n/s"),
            ) + extras,
            now,
        )
    }

    fun refreshSavedLogs() {
        _savedLogs.value = sessionLogStore.list()
    }

    fun readSavedLog(fileName: String): String? = sessionLogStore.read(fileName)

    fun deleteSavedLog(fileName: String) {
        sessionLogStore.delete(fileName)
        _savedLogs.value = sessionLogStore.list()
        logUploadManager.refreshCounts()
    }

    fun setShowEstimatedGear(enabled: Boolean) {
        dashThemeStore.saveShowEstimatedGear(enabled)
        _settings.update { it.copy(showEstimatedGear = enabled) }
    }

    /**
     * Settings → Simulation. Off while on Demo clears the feed to disconnected
     * (`--` on Dash). On while disconnected starts the profile Demo source.
     */
    fun setAllowDemo(enabled: Boolean) {
        dashThemeStore.saveAllowDemo(enabled)
        _settings.update { it.copy(allowDemo = enabled) }
        val ui = _uiState.value
        when (DemoAllowPolicy.next(enabled, ui.sourceIsLive, ui.connection)) {
            DemoAllowPolicy.Next.DISCONNECT -> disconnect()
            DemoAllowPolicy.Next.START_DEMO -> useSource(demoSourceForProfile())
            DemoAllowPolicy.Next.NONE -> Unit
        }
        publishCarDash()
    }

    fun setDashTheme(theme: DashTheme) {
        if (theme == _settings.value.dashTheme) return
        dashThemeStore.save(theme)
        _settings.update { it.copy(dashTheme = theme) }
    }

    fun setVoiceAlerts(enabled: Boolean) {
        _settings.update { it.copy(voiceAlerts = enabled) }
        voiceAlerter.enabled = enabled
        if (enabled) {
            voiceAlerter.start()
            voiceAlerter.speakTest("Voice alerts on")
        }
    }

    fun setDuckMediaDuringAlerts(enabled: Boolean) {
        _settings.update { it.copy(duckMediaDuringAlerts = enabled) }
        voiceAlerter.duckMediaDuringAlerts = enabled
    }

    /**
     * Settings “Check sound alert” — same tone + phrase as a live battery
     * critical alarm, so the driver can verify cabin audio without waiting
     * for a real fault.
     */
    fun testSoundAlert() {
        voiceAlerter.start()
        voiceAlerter.speakTest("Battery critical")
    }

    /** Cancel the active OBD source (closes ELM socket) and mark offline. */
    fun disconnect() {
        if (_settings.value.valueLogging) {
            stopValueLogging()
        }
        readinessJob?.cancel()
        readinessJob = null
        staleWatchJob?.cancel()
        staleWatchJob = null
        collectJob?.cancel()
        collectJob = null
        currentSource = null
        lastElmStore.markUserDisconnected()
        stopElmMonitor()
        eventTracker.onConnection(ConnectionState.DISCONNECTED, false, "")
        eventTracker.reset()
        _uiState.update {
            it.copy(
                snapshot = VehicleSnapshot.EMPTY,
                connection = ConnectionState.DISCONNECTED,
                sourceName = "",
                sourceIsLive = false,
                dtcCount = null,
                reconnecting = false,
                lastError = null,
            )
        }
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "Disconnected")
        publishCarDash()
    }

    fun useSource(source: ObdSource) {
        readinessJob?.cancel()
        staleWatchJob?.cancel()
        collectJob?.cancel()
        currentSource = source
        if (source is Elm327BluetoothSource) {
            runCatching {
                lastElmStore.saveConnected(source.deviceAddress, source.deviceName)
            }.onFailure { e ->
                ObdLogger.logDebug(ObdLogger.Dir.INFO, "Last ELM save failed: ${e.message}")
            }
        }
        // Demo / non-live: drop the connected-device FGS. Live ELM starts it on first frame.
        if (!source.isLive) {
            stopElmMonitor()
        }
        eventTracker.reset()
        diagnosticBrain.reset()
        voiceAlerter.resetHoldTimers()
        _faults.update { FaultsState() }
        _uiState.update {
            it.copy(
                connection = ConnectionState.CONNECTING,
                sourceName = source.name,
                sourceIsLive = source.isLive,
                decisionSnapshot = VehicleSnapshot.EMPTY,
                dtcCount = null,
                reconnecting = false,
                lastError = null,
            )
        }
        publishCarDash()
        // Real ELM: auto-start value logging unless already running.
        // Manual STOP LOG turns it off until the next fresh ELM connect.
        if (source.isLive && !_settings.value.valueLogging) {
            startValueLogging()
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "VALUE LOG auto-started on ELM connect")
        }
        collectJob = source.snapshots()
            .onEach { incoming ->
                lastLiveSnapshotMs = System.currentTimeMillis()
                val prev = _uiState.value.snapshot
                // Never wipe a live Dash with a blank reconnect frame.
                val base = if (incoming.isEffectivelyBlank() && !prev.isEffectivelyBlank()) {
                    prev
                } else {
                    incoming
                }
                // Keep deep-search recoveries until a live poll fills that field again.
                val snapshot = overlayDeepFoundOntoSnapshot(base)
                clearDeepFoundWhenLive(incoming)
                val now = System.currentTimeMillis()
                if (ObdLogger.valueLoggingEnabled && now - lastSnapshotLogMs >= 1_000L) {
                    lastSnapshotLogMs = now
                    // Log raw ELM frame — not deepFound overlay — so CSV stays honest.
                    ObdLogger.logSnapshot(incoming, now)
                }
                // Smooth noisy sensors for health/voice; UI still shows raw [snapshot].
                val decision = diagnosticBrain.decisionSnapshot(snapshot)
                snapshot.speedKmh?.let { accelTimer.onSample(now, it) }
                tripComputer.onSample(now, snapshot.speedKmh, snapshot.mafGps, null)
                val heavyDue = now - lastHeavyUiMs >= 1_000L
                if (heavyDue) {
                    lastHeavyUiMs = now
                    _trip.update {
                        TripState(
                            tripComputer.distanceKm,
                            tripComputer.kmPerLiter,
                            tripComputer.litersPer100Km,
                            tripComputer.tripCost,
                            tripComputer.idleSeconds,
                            tripComputer.fuelPricePerLiter,
                        )
                    }
                    _performance.update {
                        it.copy(
                            current = accelTimer.current,
                            best = accelTimer.best,
                            currentSpeedKmh = snapshot.speedKmh,
                            phase = accelTimer.phase,
                        )
                    }
                    _health.update {
                        scoreHealth(snapshot = decision)
                    }
                } else {
                    // Keep perf phase/speed somewhat current without full health recalc.
                    _performance.update {
                        it.copy(
                            currentSpeedKmh = snapshot.speedKmh,
                            phase = accelTimer.phase,
                        )
                    }
                }
                voiceAlerter.onSnapshot(
                    snapshot = decision,
                    thresholds = _healthThresholds.value,
                    atfC = lastAtfForVoice,
                    tcSlipRpm = lastSlipForVoice,
                )
                val lockText = _transValues.value.entries
                    .firstOrNull { it.key.contains("lock", true) }
                    ?.value
                // Event tracker sees the real incoming frame (not the sticky display).
                if (!incoming.isEffectivelyBlank()) {
                    eventTracker.onSnapshot(incoming, _healthThresholds.value, lockText)
                    eventTracker.onDtcCount(_uiState.value.dtcCount)
                }
                _uiState.update {
                    it.copy(
                        snapshot = snapshot,
                        decisionSnapshot = decision,
                        connection = ConnectionState.CONNECTED,
                        reconnecting = false,
                        lastError = null,
                    )
                }
                eventTracker.onConnection(
                    ConnectionState.CONNECTED,
                    source.isLive,
                    source.name,
                )
                if (source.isLive) {
                    ensureElmMonitor(ObdMonitorForegroundService.STATUS_LIVE)
                }
                if (heavyDue) {
                    publishCarDash()
                }
                // Sample main Dash only into the session CSV.
                if (ObdLogger.valueLoggingEnabled) {
                    logDashValues(snapshot)
                }
            }
            .catch { err ->
                val msg = err.message ?: err.javaClass.simpleName
                ObdLogger.logDebug(ObdLogger.Dir.INFO, "Connection ERROR: $msg")
                ObdLogger.logEvent("ELM", "Connection error: $msg")
                eventTracker.onConnection(ConnectionState.ERROR, false, source.name)
                // Keep last-good snapshot on screen — only flip the status chip.
                _uiState.update { st ->
                    st.copy(
                        connection = ConnectionState.ERROR,
                        reconnecting = false,
                        lastError = msg,
                    )
                }
                // Permanent failure of the live session (retryWhen aborted) — drop FGS.
                if (source.isLive) {
                    stopElmMonitor()
                }
                publishCarDash()
            }
            .launchIn(viewModelScope)

        // If live frames stop arriving, mark reconnecting while the source retries.
        if (source.isLive) {
            lastLiveSnapshotMs = System.currentTimeMillis()
            staleWatchJob = viewModelScope.launch {
                while (isActive && currentSource === source) {
                    delay(2_000L)
                    val silentFor = System.currentTimeMillis() - lastLiveSnapshotMs
                    val ui = _uiState.value
                    if (ui.connection == ConnectionState.CONNECTED && silentFor > 8_000L) {
                        ObdLogger.logDebug(
                            ObdLogger.Dir.INFO,
                            "No ELM frames for ${silentFor}ms — marking reconnecting",
                        )
                        _uiState.update {
                            it.copy(
                                reconnecting = true,
                                connection = ConnectionState.CONNECTING,
                            )
                        }
                        ensureElmMonitor(ObdMonitorForegroundService.STATUS_RETRY)
                        publishCarDash()
                    }
                }
            }
        }

        // Lightweight readiness poll for Dash DTC counter (Mode 01 PID 01).
        readinessJob = viewModelScope.launch {
            delay(800L)
            while (isActive && currentSource === source) {
                if (_uiState.value.reconnecting ||
                    _uiState.value.connection == ConnectionState.ERROR
                ) {
                    delay(2_000L)
                    continue
                }
                runCatching {
                    val readiness = source.readReadiness()
                    _uiState.update { it.copy(dtcCount = readiness.dtcCount) }
                    eventTracker.onDtcCount(readiness.dtcCount)
                    _health.update {
                        scoreHealth(dtcCount = readiness.dtcCount.coerceAtLeast(_faults.value.stored.size))
                    }
                    publishCarDash()
                }
                delay(12_000L)
            }
        }
    }

    private fun ensureElmMonitor(status: String) {
        val app = getApplication<Application>()
        if (!elmMonitorActive) {
            // First successful live connect — sticky "ELM connected".
            ObdMonitorForegroundService.start(app, ObdMonitorForegroundService.STATUS_CONNECTED)
            elmMonitorActive = true
            elmMonitorStatus = ObdMonitorForegroundService.STATUS_CONNECTED
            return
        }
        // Keep "ELM connected" until a RETRY cycle; then flip LIVE ↔ RETRY.
        if (status == ObdMonitorForegroundService.STATUS_LIVE &&
            elmMonitorStatus == ObdMonitorForegroundService.STATUS_CONNECTED
        ) {
            return
        }
        if (elmMonitorStatus == status) return
        ObdMonitorForegroundService.updateStatus(app, status)
        elmMonitorStatus = status
    }

    /**
     * Sticky FGS / process death: reconnect the last ELM unless the driver
     * tapped Disconnect / Exit.
     */
    @SuppressLint("MissingPermission")
    fun reconnectLastElmIfIdle() {
        val live = currentSource?.isLive == true
        val conn = _uiState.value.connection
        if (live && conn != ConnectionState.DISCONNECTED && conn != ConnectionState.ERROR) {
            ensureElmMonitor(ObdMonitorForegroundService.STATUS_LIVE)
            return
        }
        val saved = lastElmStore.load()
        if (!KeepAlivePolicy.shouldReconnectAfterDeath(saved.address, saved.userDisconnected)) {
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val remote = runCatching { adapter.getRemoteDevice(saved.address) }.getOrNull() ?: return
        ObdLogger.logDebug(
            ObdLogger.Dir.INFO,
            "Keep-alive reconnect ${saved.name ?: ""} ${saved.address}",
        )
        useSource(Elm327BluetoothSource(remote))
    }

    private fun stopElmMonitor() {
        if (!elmMonitorActive && elmMonitorStatus == null) return
        ObdMonitorForegroundService.stop(getApplication())
        elmMonitorActive = false
        elmMonitorStatus = null
    }

    fun readFaults() {
        val source = currentSource ?: return
        viewModelScope.launch {
            _faults.update { it.copy(loading = true, message = null) }
            val stored = source.readStoredDtcs()
            val pending = source.readPendingDtcs()
            val permanent = source.readPermanentDtcs()
            _faults.update {
                it.copy(
                    loading = false,
                    stored = stored,
                    pending = pending,
                    permanent = permanent,
                    hasRead = true,
                    message = if (stored.isEmpty() && pending.isEmpty() && permanent.isEmpty()) {
                        "No fault codes found."
                    } else {
                        null
                    },
                )
            }
            _uiState.update { it.copy(dtcCount = stored.size) }
            eventTracker.onDtcCount(stored.size)
            _health.update {
                scoreHealth(dtcCount = stored.size)
            }
            publishCarDash()
        }
    }

    fun clearFaults() {
        val source = currentSource ?: return
        viewModelScope.launch {
            _faults.update { it.copy(loading = true, message = null) }
            val ok = source.clearDtcs()
            val stored = source.readStoredDtcs()
            val pending = source.readPendingDtcs()
            val permanent = source.readPermanentDtcs()
            _faults.update {
                it.copy(
                    loading = false,
                    stored = stored,
                    pending = pending,
                    permanent = permanent,
                    hasRead = true,
                    message = if (ok) {
                        "Codes cleared (Mode 04). Permanent (Mode 0A) codes may remain until the ECU clears them."
                    } else {
                        "Clear failed or not supported."
                    },
                )
            }
            _uiState.update { it.copy(dtcCount = stored.size) }
            eventTracker.onDtcCount(stored.size)
            _health.update { scoreHealth(dtcCount = stored.size) }
            publishCarDash()
        }
    }

    fun resetPerformance() {
        accelTimer.reset()
        _performance.update {
            it.copy(
                current = accelTimer.current,
                best = accelTimer.best,
                phase = accelTimer.phase,
            )
        }
    }

    fun resetTrip() {
        tripComputer.reset()
        _trip.update {
            TripState(fuelPrice = tripComputer.fuelPricePerLiter)
        }
    }

    /** PKR per liter — used for trip cost. Clamped to a sensible petrol range. */
    fun setFuelPricePerLiter(price: Double) {
        val p = price.coerceIn(50.0, 1000.0)
        tripComputer.fuelPricePerLiter = p
        _settings.update { it.copy(fuelPricePerLiter = p) }
        _trip.update {
            it.copy(
                cost = tripComputer.tripCost,
                fuelPrice = p,
            )
        }
    }

    fun setCustomFilter(cat: PidCategory?) {
        _custom.update { it.copy(filter = cat) }
    }

    fun toggleCustomPid(pid: PidDefinition) {
        _custom.update {
            val next = it.selectedIds.toMutableSet()
            if (!next.add(pid.id)) next.remove(pid.id)
            it.copy(selectedIds = next)
        }
    }

    fun probeCustomSelected() {
        val source = currentSource ?: return
        viewModelScope.launch {
            _custom.update { it.copy(probing = true) }
            val selected = pidCatalog.filter { it.id in _custom.value.selectedIds }
            val probed = source.probePids(selected)
            val results = LiveSnapshotOverlay.apply(probed, _uiState.value.snapshot)
            ObdLogger.logProbe("Custom sensors", results)
            val live = results.associate { r ->
                r.pid.id to LiveSnapshotOverlay.formatDisplay(r)
            }
            _custom.update { it.copy(probing = false, liveValues = live) }
        }
    }

    fun refreshFuelPage() {
        val source = currentSource ?: return
        viewModelScope.launch {
            val defs = StandardPidCatalog.fuelPageDefaults() +
                VehicleProfileConfig.fuelExtraPids(vehicleProfile)
            val probed = source.probePids(defs.distinctBy { it.id })
            val results = LiveSnapshotOverlay.apply(probed, _uiState.value.snapshot)
            ObdLogger.logProbe("Fuel system", results)
            _fuelValues.value = results.associate { r ->
                r.pid.label to LiveSnapshotOverlay.formatDisplay(r)
            }
        }
    }

    fun refreshIdleDiagnostics() {
        val source = currentSource ?: return
        viewModelScope.launch {
            _idleDiag.update { it.copy(loading = true) }
            val liveSnap = _uiState.value.snapshot
            // Paint live Dash values immediately so the page isn't stuck on blank "Probing…".
            val ordered = ColdStartIdleCatalog.allPidsFor(vehicleProfile).sortedBy { pid ->
                when {
                    pid.request.startsWith("01") -> 0
                    pid.request.startsWith("22") -> 2
                    else -> 1
                }
            }
            val mode01 = ordered.filter { it.request.startsWith("01") }
            val mode22 = ordered.filter { !it.request.startsWith("01") }

            val prefill = LiveSnapshotOverlay.apply(
                mode01.map { PidProbeResult(it, false, null, null) } +
                    mode22.map { PidProbeResult(it, false, null, null) },
                liveSnap,
            )
            _idleDiag.update {
                it.copy(
                    loading = true,
                    values = prefill.associate { r -> r.pid.id to LiveSnapshotOverlay.formatDisplay(r) } +
                        prefill.associate { r -> r.pid.label to LiveSnapshotOverlay.formatDisplay(r) },
                )
            }

            // Probe Mode 01 first (fast). Skip Mode 22 if the bus is already unhealthy.
            val probed01 = source.probePids(mode01)
            val unable = probed01.count { r ->
                r.raw?.uppercase()?.contains("UNABLE") == true ||
                    r.raw?.contains("SKIPPED", true) == true
            }
            val busOk = unable < 2
            val probed22 = if (busOk) {
                source.probePids(mode22)
            } else {
                ObdLogger.logDebug(
                    ObdLogger.Dir.INFO,
                    "Idle probe: skipping Mode 22 (bus unhealthy after Mode 01)",
                )
                mode22.map { PidProbeResult(it, false, null, "SKIPPED (bus unhealthy)") }
            }

            val withLive = LiveSnapshotOverlay.apply(probed01 + probed22, _uiState.value.snapshot)
                .toMutableList()
            val battIdx = withLive.indexOfFirst {
                it.pid.request.equals("0142", true) ||
                    it.pid.label.contains("Control module voltage", true)
            }
            val liveBatt = _uiState.value.snapshot.batteryVolts
            if (battIdx >= 0 && liveBatt != null &&
                (!withLive[battIdx].supported || withLive[battIdx].sample == null)
            ) {
                val pid = withLive[battIdx].pid
                withLive[battIdx] = PidProbeResult(pid, true, liveBatt, "ATRV/live")
            }
            ObdLogger.logProbe("Cold start / rough idle", withLive)
            val values = withLive.associate { r ->
                r.pid.id to LiveSnapshotOverlay.formatDisplay(r)
            } + withLive.associate { r ->
                r.pid.label to LiveSnapshotOverlay.formatDisplay(r)
            }
            _idleDiag.value = IdleDiagState(
                loading = false,
                values = values,
                tips = ColdStartIdleCatalog.analyze(withLive),
            )
        }
    }

    fun refreshTransmission() {
        if (!VehicleProfileConfig.showTransmissionPage(vehicleProfile)) {
            _transValues.value = emptyMap()
            return
        }
        val source = currentSource ?: return
        viewModelScope.launch {
            val results = source.probePids(HondaPidCatalog.transmission.pids)
            ObdLogger.logProbe("Transmission", results)
            lastTcmSupportedCount = results.count { it.supported }
            _transValues.value = results.associate { r ->
                r.pid.label to LiveSnapshotOverlay.formatDisplay(r)
            }
            val atf = results.find { it.pid.label.startsWith("ATF") && it.supported }?.sample
            val slip = results.find { it.pid.label.contains("slip", true) && it.supported }?.sample
            lastAtfForVoice = atf
            lastSlipForVoice = slip
            val lockText = results.find { it.pid.label.contains("lock", true) }?.let {
                LiveSnapshotOverlay.formatDisplay(it)
            }
            eventTracker.onSnapshot(_uiState.value.snapshot, _healthThresholds.value, lockText)
            _health.update {
                scoreHealth(atfC = atf, tcSlipRpm = slip)
            }
        }
    }

    fun readVehicleInfo() {
        val source = currentSource ?: return
        viewModelScope.launch {
            _vehicleInfoLoading.value = true
            val info = source.readVehicleInfo()
            _vehicleInfo.value = info
            ObdLogger.logProbeNote(
                "Vehicle info",
                "VIN=${info.vin ?: "—"} ECU=${info.ecuName ?: "—"} " +
                    "CAL=${info.calibrationIds.joinToString("|").ifBlank { "—" }}",
            )
            _vehicleInfoLoading.value = false
        }
    }

    fun scanDeepDiagnostics() {
        val source = currentSource ?: return
        viewModelScope.launch {
            _deepDiag.update { it.copy(loading = true) }
            val readiness = source.readReadiness()
            val freeze = source.readFreezeFrame()
            val o2 = source.readMode05()
            val mode06 = source.readMode06()
            ObdLogger.logProbeNote(
                "Deep diagnostics",
                "MIL=${readiness.milOn} dtcCount=${readiness.dtcCount} " +
                    "freeze=${freeze.dtc ?: "none"} o2=${o2.size} mode06=${mode06.size}",
            )
            freeze.values.forEach { (k, v) ->
                ObdLogger.logProbeNote("Deep diagnostics freeze", "$k=$v")
            }
            _deepDiag.value = DeepDiagState(
                loading = false,
                readiness = readiness,
                freeze = freeze,
                o2 = o2,
                mode06 = mode06,
            )
        }
    }

    fun scanHondaModules() {
        if (!VehicleProfileConfig.showHondaModules(vehicleProfile)) {
            _hondaScan.value = emptyList()
            return
        }
        val source = currentSource ?: return
        viewModelScope.launch {
            _hondaScanning.value = true
            val modules = source.probeHondaModules()
            _hondaScan.value = modules
            ObdLogger.logProbeNote(
                "Honda modules",
                modules.joinToString(" | ") { "${it.module}:${it.supportedCount}/${it.totalCount}" },
            )
            val tcm = modules.find { it.profileId == "honda_tcm" }
            if (tcm != null) {
                lastTcmSupportedCount = tcm.supportedCount
                _health.update {
                    scoreHealth()
                }
            }
            _hondaScanning.value = false
        }
    }

    fun recalcHealth() {
        val ui = _uiState.value
        val decision = ui.decisionSnapshot.takeUnless { it.isEffectivelyBlank() } ?: ui.snapshot
        _health.update {
            HealthScoreCalculator.compute(
                decision,
                _faults.value.stored.size,
                tcmSupportedCount = lastTcmSupportedCount,
                thresholds = _healthThresholds.value,
            )
        }
    }

    private fun scoreHealth(
        snapshot: VehicleSnapshot = _uiState.value.snapshot,
        dtcCount: Int = _faults.value.stored.size,
        atfC: Double? = null,
        tcSlipRpm: Double? = null,
    ): HealthScore = HealthScoreCalculator.compute(
        snapshot = snapshot,
        storedDtcCount = dtcCount,
        atfC = atfC,
        tcSlipRpm = tcSlipRpm,
        tcmSupportedCount = lastTcmSupportedCount,
        thresholds = _healthThresholds.value,
    )

    override fun onCleared() {
        if (_settings.value.valueLogging) {
            stopValueLogging()
        }
        readinessJob?.cancel()
        readinessJob = null
        staleWatchJob?.cancel()
        staleWatchJob = null
        collectJob?.cancel()
        collectJob = null
        currentSource = null
        stopElmMonitor()
        MaintenanceStore(File(filesDir, "maintenance.json")).save(_maintenance.value)
        VehicleLiveStore.onToggleLogging = null
        VehicleLiveStore.onConnectRequest = null
        autoUploadJob?.cancel()
        autoUploadJob = null
        logUploadManager.stop()
        voiceAlerter.shutdown()
        super.onCleared()
    }

    /**
     * When a live poll has filled a field again, drop the sticky deep-search
     * overlay so Classic cannot freeze an old recovered number.
     */
    private fun clearDeepFoundWhenLive(incoming: VehicleSnapshot) {
        if (_deepFoundValues.value.isEmpty()) return
        val drop = mutableSetOf<String>()
        fun dropLabel(label: String, idHint: String) {
            drop += label
            drop += idHint
        }
        if (incoming.coolantC != null) dropLabel("Coolant 1", "0105")
        if (incoming.coolant2C != null) dropLabel("Coolant 2", "0167")
        if (incoming.mafGps != null) dropLabel("MAF", "0110")
        if (incoming.ambientC != null) dropLabel("Ambient", "0146")
        if (incoming.ltftPct != null) dropLabel("LTFT", "0107")
        if (incoming.stftPct != null) dropLabel("STFT", "0106")
        if (incoming.batteryVolts != null) {
            dropLabel("Battery", "0142")
            drop += "ECU V"
        }
        if (incoming.intakeC != null) dropLabel("Intake", "010F")
        if (incoming.mapKpa != null) dropLabel("MAP", "010B")
        if (incoming.timingAdvance != null) dropLabel("Timing", "010E")
        if (drop.isEmpty()) return
        _deepFoundValues.update { cur -> cur.filterKeys { it !in drop } }
        _deepFoundAtMs.update { cur -> cur.filterKeys { it !in drop } }
    }

    /** Re-apply deep-search hits into null snapshot fields until live poll returns. */
    private fun overlayDeepFoundOntoSnapshot(snap: VehicleSnapshot): VehicleSnapshot {
        val deep = _deepFoundValues.value
        if (deep.isEmpty()) return snap
        val hitTimes = _deepFoundAtMs.value
        var out = snap
        fun tryApply(label: String, id: String, missing: Boolean) {
            if (!missing) return
            val text = deep[label] ?: deep[id] ?: return
            val raw = text.substringBefore(" ").toDoubleOrNull() ?: return
            val hitAt = hitTimes[label] ?: hitTimes[id] ?: return
            out = applyDeepSearchHit(out, label, id, raw, hitAt)
        }
        tryApply("Coolant 1", "0105", out.coolantC == null)
        tryApply("Coolant 2", "0167", out.coolant2C == null)
        tryApply("MAF", "0110", out.mafGps == null)
        tryApply("Ambient", "0146", out.ambientC == null)
        tryApply("LTFT", "0107", out.ltftPct == null)
        tryApply("STFT", "0106", out.stftPct == null)
        tryApply("Battery", "0142", out.batteryVolts == null)
        tryApply("Intake", "010F", out.intakeC == null)
        tryApply("MAP", "010B", out.mapKpa == null)
        tryApply("Timing", "010E", out.timingAdvance == null)
        return out
    }

    companion object {
        /** How often the live LOG buffer is flushed to the session CSV on disk. */
        private const val CHECKPOINT_INTERVAL_MS = 60_000L

        /**
         * Map a deep-search hit into live snapshot fields + freshness timestamps
         * so Dash / health / CSV keep the recovered value until the next good poll.
         */
        internal fun applyDeepSearchHit(
            snapshot: VehicleSnapshot,
            label: String,
            targetId: String,
            value: Double,
            nowMs: Long,
        ): VehicleSnapshot {
            val lab = label.lowercase()
            val tid = targetId.lowercase()
            fun withFresh(key: String, block: VehicleSnapshot.() -> VehicleSnapshot): VehicleSnapshot {
                val next = snapshot.block()
                // Preserve original hit time if already stamped (no forever-green on re-apply).
                val fresh = if (next.freshAtMs.containsKey(key)) {
                    next.freshAtMs
                } else {
                    next.freshAtMs + (key to nowMs)
                }
                return next.copy(freshAtMs = fresh)
            }
            return when {
                lab.contains("battery") || lab.contains("ecu v") || tid.contains("0142") ->
                    withFresh(SnapshotFreshness.KEY_BATTERY) { copy(batteryVolts = value) }
                lab.contains("coolant 2") || tid.contains("0167") ->
                    withFresh(SnapshotFreshness.KEY_COOLANT2) { copy(coolant2C = value) }
                lab.contains("coolant") || tid.contains("0105") ->
                    withFresh(SnapshotFreshness.KEY_COOLANT) { copy(coolantC = value) }
                lab.contains("maf") || tid.contains("0110") ->
                    withFresh(SnapshotFreshness.KEY_MAF) { copy(mafGps = value) }
                lab.contains("ambient") || tid.contains("0146") ->
                    withFresh(SnapshotFreshness.KEY_AMBIENT) { copy(ambientC = value) }
                lab.startsWith("ltft") || tid.contains("0107") ->
                    withFresh(SnapshotFreshness.KEY_LTFT) { copy(ltftPct = value) }
                lab.startsWith("stft") || tid.contains("0106") ->
                    withFresh(SnapshotFreshness.KEY_STFT) { copy(stftPct = value) }
                lab.contains("intake") || tid.contains("010f") ->
                    withFresh(SnapshotFreshness.KEY_INTAKE) { copy(intakeC = value) }
                lab.contains("map") || tid.contains("010b") ->
                    withFresh(SnapshotFreshness.KEY_MAP) { copy(mapKpa = value) }
                lab.contains("timing") || tid.contains("010e") ->
                    withFresh(SnapshotFreshness.KEY_TIMING) { copy(timingAdvance = value) }
                else -> snapshot
            }
        }
    }
}
