package com.fb2.obd.obd

/**
 * Full SAE J1979 Mode 01 catalog used by the custom sensor picker and the
 * Honda-profile probe. Entries with known formulas decode to engineering units;
 * the rest decode the first data byte as a raw value so they remain usable.
 *
 * Coverage: SAE J1979 Mode 01 live PIDs commonly listed through ~0xB6
 * (support bitmasks still decide what each car actually answers).
 */
object StandardPidCatalog {

    private fun a(d: IntArray) = if (d.isNotEmpty()) d[0].toDouble() else null
    private fun a40(d: IntArray) = if (d.isNotEmpty()) (d[0] - 40).toDouble() else null
    private fun pct(d: IntArray) = if (d.isNotEmpty()) d[0] * 100.0 / 255.0 else null
    private fun trim(d: IntArray) = if (d.isNotEmpty()) (d[0] - 128) * 100.0 / 128.0 else null
    private fun ab256(d: IntArray) = if (d.size >= 2) (d[0] * 256 + d[1]).toDouble() else null
    private fun maf(d: IntArray) = if (d.size >= 2) (d[0] * 256 + d[1]) / 100.0 else null
    private fun rpm(d: IntArray) = if (d.size >= 2) (d[0] * 256 + d[1]) / 4.0 else null
    private fun volts(d: IntArray) = if (d.size >= 2) (d[0] * 256 + d[1]) / 1000.0 else null
    private fun o2v(d: IntArray) = if (d.isNotEmpty()) d[0] / 200.0 else null
    private fun timing(d: IntArray) = if (d.isNotEmpty()) d[0] / 2.0 - 64.0 else null
    private fun fuelPress(d: IntArray) = if (d.isNotEmpty()) d[0] * 3.0 else null
    private fun rail(d: IntArray) = if (d.size >= 2) (d[0] * 256 + d[1]) * 0.1 else null
    private fun railAbs(d: IntArray) = if (d.size >= 2) (d[0] * 256 + d[1]) * 10.0 else null
    private fun lambda(d: IntArray) = if (d.size >= 2) (d[0] * 256 + d[1]) * 2.0 / 65535.0 else null
    private fun afr(d: IntArray) = lambda(d)?.times(14.7)
    private fun raw(n: Int) = { d: IntArray -> if (d.isNotEmpty()) d[0].toDouble() else null }

    private fun entry(
        num: Int, label: String, unit: String, cat: PidCategory, bytes: Int,
        decode: (IntArray) -> Double?,
    ) = PidDefinition(
        id = "01%02X".format(num),
        request = "01%02X".format(num),
        label = label,
        unit = unit,
        category = cat,
        dataBytes = bytes,
        profile = "SAE",
        decode = decode,
    )

