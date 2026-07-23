package com.fb2.obd.data

import com.fb2.obd.obd.Dtc
import com.fb2.obd.obd.VehicleSnapshot
import kotlinx.coroutines.flow.Flow

/** Connection state surfaced to the UI. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * A source of live vehicle data. Implementations may be a simulated demo feed or
 * a real ELM327 Bluetooth adapter. The dashboard only depends on this interface.
 */
interface ObdSource {
    val name: String

    /** True for a real vehicle connection; false for the simulated demo feed. */
    val isLive: Boolean

    /** Emits a decoded snapshot on every poll cycle. */
    fun snapshots(): Flow<VehicleSnapshot>

    /** Mode 03 stored diagnostic trouble codes. */
    suspend fun readStoredDtcs(): List<Dtc> = emptyList()

    /** Mode 07 pending diagnostic trouble codes. */
    suspend fun readPendingDtcs(): List<Dtc> = emptyList()

    /** Mode 04 clear DTCs + freeze frame. Returns true on a positive response. */
    suspend fun clearDtcs(): Boolean = false
}
