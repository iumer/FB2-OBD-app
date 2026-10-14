package com.fb2.obd.obd

/**
 * Parses the Mode 01 "PIDs supported" bitmask responses (PID 0x00 / 0x20 / 0x40 /
 * 0x60 / 0xA0 ...). Each response carries 4 data bytes, bit-encoded MSB-first,
 * where the first bit maps to base+1 and the last to base+0x20.
 *
 * Example: request `0100`, response `41 00 BE 1F A8 13` -> the set of supported
 * PID numbers in the range 0x01..0x20.
 *
 * Pure Kotlin, unit testable.
 */
object SupportedPids {

    /**
     * @param base the base PID (0x00, 0x20, 0x40, 0x60, 0xA0 ...).
     * @param dataBytes the 4 decoded data bytes returned for that request.
     * @return the set of supported PID numbers in that block.
     */
    fun fromBitmask(base: Int, dataBytes: IntArray): Set<Int> {
        if (dataBytes.size < 4) return emptySet()
        val supported = mutableSetOf<Int>()
        for (byteIndex in 0 until 4) {
            val b = dataBytes[byteIndex]
            for (bit in 0 until 8) {
                // MSB first: bit 7 of byte 0 => base+1, bit 0 of byte 3 => base+0x20.
                val isSet = (b and (1 shl (7 - bit))) != 0
                if (isSet) {
                    val pidNumber = base + byteIndex * 8 + bit + 1
                    supported.add(pidNumber)
                }
            }
        }
        return supported
    }
}
