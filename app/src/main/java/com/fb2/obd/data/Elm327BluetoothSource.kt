package com.fb2.obd.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.fb2.obd.obd.DiagnosticParsers
import com.fb2.obd.obd.Dtc
import com.fb2.obd.obd.DtcDecoder
import com.fb2.obd.obd.FreezeFrame
import com.fb2.obd.obd.FuelSystemDecoder
import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.HondaPidCatalog
import com.fb2.obd.obd.Mode06Result
import com.fb2.obd.obd.ModuleScanResult
import com.fb2.obd.obd.O2TestResult
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.ObdResponseParser
import com.fb2.obd.obd.PidDefinition
import com.fb2.obd.obd.PidPollPlanner
import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.ReadinessStatus
import com.fb2.obd.obd.SnapshotFreshness
import com.fb2.obd.obd.SupportedPids
import com.fb2.obd.obd.VehicleInfo
import com.fb2.obd.obd.VehicleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * Real ELM327 adapter over Bluetooth Classic (SPP), built on a shared
 * [Elm327Connection] so the continuous PID polling and one-off commands (DTC
 * read/clear) safely share one serial stream.
 *
 * On connect it runs the init sequence, probes supported PIDs, then round-robins
 * the supported dashboard PIDs. All raw traffic goes to [ObdLogger].
 *
 * Battery: many FB2 ECUs omit PID 0142 from the support bitmask (Torque still
 * shows volts via ATRV). We always poll ATRV and force-try 0142.
 */
