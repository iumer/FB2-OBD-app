package com.fb2.obd.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.ObdResponseParser
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
 * Opens an RFCOMM socket to the paired dongle, runs the standard ELM327 init
 * sequence, then round-robins the dashboard PIDs and decodes each reply with the
 * shared [ObdResponseParser]. Requires BLUETOOTH_CONNECT at runtime.
 *
 * Robustness:
 * - Reads are bounded by [READ_TIMEOUT_MS] so a silent/stalled adapter surfaces
 *   an error instead of hanging the UI forever.
 * - The socket is closed from [awaitClose], so cancelling the flow (e.g. the user
 *   switches to demo or leaves the screen) unblocks any in-progress read and
 *   releases the socket/thread.
 * - A bounded reconnect ([MAX_RECONNECTS]) rides out brief drop-outs.
 */
class Elm327BluetoothSource(
    private val device: BluetoothDevice,
    private val gearEstimator: GearEstimator = GearEstimator(),
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
        // Discovery must be off before connecting or the connect will be slow/fail.
        runCatching { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() }

        val socket = openSocket()
        val input = socket.inputStream
        val output = socket.outputStream

        val reader = launch(Dispatchers.IO) {
            try {
                for (cmd in INIT_SEQUENCE) {
                    sendCommand(input, output, cmd)
                    delay(120L)
                }
                var snapshot = VehicleSnapshot.EMPTY
                while (isActive) {
                    for (pid in polled) {
                        val raw = sendCommand(input, output, pid.request)
                        snapshot = snapshot.merge(pid, ObdResponseParser.parse(pid, raw))
                    }
                    val gear = if (snapshot.speedKmh != null && snapshot.rpm != null) {
                        gearEstimator.estimate(snapshot.speedKmh!!, snapshot.rpm!!)
                    } else {
                        null
                    }
                    trySend(snapshot.copy(gear = gear))
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
                delay(1500L)
                true
            } else {
                false
            }
        }
        .flowOn(Dispatchers.IO)

    /**
     * Opens the RFCOMM socket, falling back to the reflection-based channel-1
     * socket that many cheap ELM327 clones require when the standard SPP
     * service-record connect fails.
     */
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
        output.write((command + "\r").toByteArray())
        output.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val b = input.read()
                if (b == -1) break
                val c = b.toChar()
                if (c == '>') return sb.toString()
                sb.append(c)
            } else {
                Thread.sleep(5L)
            }
        }
        throw IOException("ELM327 read timeout for '$command'")
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
