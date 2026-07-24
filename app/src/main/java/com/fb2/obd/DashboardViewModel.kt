package com.fb2.obd

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fb2.obd.car.CarDashBuilder
import com.fb2.obd.car.VehicleLiveStore
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.HealthThresholdStore
import com.fb2.obd.data.MaintenanceEntry
import com.fb2.obd.data.MaintenanceStore
import com.fb2.obd.data.ObdLogger
import com.fb2.obd.data.ObdSource
import com.fb2.obd.data.SavedLogFile
import com.fb2.obd.data.SessionLogStore
import com.fb2.obd.data.VoiceAlerter
import com.fb2.obd.obd.ColdStartIdleCatalog
import com.fb2.obd.obd.DeepSearchReport
import com.fb2.obd.obd.DeepSensorSearch
import com.fb2.obd.obd.DiagnosticEventTracker
import com.fb2.obd.obd.Dtc
import com.fb2.obd.obd.FreezeFrame
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.HealthScoreCalculator
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.HondaPidCatalog
import com.fb2.obd.obd.LiveSnapshotOverlay
import com.fb2.obd.obd.Mode06Result
import com.fb2.obd.obd.ModuleScanResult
import com.fb2.obd.obd.O2TestResult
import com.fb2.obd.obd.PidCategory
import com.fb2.obd.obd.PidDefinition
import com.fb2.obd.obd.ReadinessStatus
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.TripComputer
import com.fb2.obd.obd.VehicleInfo
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.obd.isEffectivelyBlank
import com.fb2.obd.obd.withField
import com.fb2.obd.perf.AccelResult
import com.fb2.obd.perf.AccelerationTimer
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
)

