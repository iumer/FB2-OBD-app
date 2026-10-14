package com.fb2.obd.obd

/**
 * Parses Mode 01 PID 01 readiness, Mode 02 freeze frame, Mode 09 vehicle info,
 * and Mode 05/06 text dumps from raw ELM327 responses.
 */
object DiagnosticParsers {

    fun parseReadiness(raw: String): ReadinessStatus {
        val bytes = ObdResponseParser.rawDataBytes("0101", 4, raw) ?: return ReadinessStatus(raw = raw)
        if (bytes.size < 4) return ReadinessStatus(raw = raw)
        val a = bytes[0]
        val b = bytes[1]
        val c = bytes[2]
        val d = bytes[3]
        val mil = (a and 0x80) != 0
        val count = a and 0x7F
        val spark = (b and 0x08) == 0 // spark ignition if bit3 clear (simplified)
        val monitors = mutableListOf<MonitorItem>()
        fun add(name: String, availBit: Int, incompleteBit: Int, availByte: Int, incompleteByte: Int) {
            val avail = (availByte and availBit) != 0
            val incomplete = (incompleteByte and incompleteBit) != 0
            monitors += MonitorItem(name, avail, avail && !incomplete)
        }
        // Continuous monitors in B
        monitors += MonitorItem("Misfire", true, (b and 0x01) == 0)
        monitors += MonitorItem("Fuel system", true, (b and 0x02) == 0)
        monitors += MonitorItem("Components", true, (b and 0x04) == 0)
        // Non-continuous — spark layout (most FB2)
        if (spark) {
            add("Catalyst", 0x01, 0x01, c, d)
            add("Heated catalyst", 0x02, 0x02, c, d)
            add("EVAP", 0x04, 0x04, c, d)
            add("Secondary air", 0x08, 0x08, c, d)
            add("O2 sensor", 0x20, 0x20, c, d)
            add("O2 heater", 0x40, 0x40, c, d)
            add("EGR/VVT", 0x80, 0x80, c, d)
        }
        return ReadinessStatus(mil, count, monitors, raw)
    }

    fun parseFreezeFrame(raw: String): FreezeFrame {
        val cleaned = raw.replace(">", " ").uppercase()
        if (cleaned.contains("NO DATA") || cleaned.contains("UNABLE")) {
            return FreezeFrame(raw = raw)
        }
        // Mode 02 PID 02 returns the DTC that caused the freeze frame.
        val dtcBytes = ObdResponseParser.rawDataBytes("0202", 2, raw)
        val dtc = if (dtcBytes != null && dtcBytes.size >= 2) {
            DtcDecoder.decodePair(dtcBytes[0], dtcBytes[1])
        } else null

        val values = mutableMapOf<String, String>()
        // Pull a few useful Mode 02 mirrors if present in multi-PID responses.
        listOf(
            "0204" to "Load", "0205" to "Coolant", "020C" to "RPM",
            "020D" to "Speed", "020F" to "Intake", "0211" to "Throttle",
        ).forEach { (req, label) ->
            val b = ObdResponseParser.rawDataBytes(req, 2, raw) ?: return@forEach
            val pid = StandardPidCatalog.byId("01" + req.substring(2)) ?: return@forEach
            val v = pid.decode(b.copyOf(pid.dataBytes.coerceAtMost(b.size)))
            if (v != null) values[label] = "%.1f %s".format(v, pid.unit).trim()
        }
        return FreezeFrame(dtc, values, raw)
    }

    fun parseMode09Vin(raw: String): String? {
        val cleaned = raw.replace(">", " ").replace("\r", " ").replace("\n", " ").uppercase()
        if (cleaned.contains("NO DATA")) return null
        // Strip non-printable / keep VIN charset.
        val hex = cleaned.filter { it.isDigit() || it in 'A'..'F' || it == ' ' }
        val tokens = hex.trim().split(Regex("\\s+")).filter { it.length == 2 }
        // Look for 49 02 ... then ASCII bytes.
        val joined = tokens.joinToString("")
        val idx = joined.indexOf("4902")
        if (idx < 0) {
            // Fallback: extract 17-char VIN-looking string from ascii in response.
            val ascii = raw.filter { it.code in 32..126 }
            val m = Regex("[A-HJ-NPR-Z0-9]{17}").find(ascii)
            return m?.value
        }
        val data = joined.substring(idx + 4).chunked(2).mapNotNull { it.toIntOrNull(16) }
        // Skip framing count bytes; collect printable ASCII.
        val vin = data.dropWhile { it < 0x30 || it > 0x5A }
            .map { it.toChar() }
            .filter { it.isLetterOrDigit() }
            .joinToString("")
            .take(17)
        return vin.ifBlank { null }
    }

    fun parseMode09CalIds(raw: String): List<String> {
        val ascii = raw.filter { it.code in 32..126 }
        return Regex("[A-Z0-9-]{5,}").findAll(ascii).map { it.value }.distinct().take(8).toList()
    }

    fun dumpMode05(raw: String): List<O2TestResult> {
        if (raw.uppercase().contains("NO DATA")) return emptyList()
        return listOf(O2TestResult("O2", "raw", raw.take(80), raw))
    }

    fun dumpMode06(raw: String): List<Mode06Result> {
        if (raw.uppercase().contains("NO DATA")) return emptyList()
        // Best-effort line dump — full Mode 06 framing varies widely.
        return listOf(
            Mode06Result("06", "--", "--", "--", "--", null, raw.take(120)),
        )
    }
}