    val all: List<PidDefinition> = listOf(
        entry(0x01, "Monitor status", "", PidCategory.EMISSIONS, 4, raw(4)),
        entry(0x03, "Fuel system status", "", PidCategory.FUEL, 2, ::a),
        entry(0x04, "Engine load", "%", PidCategory.ENGINE, 1, ::pct),
        entry(0x05, "Coolant temp", "\u00B0C", PidCategory.TEMPS, 1, ::a40),
        entry(0x06, "STFT Bank 1", "%", PidCategory.FUEL, 1, ::trim),
        entry(0x07, "LTFT Bank 1", "%", PidCategory.FUEL, 1, ::trim),
        entry(0x08, "STFT Bank 2", "%", PidCategory.FUEL, 1, ::trim),
        entry(0x09, "LTFT Bank 2", "%", PidCategory.FUEL, 1, ::trim),
        entry(0x0A, "Fuel pressure", "kPa", PidCategory.FUEL, 1, ::fuelPress),
        entry(0x0B, "MAP", "kPa", PidCategory.AIR, 1, ::a),
        entry(0x0C, "RPM", "rpm", PidCategory.ENGINE, 2, ::rpm),
        entry(0x0D, "Speed", "km/h", PidCategory.ENGINE, 1, ::a),
        entry(0x0E, "Timing advance", "\u00B0", PidCategory.ENGINE, 1, ::timing),
        entry(0x0F, "Intake temp", "\u00B0C", PidCategory.TEMPS, 1, ::a40),
        entry(0x10, "MAF", "g/s", PidCategory.AIR, 2, ::maf),
        entry(0x11, "Throttle", "%", PidCategory.ENGINE, 1, ::pct),
        entry(0x12, "Secondary air status", "", PidCategory.EMISSIONS, 1, ::a),
        entry(0x13, "O2 sensors present", "", PidCategory.EMISSIONS, 1, ::a),
        entry(0x14, "O2 B1S1 voltage", "V", PidCategory.FUEL, 2, ::o2v),
        entry(0x15, "O2 B1S2 voltage", "V", PidCategory.FUEL, 2, ::o2v),
        entry(0x16, "O2 B1S3 voltage", "V", PidCategory.FUEL, 2, ::o2v),
        entry(0x17, "O2 B1S4 voltage", "V", PidCategory.FUEL, 2, ::o2v),
        entry(0x18, "O2 B2S1 voltage", "V", PidCategory.FUEL, 2, ::o2v),
        entry(0x19, "O2 B2S2 voltage", "V", PidCategory.FUEL, 2, ::o2v),
        entry(0x1A, "O2 B2S3 voltage", "V", PidCategory.FUEL, 2, ::o2v),
        entry(0x1B, "O2 B2S4 voltage", "V", PidCategory.FUEL, 2, ::o2v),
        entry(0x1C, "OBD standards", "", PidCategory.EMISSIONS, 1, ::a),
        entry(0x1D, "O2 sensors present (alt)", "", PidCategory.EMISSIONS, 1, ::a),
        entry(0x1E, "Aux input status", "", PidCategory.OTHER, 1, ::a),
        entry(0x1F, "Run time since start", "s", PidCategory.ENGINE, 2, ::ab256),
        entry(0x21, "Distance with MIL on", "km", PidCategory.EMISSIONS, 2, ::ab256),
        entry(0x22, "Fuel rail pressure (rel)", "kPa", PidCategory.FUEL, 2, ::rail),
        entry(0x23, "Fuel rail pressure (abs)", "kPa", PidCategory.FUEL, 2, ::railAbs),
        entry(0x24, "O2 S1 lambda", "", PidCategory.FUEL, 4, ::lambda),
        PidDefinition(
            id = "0124I",
            request = "0124",
            label = "O2 S1 wide-range current",
            unit = "mA",
            category = PidCategory.FUEL,
            dataBytes = 4,
            profile = "SAE",
            decode = { d ->
                if (d.size >= 4) (d[2] * 256 + d[3]) / 256.0 - 128.0 else null
            },
        ),
        entry(0x25, "O2 S2 lambda", "", PidCategory.FUEL, 4, ::lambda),
        entry(0x26, "O2 S3 lambda", "", PidCategory.FUEL, 4, ::lambda),
        entry(0x27, "O2 S4 lambda", "", PidCategory.FUEL, 4, ::lambda),
        entry(0x28, "O2 S5 lambda", "", PidCategory.FUEL, 4, ::lambda),
        entry(0x29, "O2 S6 lambda", "", PidCategory.FUEL, 4, ::lambda),
        entry(0x2A, "O2 S7 lambda", "", PidCategory.FUEL, 4, ::lambda),
        entry(0x2B, "O2 S8 lambda", "", PidCategory.FUEL, 4, ::lambda),
        entry(0x2C, "Commanded EGR", "%", PidCategory.EMISSIONS, 1, ::pct),
        entry(0x2D, "EGR error", "%", PidCategory.EMISSIONS, 1, ::trim),
        entry(0x2E, "Commanded EVAP purge", "%", PidCategory.EMISSIONS, 1, ::pct),
        entry(0x2F, "Fuel level", "%", PidCategory.FUEL, 1, ::pct),
        entry(0x30, "Warm-ups since clear", "", PidCategory.EMISSIONS, 1, ::a),
        entry(0x31, "Distance since clear", "km", PidCategory.EMISSIONS, 2, ::ab256),
        entry(0x32, "EVAP vapor pressure", "Pa", PidCategory.EMISSIONS, 2) { d ->
            if (d.size >= 2) ((d[0] * 256 + d[1]).toShort()).toDouble() / 4.0 else null
        },
        entry(0x33, "Barometric pressure", "kPa", PidCategory.AIR, 1, ::a),
        entry(0x34, "O2 S1 AFR", "AFR", PidCategory.FUEL, 4, ::afr),
        entry(0x35, "O2 S2 AFR", "AFR", PidCategory.FUEL, 4, ::afr),
        entry(0x36, "O2 S3 AFR", "AFR", PidCategory.FUEL, 4, ::afr),
        entry(0x37, "O2 S4 AFR", "AFR", PidCategory.FUEL, 4, ::afr),
        entry(0x38, "O2 S5 AFR", "AFR", PidCategory.FUEL, 4, ::afr),
        entry(0x39, "O2 S6 AFR", "AFR", PidCategory.FUEL, 4, ::afr),
        entry(0x3A, "O2 S7 AFR", "AFR", PidCategory.FUEL, 4, ::afr),
        entry(0x3B, "O2 S8 AFR", "AFR", PidCategory.FUEL, 4, ::afr),
        entry(0x3C, "Catalyst temp B1S1", "\u00B0C", PidCategory.TEMPS, 2) { d ->
            if (d.size >= 2) (d[0] * 256 + d[1]) / 10.0 - 40.0 else null
        },
        entry(0x3D, "Catalyst temp B2S1", "\u00B0C", PidCategory.TEMPS, 2) { d ->
            if (d.size >= 2) (d[0] * 256 + d[1]) / 10.0 - 40.0 else null
        },
        entry(0x3E, "Catalyst temp B1S2", "\u00B0C", PidCategory.TEMPS, 2) { d ->
            if (d.size >= 2) (d[0] * 256 + d[1]) / 10.0 - 40.0 else null
        },
        entry(0x3F, "Catalyst temp B2S2", "\u00B0C", PidCategory.TEMPS, 2) { d ->
            if (d.size >= 2) (d[0] * 256 + d[1]) / 10.0 - 40.0 else null
        },
        entry(0x41, "Monitor status this cycle", "", PidCategory.EMISSIONS, 4, raw(4)),
        entry(0x42, "Control module voltage", "V", PidCategory.ELECTRICAL, 2, ::volts),
        entry(0x43, "Absolute load", "%", PidCategory.ENGINE, 2) { d ->
            if (d.size >= 2) (d[0] * 256 + d[1]) * 100.0 / 255.0 else null
        },
        entry(0x44, "Commanded AFR / EQ ratio", "", PidCategory.FUEL, 2, ::lambda),
        entry(0x45, "Relative throttle", "%", PidCategory.ENGINE, 1, ::pct),
        entry(0x46, "Ambient air temp", "\u00B0C", PidCategory.TEMPS, 1, ::a40),
        entry(0x47, "Absolute throttle B", "%", PidCategory.ENGINE, 1, ::pct),
        entry(0x48, "Absolute throttle C", "%", PidCategory.ENGINE, 1, ::pct),
        entry(0x49, "Accelerator pedal D", "%", PidCategory.ENGINE, 1, ::pct),
        entry(0x4A, "Accelerator pedal E", "%", PidCategory.ENGINE, 1, ::pct),
        entry(0x4B, "Accelerator pedal F", "%", PidCategory.ENGINE, 1, ::pct),
        entry(0x4C, "Commanded throttle", "%", PidCategory.ENGINE, 1, ::pct),
        entry(0x4D, "Time with MIL on", "min", PidCategory.EMISSIONS, 2, ::ab256),
        entry(0x4E, "Time since codes clear", "min", PidCategory.EMISSIONS, 2, ::ab256),
        entry(0x4F, "Max values (equiv/O2/MAP/MAF)", "", PidCategory.OTHER, 4, ::a),
        entry(0x50, "Max AFR / air flow", "", PidCategory.AIR, 4, ::a),
        entry(0x51, "Fuel type", "", PidCategory.FUEL, 1, ::a),
        entry(0x52, "Ethanol fuel %", "%", PidCategory.FUEL, 1, ::pct),
        entry(0x53, "Abs EVAP vapor pressure", "kPa", PidCategory.EMISSIONS, 2) { d ->
            if (d.size >= 2) (d[0] * 256 + d[1]) / 200.0 else null
        },
        entry(0x54, "EVAP vapor pressure", "Pa", PidCategory.EMISSIONS, 2) { d ->
            if (d.size >= 2) (d[0] * 256 + d[1] - 32767).toDouble() else null
        },
        entry(0x55, "STFT secondary B1", "%", PidCategory.FUEL, 1, ::trim),
        entry(0x56, "LTFT secondary B1", "%", PidCategory.FUEL, 1, ::trim),
        entry(0x57, "STFT secondary B2", "%", PidCategory.FUEL, 1, ::trim),
        entry(0x58, "LTFT secondary B2", "%", PidCategory.FUEL, 1, ::trim),
        entry(0x59, "Fuel rail abs pressure", "kPa", PidCategory.FUEL, 2, ::railAbs),
        entry(0x5A, "Relative accelerator", "%", PidCategory.ENGINE, 1, ::pct),
        entry(0x5B, "Hybrid battery remaining", "%", PidCategory.ELECTRICAL, 1, ::pct),
        entry(0x5C, "Engine oil temp", "\u00B0C", PidCategory.TEMPS, 1, ::a40),
        entry(0x5D, "Fuel injection timing", "\u00B0", PidCategory.FUEL, 2) { d ->
            if (d.size >= 2) (d[0] * 256 + d[1] - 26880) / 128.0 else null
        },
        entry(0x5E, "Engine fuel rate", "L/h", PidCategory.FUEL, 2) { d ->
            if (d.size >= 2) (d[0] * 256 + d[1]) / 20.0 else null
        },
        entry(0x5F, "Emission requirements", "", PidCategory.EMISSIONS, 1, ::a),
        entry(0x61, "Driver demand torque", "%", PidCategory.ENGINE, 1) { d ->
            if (d.isNotEmpty()) d[0] - 125.0 else null
        },
        entry(0x62, "Actual engine torque", "%", PidCategory.ENGINE, 1) { d ->
            if (d.isNotEmpty()) d[0] - 125.0 else null
        },
        entry(0x63, "Engine reference torque", "Nm", PidCategory.ENGINE, 2, ::ab256),
        entry(0x64, "Engine percent torque data", "%", PidCategory.ENGINE, 5) { d ->
            if (d.isNotEmpty()) d[0] - 125.0 else null
        },
        entry(0x65, "Auxiliary inputs / outputs", "", PidCategory.OTHER, 2, ::a),
        entry(0x66, "MAF sensor A/B", "g/s", PidCategory.AIR, 5, ::maf),
        entry(0x67, "Coolant temp sensors", "\u00B0C", PidCategory.TEMPS, 3) { d ->
            if (d.size >= 3 && (d[0] and 0x02) != 0) (d[2] - 40).toDouble() else null
        },
        entry(0x68, "Intake air temp sensors", "\u00B0C", PidCategory.TEMPS, 7, ::a40),
        entry(0x69, "Commanded EGR / EGR error", "%", PidCategory.EMISSIONS, 7, ::pct),
        entry(0x6A, "Commanded diesel intake air flow", "%", PidCategory.AIR, 5, ::pct),
        entry(0x6B, "Exhaust gas recirculation temp", "\u00B0C", PidCategory.TEMPS, 5, ::a40),
        entry(0x6C, "Commanded throttle actuator", "%", PidCategory.ENGINE, 5, ::pct),
        entry(0x6D, "Fuel pressure control system", "kPa", PidCategory.FUEL, 6, ::ab256),
        entry(0x6E, "Injection pressure control system", "kPa", PidCategory.FUEL, 5, ::ab256),
        entry(0x6F, "Turbo compressor inlet pressure", "kPa", PidCategory.AIR, 3, ::a),
        entry(0x70, "Boost pressure control", "kPa", PidCategory.AIR, 9, ::a),
        entry(0x71, "Variable geometry turbo control", "%", PidCategory.AIR, 5, ::pct),
        entry(0x72, "Wastegate control", "%", PidCategory.AIR, 5, ::pct),
        entry(0x73, "Exhaust pressure", "kPa", PidCategory.AIR, 5, ::ab256),
        entry(0x74, "Turbo RPM A", "rpm", PidCategory.AIR, 5, ::ab256),
        entry(0x75, "Turbo RPM B", "rpm", PidCategory.AIR, 5, ::ab256),
        entry(0x76, "Charge air cooler temp", "\u00B0C", PidCategory.TEMPS, 7, ::a40),
        entry(0x77, "EGT Bank 1", "\u00B0C", PidCategory.TEMPS, 5) { d ->
            if (d.size >= 3) (d[1] * 256 + d[2]) / 10.0 - 40.0 else null
        },
        entry(0x78, "EGT Bank 2", "\u00B0C", PidCategory.TEMPS, 5) { d ->
            if (d.size >= 3) (d[1] * 256 + d[2]) / 10.0 - 40.0 else null
        },
        entry(0x79, "DPF bank 1", "kPa", PidCategory.EMISSIONS, 7, ::ab256),
        entry(0x7A, "DPF bank 2", "kPa", PidCategory.EMISSIONS, 7, ::ab256),
        entry(0x7B, "DPF temp", "\u00B0C", PidCategory.TEMPS, 7, ::a40),
        entry(0x7C, "DPF / NOx aftertreatment", "", PidCategory.EMISSIONS, 9, ::a),
        entry(0x7D, "NOx reagent system", "", PidCategory.EMISSIONS, 1, ::a),
        entry(0x7E, "PM sensor bank 1/2", "", PidCategory.EMISSIONS, 10, ::a),
        entry(0x7F, "Engine run time", "s", PidCategory.ENGINE, 13, ::ab256),
        entry(0x80, "AECU runtime", "s", PidCategory.ENGINE, 4, ::ab256),
        entry(0x81, "AECU runtime (alt)", "s", PidCategory.ENGINE, 5, ::ab256),
        entry(0x82, "NOx sensor (alt)", "ppm", PidCategory.EMISSIONS, 5, ::ab256),
        entry(0x83, "NOx sensor", "ppm", PidCategory.EMISSIONS, 5, ::ab256),
        entry(0x84, "Manifold surface temp", "\u00B0C", PidCategory.TEMPS, 5, ::a40),
        entry(0x85, "NOx reagent system temp", "\u00B0C", PidCategory.TEMPS, 5, ::a40),
        entry(0x86, "PM sensor", "", PidCategory.EMISSIONS, 5, ::a),
        entry(0x87, "Intake manifold abs pressure", "kPa", PidCategory.AIR, 5, ::ab256),
        entry(0x88, "SCR inducement system", "", PidCategory.EMISSIONS, 13, ::a),
        entry(0x89, "EGT sensors", "\u00B0C", PidCategory.TEMPS, 5, ::a40),
        entry(0x8A, "EGT sensors (alt)", "\u00B0C", PidCategory.TEMPS, 5, ::a40),
        entry(0x8B, "Diesel aftertreatment status", "", PidCategory.EMISSIONS, 5, ::a),
        entry(0x8C, "O2 sensor wide-range", "V", PidCategory.FUEL, 5, ::volts),
        entry(0x8D, "Throttle position G", "%", PidCategory.ENGINE, 1, ::pct),
        entry(0x8E, "Engine friction torque", "%", PidCategory.ENGINE, 1) { d ->
            if (d.isNotEmpty()) d[0] - 125.0 else null
        },
        entry(0x8F, "PM sensor bank 1/2 (alt)", "", PidCategory.EMISSIONS, 5, ::a),
        entry(0x90, "WWH-OBD vehicle OBD info", "", PidCategory.EMISSIONS, 3, ::a),
        entry(0x91, "Fuel system status (alt)", "", PidCategory.FUEL, 5, ::a),
        entry(0x92, "Engine percent torque data", "%", PidCategory.ENGINE, 2) { d ->
            if (d.isNotEmpty()) d[0] - 125.0 else null
        },
        entry(0x93, "Engine exhaust flow rate", "kg/h", PidCategory.AIR, 3, ::ab256),
        entry(0x94, "Fuel system % use", "%", PidCategory.FUEL, 12, ::pct),
        entry(0x98, "EGT sensors 3", "\u00B0C", PidCategory.TEMPS, 9, ::a40),
        entry(0x99, "EGT sensors 4", "\u00B0C", PidCategory.TEMPS, 9, ::a40),
        entry(0x9A, "Hybrid/EV system status", "", PidCategory.ELECTRICAL, 6, ::a),
        entry(0x9B, "Diesel exhaust fluid temp", "\u00B0C", PidCategory.TEMPS, 5, ::a40),
        entry(0x9C, "O2 sensor wide-range (alt)", "", PidCategory.FUEL, 5, ::a),
        entry(0x9D, "Fuel rate (alt)", "g/s", PidCategory.FUEL, 4) { d ->
            if (d.size >= 2) (d[0] * 256 + d[1]) / 20.0 else null
        },
        entry(0x9E, "Engine exhaust flow rate (alt)", "kg/h", PidCategory.AIR, 2, ::ab256),
        entry(0x9F, "Fuel system % use (alt)", "%", PidCategory.FUEL, 4, ::pct),
        entry(0xA1, "NOx concentration corrected", "ppm", PidCategory.EMISSIONS, 9, ::ab256),
        entry(0xA2, "Cylinder fuel rate", "mg/stroke", PidCategory.FUEL, 2) { d ->
            if (d.size >= 2) (d[0] * 256 + d[1]) / 32.0 else null
        },
        entry(0xA3, "Evap system vapor pressure", "Pa", PidCategory.EMISSIONS, 9, ::ab256),
        entry(0xA4, "Transmission actual gear ratio", "", PidCategory.TRANSMISSION, 4) { d ->
            if (d.size >= 4 && (d[0] and 0x02) != 0) ((256 * d[2]) + d[3]) / 1000.0 else null
        },
        entry(0xA5, "Commanded DEF dosing", "", PidCategory.EMISSIONS, 4, ::a),
        entry(0xA6, "Odometer", "km", PidCategory.OTHER, 4) { d ->
            if (d.size >= 4) {
                ((d[0].toLong() shl 24) or (d[1].toLong() shl 16) or
                    (d[2].toLong() shl 8) or d[3].toLong()) / 10.0
            } else null
        },
        entry(0xA7, "NOx warning / inducement", "", PidCategory.EMISSIONS, 4, ::a),
        entry(0xA8, "Diesel particulate filter", "", PidCategory.EMISSIONS, 4, ::a),
        entry(0xA9, "Fuel system status (extended)", "", PidCategory.FUEL, 4, ::a),
        entry(0xAA, "Particulate control status", "", PidCategory.EMISSIONS, 4, ::a),
        entry(0xAB, "Distance since DPF regen", "km", PidCategory.EMISSIONS, 2, ::ab256),
        entry(0xAC, "O2 sensor concentration", "", PidCategory.FUEL, 4, ::lambda),
        entry(0xAD, "O2 sensor concentration (alt)", "", PidCategory.FUEL, 4, ::lambda),
        entry(0xAE, "Throttle / pedal absolute", "%", PidCategory.ENGINE, 2, ::pct),
        entry(0xAF, "Commanded/actual equivalence", "", PidCategory.FUEL, 4, ::lambda),
        entry(0xB0, "O2 sensor AFR (wide)", "AFR", PidCategory.FUEL, 4, ::afr),
        entry(0xB1, "Diesel aftertreatment history", "", PidCategory.EMISSIONS, 4, ::a),
        entry(0xB2, "O2 sensor AFR (wide alt)", "AFR", PidCategory.FUEL, 4, ::afr),
        entry(0xB3, "Fuel pressure control", "%", PidCategory.FUEL, 2, ::pct),
        entry(0xB4, "Injection pressure control", "%", PidCategory.FUEL, 2, ::pct),
        entry(0xB5, "Turbo compressor inlet temp", "\u00B0C", PidCategory.TEMPS, 3, ::a40),
        entry(0xB6, "Charge air cooler efficiency", "%", PidCategory.AIR, 1, ::pct),
    )

    fun byId(id: String): PidDefinition? = all.find { it.id.equals(id, ignoreCase = true) }

    fun byCategory(cat: PidCategory): List<PidDefinition> = all.filter { it.category == cat }

    fun fuelPageDefaults(): List<PidDefinition> = listOfNotNull(
        byId("0106"), byId("0107"), byId("0108"), byId("0109"),
        byId("0103"), byId("0114"), byId("0115"),
        byId("0124"), byId("0134"), byId("0144"),
        byId("0122"), byId("0123"), byId("0159"), byId("015E"),
    )
}
