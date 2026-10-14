package com.fb2.obd.data

import com.fb2.obd.obd.Dtc
import com.fb2.obd.obd.FreezeFrame
import com.fb2.obd.obd.Mode06Result
import com.fb2.obd.obd.ModuleScanResult
import com.fb2.obd.obd.O2TestResult
import com.fb2.obd.obd.PidDefinition
import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.PollHold
import com.fb2.obd.obd.ReadinessStatus
import com.fb2.obd.obd.VehicleInfo
import com.fb2.obd.obd.VehicleSnapshot
import kotlinx.coroutines.flow.Flow

/** Connection state surfaced to the UI. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * A source of live vehicle data. Implementations may be a simulated demo feed or
 * a real ELM327 Bluetooth adapter.
 */
interface ObdSource {
    val name: String
    val isLive: Boolean
    fun snapshots(): Flow<VehicleSnapshot>

    suspend fun readStoredDtcs(): List<Dtc> = emptyList()
    suspend fun readPendingDtcs(): List<Dtc> = emptyList()
    /** Mode 0A permanent DTCs (not cleared by Mode 04 on many ECUs). */
    suspend fun readPermanentDtcs(): List<Dtc> = emptyList()
    suspend fun clearDtcs(): Boolean = false
    suspend fun command(raw: String): String? = null

    suspend fun readVehicleInfo(): VehicleInfo = VehicleInfo()
    suspend fun readReadiness(): ReadinessStatus = ReadinessStatus()
    suspend fun readFreezeFrame(): FreezeFrame = FreezeFrame()
    suspend fun readMode05(): List<O2TestResult> = emptyList()
    suspend fun readMode06(): List<Mode06Result> = emptyList()

    /** Pause continuous Mode 01 polling — prefer [setPollHold]. Maps to [PollHold.FULL_PAUSE]. */
    fun pausePolling() {
        setPollHold(PollHold.FULL_PAUSE)
    }

    /** Resume continuous Mode 01 polling after [pausePolling] / [setPollHold]. */
    fun resumePolling() {
        setPollHold(PollHold.NONE)
    }

    fun setPollHold(hold: PollHold) {}

    fun pollHold(): PollHold = PollHold.NONE

    /** Run [block] without the poll loop sending bytes (one ATSH strategy). */
    suspend fun <T> withLinkExclusive(block: suspend () -> T): T = block()

    /**
     * Mode 01 PIDs advertised by the ECU support bitmask at connect
     * (`0100`/`0120`/…). Empty until the first successful bitmask probe.
     * Picker scan uses this instead of re-sending bitmask commands (those
     * starve Dash heroes on cheap clones).
     */
    fun advertisedMode01(): Set<Int> = emptySet()

    /**
     * Probe a list of PIDs; returns support + sample value when possible.
     * [retryNoData] is for idle/fuel pages; picker scan sets it false so a
     * missing PID does not hold the ELM mutex for a second 450 ms timeout.
     */
    suspend fun probePids(
        pids: List<PidDefinition>,
        recoverFirst: Boolean = true,
        retryNoData: Boolean = true,
    ): List<PidProbeResult> = emptyList()

    /** Probe all Honda enhanced packs and return per-module results. */
    suspend fun probeHondaModules(): List<ModuleScanResult> = emptyList()

    /** Read + decode one catalog PID. */
    suspend fun readPid(pid: PidDefinition): Double? = null
}
