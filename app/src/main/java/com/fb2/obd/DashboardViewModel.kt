package com.fb2.obd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.ObdLogger
import com.fb2.obd.data.ObdSource
import com.fb2.obd.obd.Dtc
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
)

/**
 * Collects snapshots from the active [ObdSource] and exposes them as UI state.
 * Defaults to the simulated demo feed so the dashboard is fully usable without an
 * adapter; call [useSource] to switch to a real ELM327 connection.
 */
class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(SettingsState())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    private val _faults = MutableStateFlow(FaultsState())
    val faults: StateFlow<FaultsState> = _faults.asStateFlow()

    private val _performance = MutableStateFlow(PerformanceState())
    val performance: StateFlow<PerformanceState> = _performance.asStateFlow()

    private val accelTimer = AccelerationTimer()

    private var collectJob: Job? = null
    private var currentSource: ObdSource? = null

    init {
        ObdLogger.valueLoggingEnabled = _settings.value.valueLogging
        useSource(DemoObdSource())
    }

    fun setValueLogging(enabled: Boolean) {
        _settings.update { it.copy(valueLogging = enabled) }
        ObdLogger.valueLoggingEnabled = enabled
    }

    fun setShowEstimatedGear(enabled: Boolean) {
        _settings.update { it.copy(showEstimatedGear = enabled) }
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
                snapshot.speedKmh?.let { accelTimer.onSample(System.currentTimeMillis(), it) }
                _performance.update {
                    it.copy(
                        current = accelTimer.current,
                        best = accelTimer.best,
                        currentSpeedKmh = snapshot.speedKmh,
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
        _performance.update { it.copy(current = accelTimer.current, best = accelTimer.best) }
    }

    override fun onCleared() {
        super.onCleared()
        collectJob?.cancel()
    }
}
