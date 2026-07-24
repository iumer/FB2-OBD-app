package com.fb2.obd.obd

/**
 * Standard OBD-II Mode 01 (current data) PIDs used by the dashboard.
 *
 * Each entry knows the raw request string to send to the adapter, how many data
 * bytes the response carries (so the parser can slice exactly that many and
 * ignore anything appended by a second responding module), and how to decode
 * those data bytes. Decoders are pure functions of the data byte array so they
 * can be unit tested on the JVM without any Android dependencies.
 */
enum class ObdPid(
    val request: String,
    val label: String,
    val unit: String,
    val dataBytes: Int,
    val decode: (IntArray) -> Double?,
) {
    ENGINE_RPM("010C", "RPM", "rpm", 2, { d ->
        if (d.size >= 2) ((d[0] * 256) + d[1]) / 4.0 else null
    }),
    SPEED("010D", "Speed", "km/h", 1, { d ->
        if (d.isNotEmpty()) d[0].toDouble() else null
    }),
    COOLANT_TEMP("0105", "Coolant 1", "\u00B0C", 1, { d ->
        if (d.isNotEmpty()) (d[0] - 40).toDouble() else null
    }),
    // PID 0x67 returns [support, ECT1, ECT2]; bit 1 of the support byte marks
    // sensor 2 present. We read sensor 2 (byte C, index 2) only when supported.
    // This is the second coolant sensor (e.g. post-thermostat) that most generic
    // OBD apps ignore.
    COOLANT_TEMP_2("0167", "Coolant 2", "\u00B0C", 3, { d ->
        if (d.size >= 3 && (d[0] and 0x02) != 0) (d[2] - 40).toDouble() else null
    }),
    INTAKE_TEMP("010F", "Intake", "\u00B0C", 1, { d ->
        if (d.isNotEmpty()) (d[0] - 40).toDouble() else null
    }),
    ENGINE_LOAD("0104", "Load", "%", 1, { d ->
        if (d.isNotEmpty()) d[0] * 100.0 / 255.0 else null
    }),
    THROTTLE("0111", "Throttle", "%", 1, { d ->
        if (d.isNotEmpty()) d[0] * 100.0 / 255.0 else null
    }),
    TIMING_ADVANCE("010E", "Timing", "\u00B0", 1, { d ->
        if (d.isNotEmpty()) d[0] / 2.0 - 64.0 else null
    }),
    MAF("0110", "MAF", "g/s", 2, { d ->
        if (d.size >= 2) ((d[0] * 256) + d[1]) / 100.0 else null
    }),
    INTAKE_MAP("010B", "MAP", "kPa", 1, { d ->
        if (d.isNotEmpty()) d[0].toDouble() else null
    }),
    STFT_B1("0106", "STFT", "%", 1, { d ->
        if (d.isNotEmpty()) (d[0] - 128) * 100.0 / 128.0 else null
    }),
    LTFT_B1("0107", "LTFT", "%", 1, { d ->
        if (d.isNotEmpty()) (d[0] - 128) * 100.0 / 128.0 else null
    }),
    /** Raw bank-1 status byte; use [FuelSystemDecoder] for Open/Closed Loop text. */
    FUEL_SYSTEM_STATUS("0103", "Fuel loop", "", 2, { d ->
        if (d.isNotEmpty()) d[0].toDouble() else null
    }),
    CONTROL_MODULE_VOLTAGE("0142", "ECU V", "V", 2, { d ->
        if (d.size >= 2) ((d[0] * 256) + d[1]) / 1000.0 else null
    }),
    AMBIENT_TEMP("0146", "Ambient", "\u00B0C", 1, { d ->
        if (d.isNotEmpty()) (d[0] - 40).toDouble() else null
    }),
    // PID 0xA4 "Transmission Actual Gear": 4 bytes [support, gear-bits, C, D].
    // Bit A1 of the support byte marks it available; actual gear ratio = (256*C + D)/1000.
    // Returns the ratio (mapped to a gear number by GearEstimator).
    TRANSMISSION_GEAR_RATIO("01A4", "Gear ratio", "", 4, { d ->
        if (d.size >= 4 && (d[0] and 0x02) != 0) ((256 * d[2]) + d[3]) / 1000.0 else null
    }),
    ;

    /** The PID number itself (e.g. 0x0C for RPM), parsed from the request. */
    val number: Int
        get() = request.substring(2).toInt(16)

    /** Mode byte echoed back by the ECU is the request mode + 0x40 (e.g. 01 -> 41). */
    val responseHeader: String
        get() {
            val mode = request.substring(0, 2).toInt(16) + 0x40
            val pid = request.substring(2)
            return "%02X%s".format(mode, pid)
        }
}