/** Diagnostic trouble code state for the Faults screen. */
data class FaultsState(
    val loading: Boolean = false,
    val stored: List<Dtc> = emptyList(),
    val pending: List<Dtc> = emptyList(),
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

    private val thresholdStore = HealthThresholdStore(File(filesDir, "health_thresholds.json"))
    private val _healthThresholds = MutableStateFlow(HealthThresholds.DEFAULT)
    val healthThresholds: StateFlow<HealthThresholds> = _healthThresholds.asStateFlow()

    private val voiceAlerter = VoiceAlerter(app)
    private var lastAtfForVoice: Double? = null
    private var lastSlipForVoice: Double? = null
    private var lastLiveSnapshotMs: Long = 0L
    private var staleWatchJob: Job? = null

    private val _hondaScan = MutableStateFlow<List<ModuleScanResult>>(emptyList())
    val hondaScan: StateFlow<List<ModuleScanResult>> = _hondaScan.asStateFlow()

    private val _hondaScanning = MutableStateFlow(false)
    val hondaScanning: StateFlow<Boolean> = _hondaScanning.asStateFlow()

    private val _maintenance = MutableStateFlow(MaintenanceStore.defaultTemplate())
    val maintenance: StateFlow<List<MaintenanceEntry>> = _maintenance.asStateFlow()

    private val _dashExtraPidIds = MutableStateFlow<List<String>>(emptyList())
    val dashExtraPidIds: StateFlow<List<String>> = _dashExtraPidIds.asStateFlow()

    private val _dashExtraValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val dashExtraValues: StateFlow<Map<String, String>> = _dashExtraValues.asStateFlow()

    private val sessionLogStore = SessionLogStore(File(filesDir, "value_logs"))
    private val _savedLogs = MutableStateFlow(sessionLogStore.list())
    val savedLogs: StateFlow<List<SavedLogFile>> = _savedLogs.asStateFlow()
    private var sessionStartedMs: Long = 0L

    private val _deepSearch = MutableStateFlow(DeepSearchUiState())
    val deepSearch: StateFlow<DeepSearchUiState> = _deepSearch.asStateFlow()

    /** Values recovered by deep search, keyed by tile label / pid id. */
    private val _deepFoundValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val deepFoundValues: StateFlow<Map<String, String>> = _deepFoundValues.asStateFlow()

    val pidCatalog: List<PidDefinition> =
        StandardPidCatalog.all + HondaPidCatalog.allPids

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
            ) { i, total, title ->
                _deepSearch.update { st ->
                    st.copy(progress = "Trying $i / $total — $title")
                }
            }
            if (report.success) {
                val hit = report.hit!!
                val text = "%.2f %s".format(hit.value, hit.strategy.unit).trim()
                _deepFoundValues.update {
                    it + (label to text) + (report.targetId to text)
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
        // One-shot probe so tiles outside the main poll set still show a value.
        val source = currentSource ?: return
        viewModelScope.launch {
            val probed = source.probePids(listOf(pid))
            val results = LiveSnapshotOverlay.apply(probed, _uiState.value.snapshot)
            val text = results.firstOrNull()?.let { LiveSnapshotOverlay.formatDisplay(it) } ?: "—"
            _dashExtraValues.update { it + (pid.id to text) }
            publishCarDash()
        }
    }

    fun clearDashExtraPid(pidId: String) {
        _dashExtraPidIds.update { it.filter { id -> id != pidId } }
        _dashExtraValues.update { it - pidId }
        publishCarDash()
    }

    private val accelTimer = AccelerationTimer()
    private val tripComputer = TripComputer()
    private var collectJob: Job? = null
    private var loggingJob: Job? = null
    private var readinessJob: Job? = null
    /** Throttle Dash CSV samples (~1 Hz). */
    private var lastDashLogMs: Long = 0L
    private var currentSource: ObdSource? = null
    /** Last TCM probe hit count — used so Health doesn't claim 100% with zero TCM data. */
    private var lastTcmSupportedCount: Int = 0
    private var phoneAx = 0f
    private var phoneAy = 0f
    private var phoneAz = 9.81f
    private val eventTracker = DiagnosticEventTracker()

    init {
        ObdLogger.valueLoggingEnabled = false
        tripComputer.fuelPricePerLiter = _settings.value.fuelPricePerLiter
        _maintenance.value = MaintenanceStore(File(filesDir, "maintenance.json")).load()
        _healthThresholds.value = thresholdStore.load()
        voiceAlerter.enabled = _settings.value.voiceAlerts
        voiceAlerter.start()
        VehicleLiveStore.onToggleLogging = {
            if (_settings.value.valueLogging) stopValueLogging() else startValueLogging()
            publishCarDash()
        }
        VehicleLiveStore.onConnectRequest = {
            // Car cannot pick BT devices — start Demo if offline; live ELM must be chosen on phone.
            if (!_uiState.value.sourceIsLive) {
                useSource(DemoObdSource())
            }
            publishCarDash()
        }
        _custom.update {
            it.copy(selectedIds = StandardPidCatalog.fuelPageDefaults().map { p -> p.id }.toSet())
        }
        useSource(DemoObdSource())
        publishCarDash()
    }

    /** Push the phone main Dash (tiles + hero) to Android Auto. */
    fun publishCarDash() {
        val ui = _uiState.value
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
            ),
        )
    }

    fun updateHealthThresholdField(id: String, value: Double) {
        val next = _healthThresholds.value.withField(id, value)
        _healthThresholds.value = next
        thresholdStore.save(next)
        recalcHealth()
    }

    fun resetHealthThresholds() {
        _healthThresholds.value = HealthThresholds.DEFAULT
        thresholdStore.save(HealthThresholds.DEFAULT)
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
        ObdLogger.sessionStartMs = sessionStartedMs
        ObdLogger.valueLoggingEnabled = true
        _settings.update { it.copy(valueLogging = true) }
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "VALUE LOG session start — main Dash only")
        lastDashLogMs = 0L
        logDashValues(_uiState.value.snapshot, force = true)
        // Lightly refresh user-added Dash (+) tiles so extras stay current in the CSV.
        loggingJob = viewModelScope.launch {
            while (isActive && ObdLogger.valueLoggingEnabled) {
                refreshDashExtrasForLog()
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
        // Final snapshot while logging is still enabled.
        logDashValues(_uiState.value.snapshot, force = true)
        ObdLogger.valueLoggingEnabled = false
        _settings.update { it.copy(valueLogging = false) }
        val empty = ObdLogger.valueRows().isEmpty() &&
            ObdLogger.tabValueRows().none { it.tab.equals("Dash", true) }
        if (!wasOn && empty) {
            return null
        }
        val csv = ObdLogger.valuesCsv()
        val started = if (sessionStartedMs > 0L) sessionStartedMs else System.currentTimeMillis()
        val saved = sessionLogStore.saveSession(csv, started)
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "VALUE LOG saved ${saved.fileName}")
        ObdLogger.sessionStartMs = 0L
        _savedLogs.value = sessionLogStore.list()
        return saved
    }

    /** Re-probe only the sensors the user added with + on the main Dash. */
    private suspend fun refreshDashExtrasForLog() {
        val source = currentSource ?: return
        val ids = _dashExtraPidIds.value
        if (ids.isEmpty()) return
        val pids = ids.mapNotNull { id -> pidCatalog.find { it.id.equals(id, true) } }
        if (pids.isEmpty()) return
        val results = LiveSnapshotOverlay.apply(source.probePids(pids), _uiState.value.snapshot)
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
    }

    fun setShowEstimatedGear(enabled: Boolean) {
        _settings.update { it.copy(showEstimatedGear = enabled) }
    }

    fun setVoiceAlerts(enabled: Boolean) {
        _settings.update { it.copy(voiceAlerts = enabled) }
        voiceAlerter.enabled = enabled
        if (enabled) {
            voiceAlerter.start()
            voiceAlerter.speakTest("Voice alerts on")
        }
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
        eventTracker.reset()
        _faults.update { FaultsState() }
        _uiState.update {
            it.copy(
                connection = ConnectionState.CONNECTING,
                sourceName = source.name,
                sourceIsLive = source.isLive,
                dtcCount = null,
                reconnecting = false,
                lastError = null,
            )
        }
        publishCarDash()
        collectJob = source.snapshots()
            .onEach { incoming ->
                lastLiveSnapshotMs = System.currentTimeMillis()
                val prev = _uiState.value.snapshot
                // Never wipe a live Dash with a blank reconnect frame.
                val snapshot = if (incoming.isEffectivelyBlank() && !prev.isEffectivelyBlank()) {
                    prev
                } else {
                    incoming
                }
                ObdLogger.logSnapshot(incoming)
                val now = System.currentTimeMillis()
                snapshot.speedKmh?.let { accelTimer.onSample(now, it) }
                tripComputer.onSample(now, snapshot.speedKmh, snapshot.mafGps, null)
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
                    scoreHealth(snapshot = snapshot)
                }
                voiceAlerter.onSnapshot(
                    snapshot = snapshot,
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
                publishCarDash()
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

    fun readFaults() {
        val source = currentSource ?: return
        viewModelScope.launch {
            _faults.update { it.copy(loading = true, message = null) }
            val stored = source.readStoredDtcs()
            val pending = source.readPendingDtcs()
            _faults.update {
                it.copy(
                    loading = false,
                    stored = stored,
                    pending = pending,
                    hasRead = true,
                    message = if (stored.isEmpty() && pending.isEmpty()) "No fault codes found." else null,
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
            _faults.update {
                it.copy(
                    loading = false,
                    stored = stored,
                    pending = pending,
                    hasRead = true,
                    message = if (ok) "Codes cleared." else "Clear failed or not supported.",
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
                HondaPidCatalog.engine.pids.filter { it.label.contains("Injector", true) } +
                HondaPidCatalog.engine.pids.filter { it.label.contains("Fuel", true) }
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
            val probed = source.probePids(ColdStartIdleCatalog.allPids)
            val results = LiveSnapshotOverlay.apply(probed, _uiState.value.snapshot)
            ObdLogger.logProbe("Cold start / rough idle", results)
            val values = results.associate { r ->
                r.pid.id to LiveSnapshotOverlay.formatDisplay(r)
            } + results.associate { r ->
                r.pid.label to LiveSnapshotOverlay.formatDisplay(r)
            }
            _idleDiag.value = IdleDiagState(
                loading = false,
                values = values,
                tips = ColdStartIdleCatalog.analyze(results),
            )
        }
    }

    fun refreshTransmission() {
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
        _health.update {
            HealthScoreCalculator.compute(
                _uiState.value.snapshot,
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
        MaintenanceStore(File(filesDir, "maintenance.json")).save(_maintenance.value)
        VehicleLiveStore.onToggleLogging = null
        VehicleLiveStore.onConnectRequest = null
        voiceAlerter.shutdown()
        super.onCleared()
    }
}
