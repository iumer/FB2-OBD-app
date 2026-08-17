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
import kotlinx.coroutines.flow.emptyFlow

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

    /**
     * Pause continuous Mode 01 polling. Prefer [setPollHold] — a full pause
     * blanks the Dash via freshness TTL. Kept as [PollHold.FULL_PAUSE].
     */
    fun pausePolling() {
        setPollHold(PollHold.FULL_PAUSE)
    }

    /** Resume continuous Mode 01 polling after [pausePolling] / [setPollHold]. */
    fun resumePolling() {
        setPollHold(PollHold.NONE)
    }

    fun setPollHold(hold: PollHold) {}

    fun pollHold(): PollHold = PollHold.NONE

    /**
     * Run [block] without the poll loop sending bytes (ATSH strategies).
     * Poll still emits held last-good values so the Dash does not go n/s.
     */
    suspend fun <T> withLinkExclusive(block: suspend () -> T): T = block()

    /**
     * Keep RPM/Speed/Coolant/MAF/ATRV polling while [block] steals the ELM
     * for Mode 03/07/0A, VIN, freeze frame, or page probes. Nested calls
     * restore the hold that was already in effect (deep search).
     */
    suspend fun <T> withDashKeptAlive(block: suspend () -> T): T {
        val prev = pollHold()
        if (prev == PollHold.NONE) setPollHold(PollHold.HEROES_ONLY)
        return try {
            block()
        } finally {
            if (prev == PollHold.NONE) setPollHold(PollHold.NONE)
        }
    }

    /**
     * Queue catalog PIDs for the live poll loop (1 extra per cycle).
     * Does **not** pause Dash heroes — used by the sensor picker.
     */
    fun enqueueBackgroundProbes(pids: List<PidDefinition>) {}

    fun clearBackgroundProbes() {}

    /** Extra Dash (+) / remap PIDs sampled 1-per-cycle after heroes. */
    fun setKeepaliveProbes(pids: List<PidDefinition>) {}

    fun backgroundProbes(): Flow<PidProbeResult> = emptyFlow()

    /** Probe a list of PIDs; returns support + sample value when possible. */
    suspend fun probePids(
        pids: List<PidDefinition>,
        recoverFirst: Boolean = true,
    ): List<PidProbeResult> = emptyList()

    /** Probe all Honda enhanced packs and return per-module results. */
    suspend fun probeHondaModules(): List<ModuleScanResult> = emptyList()

    /** Read + decode one catalog PID. */
    suspend fun readPid(pid: PidDefinition): Double? = null
}
