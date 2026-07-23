package com.fb2.obd.obd

/**
 * Honda / Acura enhanced (Mode 22) profile packs for Civic FB2-class cars.
 *
 * Mode 22 PIDs vary by market/year/ECU calibration. Each pack is probed at
 * connect time: if the ECU answers with data (not NO DATA / UNABLE), the PID
 * is marked supported. Decode formulas are best-effort community mappings and
 * will be refined from the user's live debug captures.
 */
object HondaPidCatalog {

    data class ProfilePack(
        val id: String,
        val title: String,
        val description: String,
        val module: PidCategory,
        val pids: List<PidDefinition>,
    )

    private fun m22(
        id: String, label: String, unit: String, cat: PidCategory,
        bytes: Int = 2, profile: String, decode: (IntArray) -> Double? = { d ->
            if (d.isNotEmpty()) d[0].toDouble() else null
        },
    ) = PidDefinition(
        id = id, request = id, label = label, unit = unit,
        category = cat, dataBytes = bytes, profile = profile, decode = decode,
    )

    private fun tempLike(d: IntArray) = if (d.isNotEmpty()) (d[0] - 40).toDouble() else null
    private fun pctLike(d: IntArray) = if (d.isNotEmpty()) d[0] * 100.0 / 255.0 else null
    private fun u16(d: IntArray) = if (d.size >= 2) (d[0] * 256 + d[1]).toDouble() else null

    val transmission = ProfilePack(
        id = "honda_tcm",
        title = "Honda Transmission (TCM)",
        description = "ATF, gear/selector, TC lock/slip, solenoids, line pressure",
        module = PidCategory.TRANSMISSION,
        pids = listOf(
            m22("221101", "ATF temperature", "\u00B0C", PidCategory.TRANSMISSION, 1, "honda_tcm", ::tempLike),
            m22("221102", "Transmission fluid temp (alt)", "\u00B0C", PidCategory.TRANSMISSION, 1, "honda_tcm", ::tempLike),
            m22("221201", "Current gear (raw)", "", PidCategory.TRANSMISSION, 1, "honda_tcm"),
            m22("221202", "Range selector (PRND)", "", PidCategory.TRANSMISSION, 1, "honda_tcm"),
            m22("221203", "Gear ratio (live)", "", PidCategory.TRANSMISSION, 2, "honda_tcm") { d ->
                if (d.size >= 2) (d[0] * 256 + d[1]) / 1000.0 else null
            },
            m22("221204", "Input shaft RPM", "rpm", PidCategory.TRANSMISSION, 2, "honda_tcm", ::u16),
            m22("221205", "Output shaft RPM", "rpm", PidCategory.TRANSMISSION, 2, "honda_tcm", ::u16),
            m22("221206", "TC slip RPM", "rpm", PidCategory.TRANSMISSION, 2, "honda_tcm", ::u16),
            m22("221207", "TC lock-up status", "", PidCategory.TRANSMISSION, 1, "honda_tcm"),
            m22("221208", "Line pressure", "kPa", PidCategory.TRANSMISSION, 2, "honda_tcm", ::u16),
            m22("221209", "Shift solenoid A", "%", PidCategory.TRANSMISSION, 1, "honda_tcm", ::pctLike),
            m22("22120A", "Shift solenoid B", "%", PidCategory.TRANSMISSION, 1, "honda_tcm", ::pctLike),
            m22("22120B", "Shift solenoid C", "%", PidCategory.TRANSMISSION, 1, "honda_tcm", ::pctLike),
            m22("22120C", "Shift solenoid D", "%", PidCategory.TRANSMISSION, 1, "honda_tcm", ::pctLike),
            m22("22120D", "Transmission load", "%", PidCategory.TRANSMISSION, 1, "honda_tcm", ::pctLike),
            m22("22120E", "Kickdown status", "", PidCategory.TRANSMISSION, 1, "honda_tcm"),
            m22("22120F", "Adaptive learning status", "", PidCategory.TRANSMISSION, 1, "honda_tcm"),
        ),
    )

    val engine = ProfilePack(
        id = "honda_engine",
        title = "Honda Engine enhanced",
        description = "Oil temp/life, injector PW, pedal, fan, knock, misfire",
        module = PidCategory.ENGINE,
        pids = listOf(
            m22("221301", "Oil temperature", "\u00B0C", PidCategory.TEMPS, 1, "honda_engine", ::tempLike),
            m22("221302", "Oil pressure", "kPa", PidCategory.ENGINE, 2, "honda_engine", ::u16),
            m22("221303", "Oil life remaining", "%", PidCategory.ENGINE, 1, "honda_engine", ::pctLike),
            m22("221304", "Injector pulse width", "ms", PidCategory.FUEL, 2, "honda_engine") { d ->
                if (d.size >= 2) (d[0] * 256 + d[1]) / 1000.0 else null
            },
            m22("221305", "Accelerator pedal position", "%", PidCategory.ENGINE, 1, "honda_engine", ::pctLike),
            m22("221306", "Radiator fan stage", "", PidCategory.ENGINE, 1, "honda_engine"),
            m22("221307", "Knock count", "", PidCategory.ENGINE, 1, "honda_engine"),
            m22("221308", "Misfire cyl 1", "", PidCategory.ENGINE, 2, "honda_engine", ::u16),
            m22("221309", "Misfire cyl 2", "", PidCategory.ENGINE, 2, "honda_engine", ::u16),
            m22("22130A", "Misfire cyl 3", "", PidCategory.ENGINE, 2, "honda_engine", ::u16),
            m22("22130B", "Misfire cyl 4", "", PidCategory.ENGINE, 2, "honda_engine", ::u16),
            m22("22130C", "ECU voltage", "V", PidCategory.ELECTRICAL, 2, "honda_engine") { d ->
                if (d.size >= 2) (d[0] * 256 + d[1]) / 1000.0 else null
            },
            // Fuel pump / rail / idle — candidates; ColdStartIdleCatalog also probes these.
            m22("221310", "Fuel pump pressure (cand A)", "kPa", PidCategory.FUEL, 2, "honda_engine", ::u16),
            m22("221311", "Fuel pump pressure (cand B)", "kPa", PidCategory.FUEL, 2, "honda_engine", ::u16),
            m22("221312", "Fuel rail pressure (Honda)", "kPa", PidCategory.FUEL, 2, "honda_engine") { d ->
                if (d.size >= 2) (d[0] * 256 + d[1]) * 0.1 else null
            },
            m22("221313", "Fuel pump duty / command", "%", PidCategory.FUEL, 1, "honda_engine", ::pctLike),
            m22("221314", "Target idle RPM", "rpm", PidCategory.ENGINE, 2, "honda_engine", ::u16),
            m22("221315", "IAC / idle air command", "%", PidCategory.ENGINE, 1, "honda_engine", ::pctLike),
            m22("221316", "Total misfire count", "", PidCategory.ENGINE, 2, "honda_engine", ::u16),
        ),
    )

