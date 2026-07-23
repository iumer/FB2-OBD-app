package com.fb2.obd.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.ObdResponseParser
import com.fb2.obd.obd.SupportedPids
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
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Real ELM327 adapter over Bluetooth Classic (Serial Port Profile).
 *
 * On connect it runs the ELM327 init sequence, probes which PIDs the ECU
 * supports, then round-robins the supported dashboard PIDs. All raw traffic is
 * mirrored to [ObdLogger] for the in-app debug log.
 *
 * Robustness:
 * - Bounded per-read timeout so a silent adapter surfaces an error, not a hang.
 * - Per-PID errors are isolated: a single slow/unsupported PID is skipped, it
 *   does not tear down the whole stream. Only a fully dead cycle triggers a
 *   bounded reconnect.
 * - Socket is closed from [awaitClose] so cancellation unblocks a pending read.
 */
class Elm327BluetoothSource(
    private val device: BluetoothDevice,
    private val gearEstimator: GearEstimator = GearEstimator(),
    private val logger: ObdLogger = ObdLogger,
) : ObdSource {

    override val name: String = "ELM327 (Bluetooth)"
    override val isLive: Boolean = true

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
        val input = socket.inputStream
        val output = socket.outputStream

        val reader = launch(Dispatchers.IO) {
            try {
                for (cmd in INIT_SEQUENCE) {
                    sendCommand(input, output, cmd)
                    delay(120L)
                }

                val supported = probeSupportedPids(input, output)
                val basePids = if (supported.isEmpty()) polled
                else polled.filter { it.number in supported }
                val hasEcuGear = supported.isEmpty() ||
                    ObdPid.TRANSMISSION_GEAR_RATIO.number in supported
                val activePids = if (hasEcuGear) basePids + ObdPid.TRANSMISSION_GEAR_RATIO else basePids
                val unsupported = if (supported.isEmpty()) emptySet()
                else polled.map { it.number }.filter { it !in supported }.toSet()

                logger.logDebug(
                    ObdLogger.Dir.INFO,
                    "Polling ${activePids.size} PIDs; ECU gear=${hasEcuGear}",
                )

                var snapshot = VehicleSnapshot.EMPTY.copy(unsupportedPids = unsupported)
                var deadCycles = 0
                while (isActive) {
                    var responded = 0
                    for (pid in activePids) {
                        val value = try {
                            val raw = sendCommand(input, output, pid.request)
                            responded++
                            ObdResponseParser.parse(pid, raw)
                        } catch (io: IOException) {
                            // Isolate a slow/unsupported PID; keep the stream alive.
                            logger.logDebug(ObdLogger.Dir.INFO, "skip ${pid.request}: ${io.message}")
                            null
                        }
                        snapshot = snapshot.merge(pid, value)
                    }

                    // A fully dead cycle (nothing answered) means the link is gone.
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
            runCatching { socket.close() }
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

    /** Queries the Mode 01 supported-PID bitmasks and returns all supported PIDs. */
    private fun probeSupportedPids(input: InputStream, output: OutputStream): Set<Int> {
        val supported = mutableSetOf<Int>()
        for (base in intArrayOf(0x00, 0x20, 0x40, 0x60, 0xA0)) {
            val request = "01%02X".format(base)
            val raw = try {
                sendCommand(input, output, request)
            } catch (io: IOException) {
                continue
            }
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
        // Prefer the ECU actual-gear ratio; fall back to speed/RPM estimation.
        if (hasEcuGear && gearRatioActual != null) {
            val g = gearEstimator.gearFromRatio(gearRatioActual)
            if (g != null) return copy(gear = g, gearSource = GearSource.ECU)
        }
        val est = if (speedKmh != null && rpm != null) {
            gearEstimator.estimate(speedKmh, rpm)
        } else {
            null
        }
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

    /** Writes a command and reads until the ELM327 ">" prompt or a timeout. */
    private fun sendCommand(input: InputStream, output: OutputStream, command: String): String {
        logger.logDebug(ObdLogger.Dir.TX, command)
        output.write((command + "\r").toByteArray())
        output.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val b = input.read()
                if (b == -1) break
                val c = b.toChar()
                if (c == '>') {
                    logger.logDebug(ObdLogger.Dir.RX, sb.toString())
                    return sb.toString()
                }
                sb.append(c)
            } else {
                Thread.sleep(5L)
            }
        }
        throw IOException("read timeout for '$command'")
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
        /** Standard Serial Port Profile UUID used by ELM327 dongles. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private const val READ_TIMEOUT_MS = 3000L
        private const val MAX_RECONNECTS = 3L

        private val INIT_SEQUENCE = listOf(
            "ATZ",   // reset
            "ATE0",  // echo off
            "ATL0",  // linefeeds off
            "ATS0",  // spaces off
            "ATSP0", // auto protocol
        )
    }
}
