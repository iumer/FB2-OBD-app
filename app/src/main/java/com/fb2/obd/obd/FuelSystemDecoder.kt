package com.fb2.obd.obd

/**
 * SAE J1979 PID 0103 — Fuel system status (bank 1 / bank 2 bitfields).
 *
 * Bit meanings (per bank byte):
 * - 0 Open loop — not yet ready for closed loop
 * - 1 Closed loop — using O2 feedback
 * - 2 Open loop due to driving conditions
 * - 3 Open loop due to detected system fault
 * - 4 Closed loop, but at least one O2 sensor is faulty
 */
object FuelSystemDecoder {

    fun decodeBank(byte: Int): String = when {
        (byte and 0x02) != 0 -> "CLOSED LOOP"
        (byte and 0x10) != 0 -> "CLOSED (FAULT)"
        (byte and 0x08) != 0 -> "OPEN (FAULT)"
        (byte and 0x04) != 0 -> "OPEN (DRIVE)"
        (byte and 0x01) != 0 -> "OPEN LOOP"
        else -> "UNKNOWN"
    }

    /** Prefer bank 1; fall back to bank 2 if bank 1 is empty/unknown. */
    fun decode(bytes: IntArray): String {
        if (bytes.isEmpty()) return "UNKNOWN"
        val b1 = decodeBank(bytes[0])
        if (b1 != "UNKNOWN" || bytes.size < 2) return b1
        return decodeBank(bytes[1])
    }

    /** Decode from the raw A-byte value stored as a Double on the snapshot. */
    fun fromRawByte(raw: Double?): String? {
        if (raw == null) return null
        return decodeBank(raw.toInt().coerceIn(0, 255))
    }
}
