package com.fb2.obd

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.MaintenanceEntry
import com.fb2.obd.data.MaintenanceStore
import com.fb2.obd.data.ObdLogger
import com.fb2.obd.data.ObdSource
import com.fb2.obd.data.SavedLogFile
import com.fb2.obd.data.SessionLogStore
import com.fb2.obd.obd.ColdStartIdleCatalog
import com.fb2.obd.obd.Dtc
import com.fb2.obd.obd.FreezeFrame
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.HealthScoreCalculator
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
import com.fb2.obd.perf.AccelResult
import com.fb2.obd.perf.AccelerationTimer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import java.io.File

/** UI state for the dashboard screen. */
data class DashboardUiState(
    val snapshot: VehicleSnapshot = VehicleSnapshot.EMPTY,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val sourceName: String = "",
    val sourceIsLive: Boolean = false,
)

/** User-adjustable settings. */
data class SettingsState(
    val valueLogging: Boolean = false,
    val showEstimatedGear: Boolean = true,
    val fuelPricePerLiter: Double = 280.0,
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

    val pidCatalog: List<PidDefinition> =
        StandardPidCatalog.all + HondaPidCatalog.allPids

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
        }
    }

    fun clearDashExtraPid(pidId: String) {
        _dashExtraPidIds.update { it.filter { id -> id != pidId } }
        _dashExtraValues.update { it - pidId }
    }

    private val accelTimer = AccelerationTimer()
    private val tripComputer = TripComputer()
    private var collectJob: Job? = null
    private var currentSource: ObdSource? = null
    /** Last TCM probe hit count — used so Health doesn't claim 100% with zero TCM data. */
    private var lastTcmSupportedCount: Int = 0

    init {
        ObdLogger.valueLoggingEnabled = false
        tripComputer.fuelPricePerLiter = _settings.value.fuelPricePerLiter
        _maintenance.value = MaintenanceStore(File(filesDir, "maintenance.json")).load()
        _custom.update {
            it.copy(selectedIds = StandardPidCatalog.fuelPageDefaults().map { p -> p.id }.toSet())
        }
        useSource(DemoObdSource())
    }

    /** Settings toggle and dashboard LOG button both use session start/stop. */
    fun setValueLogging(enabled: Boolean) {
        if (enabled) startValueLogging() else stopValueLogging()
    }

    fun toggleValueLogging() {
        setValueLogging(!_settings.value.valueLogging)
    }

    /** Begin a fresh in-memory session (does not erase previously saved CSV files). */
    fun startValueLogging() {
        ObdLogger.clearValues()
        sessionStartedMs = System.currentTimeMillis()
        ObdLogger.valueLoggingEnabled = true
        _settings.update { it.copy(valueLogging = true) }
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "VALUE LOG session start")
    }

    /**
     * Stop logging and persist the current buffer as its own timestamped CSV
     * under app files (`value_logs/`). Returns the saved file, or null if empty.
     */
    fun stopValueLogging(): SavedLogFile? {
        val wasOn = ObdLogger.valueLoggingEnabled
        ObdLogger.valueLoggingEnabled = false
        _settings.update { it.copy(valueLogging = false) }
        if (!wasOn && ObdLogger.valueRows().isEmpty() && ObdLogger.probeRows().isEmpty()) {
            return null
        }
        val csv = ObdLogger.valuesCsv()
        val started = if (sessionStartedMs > 0L) sessionStartedMs else System.currentTimeMillis()
        val saved = sessionLogStore.saveSession(csv, started)
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "VALUE LOG saved ${saved.fileName}")
        _savedLogs.value = sessionLogStore.list()
        return saved
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

    /** Cancel the active OBD source (closes ELM socket) and mark offline. */
    fun disconnect() {
        if (_settings.value.valueLogging) {
            stopValueLogging()
        }
        collectJob?.cancel()
        collectJob = null
        currentSource = null
        _uiState.update {
            it.copy(
                snapshot = VehicleSnapshot.EMPTY,
                connection = ConnectionState.DISCONNECTED,
                sourceName = "",
                sourceIsLive = false,
            )
        }
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "Disconnected")
    }

    fun useSource(source: ObdSource) {
        collectJob?.cancel()
        currentSource = source
        _faults.update { FaultsState() }
        _uiState.update {
            it.copy(
                connection = ConnectionState.CONNECTING,
                sourceName = source.name,
                sourceIsLive = source.isLive,
            )
        }
        collectJob = source.snapshots()
            .onEach { snapshot ->
                ObdLogger.logSnapshot(snapshot)
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
                    HealthScoreCalculator.compute(
                        snapshot,
                        _faults.value.stored.size,
                        tcmSupportedCount = lastTcmSupportedCount,
                    )
                }
                _uiState.update {
                    it.copy(
                        snapshot = snapshot,
                        connection = ConnectionState.CONNECTED,
                    )
                }
            }
            .catch {
                _uiState.update { it.copy(connection = ConnectionState.ERROR) }
            }
            .launchIn(viewModelScope)
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
            _health.update {
                HealthScoreCalculator.compute(
                    _uiState.value.snapshot,
                    stored.size,
                    tcmSupportedCount = lastTcmSupportedCount,
                )
            }
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
            _health.update {
                HealthScoreCalculator.compute(
                    _uiState.value.snapshot,
                    _faults.value.stored.size,
                    atf,
                    slip,
                    tcmSupportedCount = lastTcmSupportedCount,
                )
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
                    HealthScoreCalculator.compute(
                        _uiState.value.snapshot,
                        _faults.value.stored.size,
                        tcmSupportedCount = lastTcmSupportedCount,
                    )
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
            )
        }
    }

    override fun onCleared() {
        if (_settings.value.valueLogging) {
            stopValueLogging()
        }
        collectJob?.cancel()
        collectJob = null
        currentSource = null
        MaintenanceStore(File(filesDir, "maintenance.json")).save(_maintenance.value)
        super.onCleared()
    }
}
