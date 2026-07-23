package com.fb2.obd.obd

/** A decoded diagnostic trouble code plus a human-readable description. */
data class Dtc(val code: String, val description: String)

/**
 * Decodes Mode 03 (stored) / Mode 07 (pending) / Mode 0A (permanent) DTC
 * responses into codes like "P0133". Pure Kotlin, unit testable.
 */
object DtcDecoder {

    private val LETTERS = charArrayOf('P', 'C', 'B', 'U')

    /**
     * @param raw the raw ELM327 response text.
     * @param modeByte the expected positive-response mode byte (0x43 for Mode 03,
     *   0x47 for Mode 07, 0x4A for Mode 0A).
     */
    fun decode(raw: String, modeByte: Int): List<Dtc> {
        val cleaned = raw.replace(">", " ").replace("\r", " ").replace("\n", " ").uppercase()
        if (listOf("NO DATA", "UNABLE", "ERROR", "STOPPED", "?", "BUS INIT").any { cleaned.contains(it) }) {
            return emptyList()
        }

        val tokens = cleaned.split(Regex("\\s+")).filter { it.matches(Regex("[0-9A-F]+")) }
        val bytes = tokens.joinToString("").chunked(2).filter { it.length == 2 }.map { it.toInt(16) }

        val idx = bytes.indexOfFirst { it == modeByte }
        if (idx < 0) return emptyList()
        var data = bytes.drop(idx + 1)
        // CAN responses prepend a DTC count byte; if present the payload length is odd.
        if (data.size % 2 == 1) data = data.drop(1)

        val result = mutableListOf<Dtc>()
        var i = 0
        while (i + 1 < data.size) {
            val a = data[i]
            val b = data[i + 1]
            i += 2
            if (a == 0 && b == 0) continue // padding / empty slot
            val code = decodePair(a, b)
            result += Dtc(code, DtcCatalog.describe(code))
        }
        return result
    }

    fun decodePair(a: Int, b: Int): String {
        val letter = LETTERS[(a shr 6) and 0x03]
        val firstDigit = (a shr 4) and 0x03
        val secondDigit = a and 0x0F
        val thirdDigit = (b shr 4) and 0x0F
        val fourthDigit = b and 0x0F
        return "%c%d%X%X%X".format(letter, firstDigit, secondDigit, thirdDigit, fourthDigit)
    }
}

/** Plain-language descriptions for common generic codes (with a safe fallback). */
object DtcCatalog {
    private val map = mapOf(
        "P0100" to "Mass air flow (MAF) circuit",
        "P0101" to "MAF circuit range/performance",
        "P0102" to "MAF circuit low input",
        "P0106" to "MAP/baro sensor range/performance",
        "P0107" to "MAP/baro sensor low input",
        "P0111" to "Intake air temp sensor range/performance",
        "P0113" to "Intake air temp sensor high input",
        "P0116" to "Coolant temp sensor range/performance",
        "P0117" to "Coolant temp sensor low input",
        "P0118" to "Coolant temp sensor high input",
        "P0120" to "Throttle/pedal position sensor A",
        "P0128" to "Coolant thermostat (below regulating temp)",
        "P0131" to "O2 sensor low voltage (B1S1)",
        "P0133" to "O2 sensor slow response (B1S1)",
        "P0135" to "O2 sensor heater circuit (B1S1)",
        "P0137" to "O2 sensor low voltage (B1S2)",
        "P0139" to "O2 sensor slow response (B1S2)",
        "P0171" to "System too lean (Bank 1)",
        "P0172" to "System too rich (Bank 1)",
        "P0300" to "Random/multiple cylinder misfire",
        "P0301" to "Cylinder 1 misfire",
        "P0302" to "Cylinder 2 misfire",
        "P0303" to "Cylinder 3 misfire",
        "P0304" to "Cylinder 4 misfire",
        "P0325" to "Knock sensor circuit",
        "P0335" to "Crankshaft position sensor A",
        "P0339" to "Crankshaft position sensor intermittent",
        "P0340" to "Camshaft position sensor A",
        "P0401" to "EGR flow insufficient",
        "P0420" to "Catalyst efficiency below threshold (Bank 1)",
        "P0430" to "Catalyst efficiency below threshold (Bank 2)",
        "P0441" to "EVAP purge flow incorrect",
        "P0455" to "EVAP system large leak",
        "P0505" to "Idle air control system",
        "P0562" to "System voltage low (charging)",
        "P0563" to "System voltage high",
        "P0700" to "Transmission control system (see TCM codes)",
        "P0715" to "Input/turbine speed sensor",
        "P0740" to "Torque converter clutch circuit",
        "P0741" to "Torque converter clutch performance/stuck off",
        "P0780" to "Shift malfunction",
        "P0781" to "1-2 shift malfunction",
    )

    fun describe(code: String): String =
        map[code] ?: "Manufacturer-specific or unknown \u2014 check service data"
}
