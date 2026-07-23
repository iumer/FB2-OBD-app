package com.fb2.obd.data

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

    /** Emits a decoded snapshot on every poll cycle. */
    fun snapshots(): Flow<VehicleSnapshot>
}
