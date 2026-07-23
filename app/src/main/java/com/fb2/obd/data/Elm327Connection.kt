package com.fb2.obd.data

import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Owns an open ELM327 RFCOMM socket and serialises access to it.
 *
 * Both the continuous PID polling loop and one-off commands (DTC read/clear,
 * terminal) go through [exec], guarded by a [Mutex] so reads/writes never
 * interleave on the single serial stream. Raw traffic is mirrored to [ObdLogger].
 */
class Elm327Connection(
    private val socket: BluetoothSocket,
    private val logger: ObdLogger = ObdLogger,
) {
    private val input = socket.inputStream
    private val output = socket.outputStream
    private val mutex = Mutex()

    /** Runs one command and returns the response text (without the ">" prompt). */
    suspend fun exec(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String =
        mutex.withLock {
            withContext(Dispatchers.IO) { sendRaw(command, timeoutMs) }
        }

    fun close() {
        runCatching { socket.close() }
    }

    private fun sendRaw(command: String, timeoutMs: Long): String {
        logger.logDebug(ObdLogger.Dir.TX, command)
        output.write((command + "\r").toByteArray())
        output.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
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

    companion object {
        const val DEFAULT_TIMEOUT_MS = 3000L
    }
}
