package com.fb2.obd.obd

/**
 * A catalog entry for a pollable / selectable sensor.
 *
 * [request] is the raw ELM327 command (e.g. "010C" Mode 01, "2211xx" Mode 22).
 * [decode] turns data bytes (after the positive-response header) into a Double,
 * or null when the frame can't be decoded.
 */
data class PidDefinition(
    val id: String,
    val request: String,
    val label: String,
    val unit: String,
    val category: PidCategory,
    val dataBytes: Int,
    val profile: String = "SAE",
    val decode: (IntArray) -> Double?,
) {
    /** Numeric PID for Mode 01 (e.g. 0x0C); -1 for Mode 22 / other. */
    val mode01Number: Int?
        get() = if (request.length == 4 && request.startsWith("01")) {
            request.substring(2).toIntOrNull(16)
        } else {
            null
        }
}

enum class PidCategory {
    ENGINE, FUEL, TEMPS, AIR, ELECTRICAL, EMISSIONS, TRANSMISSION,
    ABS, EPS, SRS, BODY, CLIMATE, TPMS, OTHER
}

/** Result of probing one PID against the live ECU. */
data class PidProbeResult(
    val pid: PidDefinition,
    val supported: Boolean,
    val sample: Double? = null,
    val raw: String? = null,
)