    val abs = ProfilePack(
        id = "honda_abs",
        title = "Honda ABS / VSA",
        description = "Wheel speeds, brake switch",
        module = PidCategory.ABS,
        pids = listOf(
            m22("221401", "FL wheel speed", "km/h", PidCategory.ABS, 2, "honda_abs", ::u16),
            m22("221402", "FR wheel speed", "km/h", PidCategory.ABS, 2, "honda_abs", ::u16),
            m22("221403", "RL wheel speed", "km/h", PidCategory.ABS, 2, "honda_abs", ::u16),
            m22("221404", "RR wheel speed", "km/h", PidCategory.ABS, 2, "honda_abs", ::u16),
            m22("221405", "Brake switch", "", PidCategory.ABS, 1, "honda_abs"),
        ),
    )

    val eps = ProfilePack(
        id = "honda_eps",
        title = "Honda EPS (steering)",
        description = "Steering angle / torque (read-only; calibration is separate)",
        module = PidCategory.EPS,
        pids = listOf(
            m22("221501", "Steering angle", "\u00B0", PidCategory.EPS, 2, "honda_eps") { d ->
                if (d.size >= 2) (d[0] * 256 + d[1] - 32768) / 10.0 else null
            },
            m22("221502", "Steering torque", "Nm", PidCategory.EPS, 2, "honda_eps") { d ->
                if (d.size >= 2) (d[0] * 256 + d[1] - 32768) / 100.0 else null
            },
        ),
    )

    val srs = ProfilePack(
        id = "honda_srs",
        title = "Honda SRS / Airbag",
        description = "SRS status bytes (read-only)",
        module = PidCategory.SRS,
        pids = listOf(
            m22("221601", "SRS status", "", PidCategory.SRS, 1, "honda_srs"),
            m22("221602", "Seatbelt status", "", PidCategory.SRS, 1, "honda_srs"),
        ),
    )

    val body = ProfilePack(
        id = "honda_body",
        title = "Honda Body / BCM",
        description = "Body control live data",
        module = PidCategory.BODY,
        pids = listOf(
            m22("221701", "Ignition switch", "", PidCategory.BODY, 1, "honda_body"),
            m22("221702", "Door status", "", PidCategory.BODY, 1, "honda_body"),
            m22("221703", "Battery sensor voltage", "V", PidCategory.ELECTRICAL, 2, "honda_body") { d ->
                if (d.size >= 2) (d[0] * 256 + d[1]) / 1000.0 else null
            },
        ),
    )

    val climate = ProfilePack(
        id = "honda_climate",
        title = "Honda Climate / HVAC",
        description = "AC compressor, pressure, ambient",
        module = PidCategory.CLIMATE,
        pids = listOf(
            m22("221801", "AC compressor status", "", PidCategory.CLIMATE, 1, "honda_climate"),
            m22("221802", "AC pressure", "kPa", PidCategory.CLIMATE, 2, "honda_climate", ::u16),
            m22("221803", "Cabin / ambient (HVAC)", "\u00B0C", PidCategory.CLIMATE, 1, "honda_climate", ::tempLike),
        ),
    )

    val tpms = ProfilePack(
        id = "honda_tpms",
        title = "Honda TPMS",
        description = "Tire pressures (if equipped)",
        module = PidCategory.TPMS,
        pids = listOf(
            m22("221901", "FL tire pressure", "kPa", PidCategory.TPMS, 1, "honda_tpms", ::aLike),
            m22("221902", "FR tire pressure", "kPa", PidCategory.TPMS, 1, "honda_tpms", ::aLike),
            m22("221903", "RL tire pressure", "kPa", PidCategory.TPMS, 1, "honda_tpms", ::aLike),
            m22("221904", "RR tire pressure", "kPa", PidCategory.TPMS, 1, "honda_tpms", ::aLike),
        ),
    )

    private fun aLike(d: IntArray) = if (d.isNotEmpty()) d[0].toDouble() else null

    val allPacks: List<ProfilePack> = listOf(
        transmission, engine, abs, eps, srs, body, climate, tpms,
    )

    val allPids: List<PidDefinition> = allPacks.flatMap { it.pids }

    fun pack(id: String): ProfilePack? = allPacks.find { it.id == id }
}
