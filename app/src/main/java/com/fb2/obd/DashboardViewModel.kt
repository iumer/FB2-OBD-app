package com.fb2.obd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fb2.obd.data.ConnectionState
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.ObdSource
import com.fb2.obd.obd.VehicleSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

/** UI state for the dashboard screen. */
data class DashboardUiState(
    val snapshot: VehicleSnapshot = VehicleSnapshot.EMPTY,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val sourceName: String = "",
)

/**
 * Collects snapshots from the active [ObdSource] and exposes them as UI state.
 * Defaults to the simulated demo feed so the dashboard is fully usable without an
 * adapter; call [useSource] to switch to a real ELM327 connection.
 */
class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    init {
        useSource(DemoObdSource())
    }

    fun useSource(source: ObdSource) {
        collectJob?.cancel()
        _uiState.value = _uiState.value.copy(
            connection = ConnectionState.CONNECTING,
            sourceName = source.name,
        )
        collectJob = source.snapshots()
            .onEach { snapshot ->
                _uiState.value = _uiState.value.copy(
                    snapshot = snapshot,
                    connection = ConnectionState.CONNECTED,
                )
            }
            .catch {
                _uiState.value = _uiState.value.copy(connection = ConnectionState.ERROR)
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        collectJob?.cancel()
    }
}