class Elm327BluetoothSource(
    private val device: BluetoothDevice,
    private val gearEstimator: GearEstimator = GearEstimator(),
    private val logger: ObdLogger = ObdLogger,
) : ObdSource {

    override val name: String = "ELM327 (Bluetooth)"
    override val isLive: Boolean = true

    val deviceAddress: String get() = device.address

    @get:SuppressLint("MissingPermission")
    val deviceName: String? get() = runCatching { device.name }.getOrNull()

    @Volatile
    private var connection: Elm327Connection? = null

    /** When true, the Mode 01 poll loop yields so deep search owns the serial link. */
    @Volatile
    private var pollingPaused: Boolean = false

    /** After deep search, force a soft recover + clear fail streaks on resume. */
    @Volatile
    private var recoverAfterResume: Boolean = false

    /** Rolling ATRV samples for median filter (cheap clones spit occasional false lows). */
    private val atrvWindow = ArrayDeque<Double>(3)

    override fun pausePolling() {
        pollingPaused = true
        logger.logDebug(ObdLogger.Dir.INFO, "ELM poll paused")
    }

    override fun resumePolling() {
        pollingPaused = false
        recoverAfterResume = true
        logger.logDebug(ObdLogger.Dir.INFO, "ELM poll resumed (recover pending)")
    }

    private val polled = listOf(
        ObdPid.ENGINE_RPM,
        ObdPid.SPEED,
        ObdPid.COOLANT_TEMP,
        ObdPid.COOLANT_TEMP_2,
        ObdPid.INTAKE_TEMP,
        ObdPid.ENGINE_LOAD,
        ObdPid.THROTTLE,
        ObdPid.TIMING_ADVANCE,
        ObdPid.MAF,
        ObdPid.INTAKE_MAP,
        ObdPid.STFT_B1,
        ObdPid.LTFT_B1,
        ObdPid.FUEL_SYSTEM_STATUS,
        ObdPid.CONTROL_MODULE_VOLTAGE,
        ObdPid.AMBIENT_TEMP,
    )

    /** Always try these even if the support bitmask omits them. */
    private val forcePoll = setOf(
        ObdPid.CONTROL_MODULE_VOLTAGE.number,
        ObdPid.MAF.number,
        ObdPid.ENGINE_RPM.number,
        ObdPid.SPEED.number,
        ObdPid.COOLANT_TEMP.number,
    )

    @SuppressLint("MissingPermission")
    override fun snapshots(): Flow<VehicleSnapshot> = channelFlow {
        runCatching { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() }

        logger.logDebug(ObdLogger.Dir.INFO, "Connecting to ${device.address}")
        val socket = openSocket()
        val conn = Elm327Connection(socket, logger)
        connection = conn

        val reader = launch(Dispatchers.IO) {
            try {
                runInit(conn)

                val supported = probeSupportedPids(conn)
                val basePids = if (supported.isEmpty()) {
                    polled
                } else {
                    polled.filter { it.number in supported || it.number in forcePoll }
                }
                val hasEcuGear = supported.isEmpty() ||
                    ObdPid.TRANSMISSION_GEAR_RATIO.number in supported
                val activePids = if (hasEcuGear) basePids + ObdPid.TRANSMISSION_GEAR_RATIO else basePids
                // Don't mark forced PIDs as unsupported — ATRV/0142 may still work.
                val unsupported = if (supported.isEmpty()) {
                    emptySet()
                } else {
                    polled.map { it.number }
                        .filter { it !in supported && it !in forcePoll }
                        .toSet()
                }

                logger.logDebug(
                    ObdLogger.Dir.INFO,
                    "Polling ${activePids.size} PIDs; ECU gear=$hasEcuGear; ATRV battery fallback on",
                )

                var snapshot = VehicleSnapshot.EMPTY.copy(unsupportedPids = unsupported)
                var deadCycles = 0
                val failStreak = mutableMapOf<ObdPid, Int>()
                val freshness = SnapshotFreshness()
                var cycles = 0
                while (isActive) {
                    // Deep search / exclusive probes own the RFCOMM socket — do not
                    // interleave Mode 01 polls (that caused laggy/wrong Dash values).
                    while (pollingPaused && isActive) {
                        delay(40L)
                    }
                    if (!isActive) break

                    if (recoverAfterResume) {
                        recoverAfterResume = false
                        softRecover(conn)
                        failStreak.clear()
                        deadCycles = 0
                        // Deep search paused polling — prior lastOk clocks aged out.
                        // Remake marks for fields we still hold so sanitize does not
                        // blank the Dash before the first post-resume poll lands.
                        freshness.remakePresent(snapshot, System.currentTimeMillis())
                        logger.logDebug(ObdLogger.Dir.INFO, "post-deep-search soft recover + streaks cleared + freshness remade")
                    }

                    cycles++
                    var responded = 0
                    var timedOut = 0
                    var unable = 0
                    var busLost = false
                    var rpmUpdated = false
                    var mode01Ok = false
                    val markedThisCycle = mutableSetOf<String>()

                    // ATRV every cycle — adapter-local, cheap, Torque-style battery source.
                    var atrvThisCycle: Double? = null
                    readAtrv(conn)?.let { v ->
                        responded++
                        atrvThisCycle = v
                        snapshot = snapshot.copy(batteryVolts = v)
                        val okAt = System.currentTimeMillis()
                        freshness.markOk(SnapshotFreshness.KEY_BATTERY, okAt)
                        markedThisCycle += SnapshotFreshness.KEY_BATTERY
                    }

                    val recovering = deadCycles > 0
                    // Heroes every cycle; rotate secondaries so Speed cannot freeze behind a long PID list.
                    val cyclePids = PidPollPlanner.selectForCycle(
                        activePids = activePids,
                        failStreak = failStreak,
                        cycle = cycles,
                        recovering = recovering,
                    )
                    for (pid in cyclePids) {
                        if (busLost) break

                        val streak = failStreak[pid] ?: 0
                        val raw = try {
                            conn.exec(pid.request)
                        } catch (io: IOException) {
                            timedOut++
                            failStreak[pid] = streak + 1
                            logger.logDebug(
                                ObdLogger.Dir.INFO,
                                "skip ${pid.request}: ${io.message} (fail#${failStreak[pid]})",
                            )
                            continue
                        }

                        if (isUnable(raw)) {
                            unable++
                            failStreak[pid] = streak + 1
                            // ECU link gone — stop burning timeouts on the rest of the cycle.
                            if (unable >= 2) {
                                busLost = true
                                logger.logDebug(
                                    ObdLogger.Dir.INFO,
                                    "bus lost (UNABLE) — soft recover",
                                )
                            }
                            continue
                        }

                        val value = ObdResponseParser.parse(pid, raw)
                        if (value != null) {
                            responded++
                            mode01Ok = true
                            failStreak[pid] = 0
                            val okAt = System.currentTimeMillis()
                            val key = SnapshotFreshness.keyFor(pid)
                            // Do not let 0142 overwrite a fresh ATRV reading this cycle.
                            if (pid == ObdPid.CONTROL_MODULE_VOLTAGE && atrvThisCycle != null) {
                                freshness.markPid(pid, okAt)
                                markedThisCycle += key
                            } else {
                                snapshot = snapshot.merge(pid, value)
                                freshness.markPid(pid, okAt)
                                markedThisCycle += key
                            }
                            if (pid == ObdPid.ENGINE_RPM) rpmUpdated = true
                        } else {
                            // Frame arrived but didn't decode — still counts as link alive.
                            responded++
                            mode01Ok = true
                            // Heroes keep retrying next cycle; cap streak so planner never skips them.
                            failStreak[pid] = if (PidPollPlanner.isAlways(pid)) {
                                (streak + 1).coerceAtMost(1)
                            } else {
                                (streak + 1).coerceAtMost(2)
                            }
                        }
                    }

                    // Extra ATRV refresh if volts still missing after Mode 01.
                    val voltFail = failStreak[ObdPid.CONTROL_MODULE_VOLTAGE] ?: 0
                    if (atrvThisCycle == null && (snapshot.batteryVolts == null || voltFail >= 2)) {
                        readAtrv(conn)?.let { v ->
                            responded++
                            atrvThisCycle = v
                            snapshot = snapshot.copy(batteryVolts = v)
                            val okAt = System.currentTimeMillis()
                            freshness.markOk(SnapshotFreshness.KEY_BATTERY, okAt)
                            markedThisCycle += SnapshotFreshness.KEY_BATTERY
                        }
                    }

                    if (busLost) {
                        softRecover(conn)
                        deadCycles++
                        logger.logDebug(
                            ObdLogger.Dir.INFO,
                            "soft recover #$deadCycles (unable=$unable)",
                        )
                        if (deadCycles >= MAX_SOFT_RECOVER_BEFORE_RECONNECT) {
                            throw IOException(
                                "ECU bus lost after $deadCycles soft recovers — reconnecting",
                            )
                        }
                    } else if (!mode01Ok) {
                        // ATRV-only or total silence — do not treat as a healthy Dash cycle.
                        deadCycles++
                        logger.logDebug(
                            ObdLogger.Dir.INFO,
                            "dead cycle $deadCycles (timeouts=$timedOut unable=$unable/" +
                                "${activePids.size} atrvOnly=${responded > 0})",
                        )
                        if (deadCycles >= 4) {
                            throw IOException("No ECU Mode 01 response after $deadCycles dead cycles")
                        }
                    } else {
                        deadCycles = 0
                    }

                    // Anything decoded this cycle must survive sanitize even if the
                    // cycle itself ran longer than that field's TTL.
                    val sanitizeAt = System.currentTimeMillis()
                    freshness.restamp(markedThisCycle, sanitizeAt)
                    snapshot = freshness.sanitize(
                        snapshot.withGear(hasEcuGear),
                        sanitizeAt,
                        rpmUpdatedThisCycle = rpmUpdated,
                    )
                    val hasAny = snapshot.rpm != null || snapshot.coolantC != null ||
                        snapshot.batteryVolts != null || snapshot.mafGps != null ||
                        snapshot.speedKmh != null
                    if (hasAny || deadCycles == 0) {
                        trySend(snapshot)
                    }
                }
            } catch (e: Exception) {
                logger.logDebug(ObdLogger.Dir.INFO, "ELM poll stopped: ${e.message}")
                close(e)
            }
        }

        awaitClose {
            reader.cancel()
            connection = null
            conn.close()
        }
    }
        .retryWhen { cause, attempt ->
            if (cause is IOException) {
                val backoff = (1_000L * (attempt + 1).coerceAtMost(8)).coerceAtMost(10_000L)
                logger.logDebug(
                    ObdLogger.Dir.INFO,
                    "reconnect attempt ${attempt + 1} in ${backoff}ms (${cause.message})",
                )
                delay(backoff)
                true
            } else {
                logger.logDebug(ObdLogger.Dir.INFO, "reconnect aborted: ${cause.message}")
                false
            }
        }
        .flowOn(Dispatchers.IO)

    override suspend fun readStoredDtcs(): List<Dtc> {
        val conn = connection ?: return emptyList()
        return runCatching { DtcDecoder.decode(conn.exec("03"), 0x43) }.getOrDefault(emptyList())
    }

    override suspend fun readPendingDtcs(): List<Dtc> {
        val conn = connection ?: return emptyList()
        return runCatching { DtcDecoder.decode(conn.exec("07"), 0x47) }.getOrDefault(emptyList())
    }

    override suspend fun readPermanentDtcs(): List<Dtc> {
        val conn = connection ?: return emptyList()
        return runCatching { DtcDecoder.decode(conn.exec("0A"), 0x4A) }.getOrDefault(emptyList())
    }

    override suspend fun clearDtcs(): Boolean {
        val conn = connection ?: return false
        return runCatching { conn.exec("04").uppercase().contains("44") }.getOrDefault(false)
    }

    override suspend fun command(raw: String): String? {
        val conn = connection ?: return null
        return runCatching { conn.exec(raw.trim()) }.getOrNull()
    }

    override suspend fun readVehicleInfo(): VehicleInfo {
        val conn = connection ?: return VehicleInfo()
        val vinRaw = runCatching { conn.exec("0902") }.getOrNull()
        val calRaw = runCatching { conn.exec("0904") }.getOrNull()
        val nameRaw = runCatching { conn.exec("090A") }.getOrNull()
        return VehicleInfo(
            vin = vinRaw?.let { DiagnosticParsers.parseMode09Vin(it) },
            calibrationIds = calRaw?.let { DiagnosticParsers.parseMode09CalIds(it) } ?: emptyList(),
            ecuName = nameRaw?.let { DiagnosticParsers.parseMode09CalIds(it).firstOrNull() },
            rawNotes = listOfNotNull(vinRaw?.take(80), calRaw?.take(80)),
        )
    }

    override suspend fun readReadiness(): ReadinessStatus {
        val conn = connection ?: return ReadinessStatus()
        val raw = runCatching { conn.exec("0101") }.getOrNull() ?: return ReadinessStatus()
        return DiagnosticParsers.parseReadiness(raw)
    }

    override suspend fun readFreezeFrame(): FreezeFrame {
        val conn = connection ?: return FreezeFrame()
        val raw = runCatching {
            conn.exec("0202") + " " + conn.exec("020C") + " " + conn.exec("020D") +
                " " + conn.exec("0205") + " " + conn.exec("0204")
        }.getOrNull() ?: return FreezeFrame()
        return DiagnosticParsers.parseFreezeFrame(raw)
    }

    override suspend fun readMode05(): List<O2TestResult> {
        val conn = connection ?: return emptyList()
        val raw = runCatching { conn.exec("05") }.getOrNull() ?: return emptyList()
        return DiagnosticParsers.dumpMode05(raw)
    }

    override suspend fun readMode06(): List<Mode06Result> {
        val conn = connection ?: return emptyList()
        val raw = runCatching { conn.exec("06") }.getOrNull() ?: return emptyList()
        return DiagnosticParsers.dumpMode06(raw)
    }

    override suspend fun probePids(
        pids: List<PidDefinition>,
        recoverFirst: Boolean,
    ): List<PidProbeResult> {
        val conn = connection ?: return emptyList()
        if (recoverFirst) {
            softRecover(conn)
        }
        val out = ArrayList<PidProbeResult>(pids.size)
        var failStreak = 0
        for (pid in pids) {
            // Bus already lost / timing out — don't sit on "Probing…" for minutes.
            if (failStreak >= 2) {
                out += PidProbeResult(pid, false, null, "SKIPPED (bus unhealthy)")
                continue
            }
            var raw = runCatching {
                conn.exec(pid.request, Elm327Connection.PROBE_TIMEOUT_MS)
            }.getOrNull()
            var up = raw?.uppercase().orEmpty()
            var bad = raw == null || BAD_TOKENS.any { up.contains(it) }
            if (bad && up.contains("NO DATA")) {
                delay(20L)
                raw = runCatching {
                    conn.exec(pid.request, Elm327Connection.PROBE_TIMEOUT_MS)
                }.getOrNull()
                up = raw?.uppercase().orEmpty()
                bad = raw == null || BAD_TOKENS.any { up.contains(it) }
            }
            when {
                up.contains("UNABLE") || raw == null -> failStreak++
                !bad -> failStreak = 0
            }

            if (bad) {
                out += PidProbeResult(pid, false, null, raw)
            } else {
                val bytes = when {
                    pid.request.startsWith("01") && pid.request.length == 4 ->
                        ObdResponseParser.rawDataBytes(pid.request, pid.dataBytes, raw!!)
                    pid.request.startsWith("22") -> extractMode22(pid.request, raw!!)
                    else -> null
                }
                val value = bytes?.let { pid.decode(it) }
                out += PidProbeResult(pid, true, value, raw)
            }
        }
        if (failStreak >= 2) {
            softRecover(conn)
        }
        return out
    }

    override suspend fun probeHondaModules(): List<ModuleScanResult> {
        return HondaPidCatalog.allPacks.map { pack ->
            val results = probePids(pack.pids)
            ObdLogger.logProbe("Honda:${pack.id}", results)
            val ok = results.filter { it.supported }
            ModuleScanResult(
                module = pack.title,
                profileId = pack.id,
                supportedCount = ok.size,
                totalCount = pack.pids.size,
                samplePids = ok.take(5).map { it.pid.label },
                status = when {
                    ok.isEmpty() -> "No response (not supported / wrong header)"
                    ok.size == pack.pids.size -> "All PIDs answered"
                    else -> "${ok.size}/${pack.pids.size} PIDs answered"
                },
            )
        }
    }

    override suspend fun readPid(pid: PidDefinition): Double? {
        return probePids(listOf(pid)).firstOrNull()?.sample
    }

    private fun extractMode22(request: String, raw: String): IntArray? {
        val pidHex = request.removePrefix("22")
        val header = "62$pidHex".uppercase()
        val cleaned = raw.replace(">", " ").replace("\r", " ").replace("\n", " ").uppercase()
        val hex = cleaned.filter { it.isDigit() || it in 'A'..'F' || it == ' ' }
        val joined = hex.split(Regex("\\s+")).filter { it.matches(Regex("[0-9A-F]+")) }.joinToString("")
        val idx = joined.indexOf(header)
        if (idx < 0) return null
        val data = joined.substring(idx + header.length).chunked(2)
            .filter { it.length == 2 }.mapNotNull { it.toIntOrNull(16) }
        return if (data.isEmpty()) null else data.toIntArray()
    }

    private suspend fun probeSupportedPids(conn: Elm327Connection): Set<Int> {
        val supported = mutableSetOf<Int>()
        for (base in intArrayOf(0x00, 0x20, 0x40, 0x60, 0xA0)) {
            val request = "01%02X".format(base)
            val raw = runCatching { conn.exec(request) }.getOrNull() ?: continue
            val bytes = ObdResponseParser.rawDataBytes(request, 4, raw) ?: continue
            supported += SupportedPids.fromBitmask(base, bytes)
        }
        if (supported.isNotEmpty()) {
            logger.logDebug(
                ObdLogger.Dir.INFO,
                "Supported PIDs: " + supported.sorted().joinToString(" ") { "%02X".format(it) },
            )
        }
        return supported
    }

    private fun VehicleSnapshot.withGear(hasEcuGear: Boolean): VehicleSnapshot {
        if (hasEcuGear && gearRatioActual != null) {
            val g = gearEstimator.gearFromRatio(gearRatioActual)
            if (g != null) return copy(gear = g, gearSource = GearSource.ECU, gearConfidencePct = null)
        }
        val est = if (speedKmh != null && rpm != null) {
            gearEstimator.estimateDetailed(speedKmh, rpm)
        } else {
            null
        }
        return copy(
            gear = est?.gear,
            gearSource = if (est != null) GearSource.ESTIMATED else GearSource.NONE,
            gearConfidencePct = est?.confidencePct,
        )
    }

    @SuppressLint("MissingPermission")
    private fun openSocket(): BluetoothSocket {
        val secure = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            secure.connect()
            return secure
        } catch (e: Exception) {
            runCatching { secure.close() }
            val fallback = device.javaClass
                .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, 1) as BluetoothSocket
            fallback.connect()
            return fallback
        }
    }

    private fun VehicleSnapshot.merge(pid: ObdPid, value: Double?): VehicleSnapshot = when (pid) {
        ObdPid.ENGINE_RPM -> copy(rpm = value ?: rpm)
        ObdPid.SPEED -> copy(speedKmh = value ?: speedKmh)
        ObdPid.COOLANT_TEMP -> copy(coolantC = value ?: coolantC)
        ObdPid.COOLANT_TEMP_2 -> copy(coolant2C = value ?: coolant2C)
        ObdPid.INTAKE_TEMP -> copy(intakeC = value ?: intakeC)
        ObdPid.AMBIENT_TEMP -> copy(ambientC = value ?: ambientC)
        ObdPid.ENGINE_LOAD -> copy(engineLoadPct = value ?: engineLoadPct)
        ObdPid.THROTTLE -> copy(throttlePct = value ?: throttlePct)
        ObdPid.TIMING_ADVANCE -> copy(timingAdvance = value ?: timingAdvance)
        ObdPid.MAF -> copy(mafGps = value ?: mafGps)
        ObdPid.INTAKE_MAP -> copy(mapKpa = value ?: mapKpa)
        ObdPid.STFT_B1 -> copy(stftPct = value ?: stftPct)
        ObdPid.LTFT_B1 -> copy(ltftPct = value ?: ltftPct)
        ObdPid.FUEL_SYSTEM_STATUS -> copy(
            fuelSystemStatus = FuelSystemDecoder.fromRawByte(value) ?: fuelSystemStatus,
        )
        ObdPid.CONTROL_MODULE_VOLTAGE -> copy(batteryVolts = value ?: batteryVolts)
        ObdPid.TRANSMISSION_GEAR_RATIO -> copy(gearRatioActual = value ?: gearRatioActual)
    }

    private suspend fun runInit(conn: Elm327Connection) {
        for (cmd in INIT_SEQUENCE) {
            conn.exec(cmd, Elm327Connection.INIT_TIMEOUT_MS)
            delay(80L)
        }
    }

    /** Restore broadcast Mode 01 after deep-search / UNABLE thrashing. */
    private suspend fun softRecover(conn: Elm327Connection) {
        for (cmd in RECOVER_SEQUENCE) {
            runCatching { conn.exec(cmd, Elm327Connection.INIT_TIMEOUT_MS) }
            delay(40L)
        }
    }

    private suspend fun readAtrv(conn: Elm327Connection): Double? {
        val raw = runCatching {
            conn.exec("ATRV", Elm327Connection.ATRV_TIMEOUT_MS)
        }.getOrNull() ?: return null
        if (isUnable(raw)) return null
        val v = ObdResponseParser.parseAtVoltage(raw) ?: return null
        // Median of last 3 ATRV samples — cheap clones sometimes spit a single
        // false low (11–12V) while the post still measures 13–14V.
        atrvWindow.addLast(v)
        while (atrvWindow.size > 3) atrvWindow.removeFirst()
        val median = atrvWindow.sorted()[atrvWindow.size / 2]
        logger.logDebug(ObdLogger.Dir.INFO, "ATRV battery raw=$v median=$median V (n=${atrvWindow.size})")
        return median
    }

    private fun isUnable(raw: String): Boolean =
        raw.uppercase().contains("UNABLE")

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val INIT_SEQUENCE = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATSP0")
        private val RECOVER_SEQUENCE = listOf("ATD", "ATE0", "ATL0", "ATS0", "ATSP0", "ATSH7DF", "ATAR")
        private val BAD_TOKENS = listOf("NO DATA", "UNABLE", "ERROR", "?", "STOPPED")
        /** Soft-recover loops that never reconnect leave a sticky false Dash. */
        private const val MAX_SOFT_RECOVER_BEFORE_RECONNECT = 3
    }
}
