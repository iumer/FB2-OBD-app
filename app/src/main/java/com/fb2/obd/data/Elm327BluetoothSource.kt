package com.fb2.obd.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.ObdResponseParser
import com.fb2.obd.obd.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Real ELM327 adapter over Bluetooth Classic (Serial Port Profile).
 *
 * Opens an RFCOMM socket to the paired dongle, runs the standard ELM327 init
 * sequence, then round-robins the dashboard PIDs and decodes each reply with the
 * shared [ObdResponseParser]. Requires BLUETOOTH_CONNECT at runtime.
 */
class Elm327BluetoothSource(
    private val device: BluetoothDevice,
    private val gearEstimator: GearEstimator = GearEstimator(),
) : ObdSource {

    override val name: String = "ELM327 (Bluetooth)"

    private val polled = listOf(
        ObdPid.ENGINE_RPM,
        ObdPid.SPEED,
        ObdPid.COOLANT_TEMP,
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
    override fun snapshots(): Flow<VehicleSnapshot> = flow {
        val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket.connect()
        val input = socket.inputStream
        val output = socket.outputStream
        try {
            for (cmd in INIT_SEQUENCE) {
                sendCommand(input, output, cmd)
            }

            var snapshot = VehicleSnapshot.EMPTY
            while (true) {
                for (pid in polled) {
                    val raw = sendCommand(input, output, pid.request)
                    val value = ObdResponseParser.parse(pid, raw)
                    snapshot = snapshot.merge(pid, value)
                }
                val gear = if (snapshot.speedKmh != null && snapshot.rpm != null) {
                    gearEstimator.estimate(snapshot.speedKmh!!, snapshot.rpm!!)
                } else {
                    null
                }
                emit(snapshot.copy(gear = gear))
            }
        } finally {
            runCatching { socket.close() }
        }
    }.flowOn(Dispatchers.IO)

    private fun sendCommand(input: InputStream, output: OutputStream, command: String): String {
        output.write((command + "\r").toByteArray())
        output.flush()
        val sb = StringBuilder()
        // ELM327 terminates a response with the ">" prompt.
        while (true) {
            val b = input.read()
            if (b == -1) break
            val c = b.toChar()
            if (c == '>') break
            sb.append(c)
        }
        return sb.toString()
    }

    private fun VehicleSnapshot.merge(pid: ObdPid, value: Double?): VehicleSnapshot = when (pid) {
        ObdPid.ENGINE_RPM -> copy(rpm = value ?: rpm)
        ObdPid.SPEED -> copy(speedKmh = value ?: speedKmh)
        ObdPid.COOLANT_TEMP -> copy(coolantC = value ?: coolantC)
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

        private val INIT_SEQUENCE = listOf(
            "ATZ",   // reset
            "ATE0",  // echo off
            "ATL0",  // linefeeds off
            "ATS0",  // spaces off
            "ATSP0", // auto protocol
        )
    }
}
