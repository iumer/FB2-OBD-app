package com.fb2.obd.obd

/**
 * Parses raw ELM327 text responses into decoded numeric values.
 *
 * ELM327 replies are noisy: they may echo the command, contain "SEARCHING...",
 * whitespace, line breaks, and a trailing ">" prompt. This parser normalises the
 * text, locates the expected "41 XX" response header for the requested PID, and
 * feeds the trailing data bytes to the PID's decoder.
 *
 * Pure Kotlin: no Android dependencies, fully unit-testable.
 */
object ObdResponseParser {

    /** Decode a Mode 01 PID response. Returns null if the frame can't be parsed. */
    fun parse(pid: ObdPid, raw: String): Double? {
        val bytes = extractDataBytes(pid.responseHeader, pid.dataBytes, raw) ?: return null
        return pid.decode(bytes)
    }

    /**
     * Extract raw data bytes for an arbitrary Mode 01 request (used by the
     * supported-PID probe). [request] is e.g. "0100"; [expected] is the number of
     * data bytes to slice.
     */
    fun rawDataBytes(request: String, expected: Int, raw: String): IntArray? {
        val mode = request.substring(0, 2).toInt(16) + 0x40
        val pid = request.substring(2)
        val header = "%02X%s".format(mode, pid)
        return extractDataBytes(header, expected, raw)
    }

    /**
     * ELM327 "ATRV" battery voltage reply, e.g. "12.5V" or "13.9". Returns volts.
     * Rejects OBD error tokens and out-of-range parses (polluted buffers).
     */
    fun parseAtVoltage(raw: String): Double? {
        val cleaned = raw.replace(">", " ").replace("\r", " ").replace("\n", " ")
        val up = cleaned.uppercase()
        if (listOf("UNABLE", "ERROR", "NO DATA", "STOPPED", "BUS INIT", "?").any { up.contains(it) }) {
            return null
        }
        // Prefer explicit "12.6V" so we don't grab digits from a leftover Mode 01 frame.
        val withV = Regex("(\\d{1,2}\\.\\d+)\\s*V").find(up.replace(',', '.'))
        if (withV != null) {
            val v = withV.groupValues[1].toDoubleOrNull() ?: return null
            return v.takeIf { it in 5.0..20.0 }
        }
        val match = Regex("(\\d{1,2}(?:\\.\\d+)?)").find(cleaned.replace(",", "."))
        val v = match?.value?.toDoubleOrNull() ?: return null
        return v.takeIf { it in 5.0..20.0 }
    }

    private fun extractDataBytes(header: String, expected: Int, raw: String): IntArray? {
        val cleaned = raw
            .replace(">", " ")
            .replace("\r", " ")
            .replace("\n", " ")
            .uppercase()

        // Reject explicit error/no-data responses.
        val errorTokens = listOf("NO DATA", "UNABLE", "ERROR", "STOPPED", "?", "BUS INIT")
        if (errorTokens.any { cleaned.contains(it) }) return null

        // Keep only whole hex tokens (drops echoes like "SEARCHING...", "ELM327",
        // voltage strings, etc.), then concatenate and re-chunk into byte pairs.
        // This handles both compact ("410C1AF8") and spaced ("41 0C 1A F8") frames.
        val hexTokens = cleaned
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it.matches(Regex("[0-9A-F]+")) }
        val joined = hexTokens.joinToString("")
        if (joined.length < 4) return null

        val bytes = joined.chunked(2).filter { it.length == 2 }.mapNotNull { it.toIntOrNull(16) }
        if (bytes.size < 2) return null

        val headerHi = header.substring(0, 2).toInt(16)
        val headerLo = header.substring(2, 4).toInt(16)

        // Find the response header pair (mode+0x40, pid); take exactly the number
        // of data bytes this PID carries. Slicing to the expected length avoids
        // pulling in a second module's appended "41 XX ..." on multi-ECU replies.
        // Skips any command echo that appears before the real response.
        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == headerHi && bytes[i + 1] == headerLo) {
                val start = i + 2
                if (start >= bytes.size) return null
                val end = minOf(start + expected, bytes.size)
                val data = bytes.subList(start, end)
                return if (data.isEmpty()) null else data.toIntArray()
            }
        }
        return null
    }
}
