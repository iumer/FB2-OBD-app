package com.fb2.obd.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.fb2.obd.obd.DiagnosticParsers
import com.fb2.obd.obd.Dtc
import com.fb2.obd.obd.DtcDecoder
import com.fb2.obd.obd.FreezeFrame
import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.HondaPidCatalog
import com.fb2.obd.obd.Mode06Result
import com.fb2.obd.obd.ModuleScanResult
import com.fb2.obd.obd.O2TestResult
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.ObdResponseParser
import com.fb2.obd.obd.PidDefinition
import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.ReadinessStatus
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
 */
class Elm327BluetoothSource(
    private val device: BluetoothDevice,
    private val gearEstimator: GearEstimator = GearEstimator(),
    private val logger: ObdLogger = ObdLogger,
) : ObdSource {

    override val name: String = "ELM327 (Bluetooth)"
    override val isLive: Boolean = true

    @Volatile
    private var connection: Elm327Connection? = null

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
        ObdPid.CONTROL_MODULE_VOLTAGE,
        ObdPid.AMBIENT_TEMP,
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
                for (cmd in INIT_SEQUENCE) {
                    conn.exec(cmd)
                    delay(120L)
                }

                val supported = probeSupportedPids(conn)
                val basePids = if (supported.isEmpty()) polled
                else polled.filter { it.number in supported }
                val hasEcuGear = supported.isEmpty() ||
                    ObdPid.TRANSMISSION_GEAR_RATIO.number in supported
                val activePids = if (hasEcuGear) basePids + ObdPid.TRANSMISSION_GEAR_RATIO else basePids
                val unsupported = if (supported.isEmpty()) emptySet()
                else polled.map { it.number }.filter { it !in supported }.toSet()

                logger.logDebug(ObdLogger.Dir.INFO, "Polling ${activePids.size} PIDs; ECU gear=$hasEcuGear")

                var snapshot = VehicleSnapshot.EMPTY.copy(unsupportedPids = unsupported)
                var deadCycles = 0
                while (isActive) {
                    var responded = 0
                    for (pid in activePids) {
                        val value = try {
                            val raw = conn.exec(pid.request)
                            responded++
                            ObdResponseParser.parse(pid, raw)
                        } catch (io: IOException) {
                            logger.logDebug(ObdLogger.Dir.INFO, "skip ${pid.request}: ${io.message}")
                            null
                        }
                        snapshot = snapshot.merge(pid, value)
                    }
                    if (responded == 0) {
                        deadCycles++
                        if (deadCycles >= 2) throw IOException("No response from adapter")
                    } else {
                        deadCycles = 0
                    }
                    snapshot = snapshot.withGear(hasEcuGear)
                    trySend(snapshot)
                }
            } catch (e: Exception) {
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
            if (cause is IOException && attempt < MAX_RECONNECTS) {
                logger.logDebug(ObdLogger.Dir.INFO, "reconnect attempt ${attempt + 1}")
                delay(1500L)
                true
            } else {
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
        // Request freeze-frame DTC + a few common PIDs.
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

    override suspend fun probePids(pids: List<PidDefinition>): List<PidProbeResult> {
        val conn = connection ?: return emptyList()
        return pids.map { pid ->
            val raw = runCatching { conn.exec(pid.request) }.getOrNull()
            val up = raw?.uppercase().orEmpty()
            val bad = raw == null || listOf("NO DATA", "UNABLE", "ERROR", "?", "STOPPED").any { up.contains(it) }
            if (bad) {
                PidProbeResult(pid, false, null, raw)
            } else {
                val bytes = when {
                    pid.request.startsWith("01") && pid.request.length == 4 ->
                        ObdResponseParser.rawDataBytes(pid.request, pid.dataBytes, raw!!)
                    pid.request.startsWith("22") -> extractMode22(pid.request, raw!!)
                    else -> null
                }
                val value = bytes?.let { pid.decode(it) }
                PidProbeResult(pid, true, value, raw)
            }
        }
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
        // Positive response 62 + PID bytes.
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
            if (g != null) return copy(gear = g, gearSource = GearSource.ECU)
        }
        val est = if (speedKmh != null && rpm != null) gearEstimator.estimate(speedKmh, rpm) else null
        return copy(
            gear = est,
            gearSource = if (est != null) GearSource.ESTIMATED else GearSource.NONE,
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
        ObdPid.CONTROL_MODULE_VOLTAGE -> copy(batteryVolts = value ?: batteryVolts)
        ObdPid.TRANSMISSION_GEAR_RATIO -> copy(gearRatioActual = value ?: gearRatioActual)
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val MAX_RECONNECTS = 3L
        private val INIT_SEQUENCE = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATSP0")
    }
}
