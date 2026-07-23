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

/** Plain-language descriptions + AI-style cause hints for common codes. */
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
        "P0122" to "Throttle/pedal position sensor A low",
        "P0123" to "Throttle/pedal position sensor A high",
        "P0500" to "Vehicle speed sensor",
        "P0507" to "Idle control system RPM higher than expected",
        "P0600" to "Serial communication link",
        "P0606" to "ECM/PCM processor",
        "P0685" to "ECM/PCM power relay control circuit",
        "P0705" to "Transmission range sensor",
        "P0720" to "Output speed sensor",
        "P0730" to "Incorrect gear ratio",
        "P0841" to "Transmission fluid pressure sensor",
        "C1234" to "ABS wheel speed sensor (generic)",
        "B0001" to "Driver airbag circuit (generic)",
        "U0100" to "Lost communication with ECM/PCM",
        "U0101" to "Lost communication with TCM",
        "U0121" to "Lost communication with ABS",
        "U0155" to "Lost communication with instrument cluster",
        // Expanded multi-brand / Honda-relevant set
        "P0001" to "Fuel volume regulator control circuit",
        "P0010" to "A camshaft position actuator circuit (Bank 1)",
        "P0011" to "A camshaft position timing over-advanced (Bank 1)",
        "P0012" to "A camshaft position timing over-retarded (Bank 1)",
        "P0021" to "A camshaft position timing over-advanced (Bank 2)",
        "P0031" to "O2 heater control circuit low (B1S1)",
        "P0037" to "O2 heater control circuit low (B1S2)",
        "P0051" to "O2 heater control circuit low (B2S1)",
        "P0068" to "MAP/MAF - throttle position correlation",
        "P0087" to "Fuel rail/system pressure too low",
        "P0088" to "Fuel rail/system pressure too high",
        "P0090" to "Fuel pressure regulator control circuit",
        "P0105" to "MAP/baro sensor circuit",
        "P0112" to "Intake air temp sensor low input",
        "P0121" to "Throttle/pedal position sensor A range/performance",
        "P0130" to "O2 sensor circuit (B1S1)",
        "P0132" to "O2 sensor high voltage (B1S1)",
        "P0134" to "O2 sensor circuit no activity (B1S1)",
        "P0136" to "O2 sensor circuit (B1S2)",
        "P0138" to "O2 sensor high voltage (B1S2)",
        "P0141" to "O2 sensor heater circuit (B1S2)",
        "P0151" to "O2 sensor low voltage (B2S1)",
        "P0153" to "O2 sensor slow response (B2S1)",
        "P0155" to "O2 sensor heater circuit (B2S1)",
        "P0157" to "O2 sensor low voltage (B2S2)",
        "P0161" to "O2 sensor heater circuit (B2S2)",
        "P0170" to "Fuel trim malfunction (Bank 1)",
        "P0174" to "System too lean (Bank 2)",
        "P0175" to "System too rich (Bank 2)",
        "P0201" to "Injector circuit Cylinder 1",
        "P0202" to "Injector circuit Cylinder 2",
        "P0203" to "Injector circuit Cylinder 3",
        "P0204" to "Injector circuit Cylinder 4",
        "P0222" to "Throttle/pedal position sensor B low",
        "P0223" to "Throttle/pedal position sensor B high",
        "P0305" to "Cylinder 5 misfire",
        "P0306" to "Cylinder 6 misfire",
        "P0315" to "Crankshaft position system variation not learned",
        "P0328" to "Knock sensor 1 circuit high",
        "P0336" to "Crankshaft position sensor A range/performance",
        "P0341" to "Camshaft position sensor A range/performance",
        "P0351" to "Ignition coil A primary/secondary",
        "P0352" to "Ignition coil B primary/secondary",
        "P0353" to "Ignition coil C primary/secondary",
        "P0354" to "Ignition coil D primary/secondary",
        "P0400" to "EGR flow malfunction",
        "P0403" to "EGR control circuit",
        "P0404" to "EGR control circuit range/performance",
        "P0440" to "EVAP system malfunction",
        "P0442" to "EVAP system small leak",
        "P0443" to "EVAP purge control valve circuit",
        "P0446" to "EVAP vent control circuit",
        "P0456" to "EVAP system very small leak",
        "P0461" to "Fuel level sensor range/performance",
        "P0462" to "Fuel level sensor low input",
        "P0463" to "Fuel level sensor high input",
        "P0480" to "Cooling fan 1 control circuit",
        "P0496" to "EVAP flow during non-purge",
        "P0506" to "Idle control system RPM lower than expected",
        "P0522" to "Engine oil pressure sensor low",
        "P0532" to "A/C refrigerant pressure sensor low",
        "P0550" to "Power steering pressure sensor circuit",
        "P0571" to "Cruise/brake switch A circuit",
        "P0601" to "Internal control module memory checksum",
        "P0607" to "Control module performance",
        "P0615" to "Starter relay circuit",
        "P0627" to "Fuel pump A control circuit open",
        "P0630" to "VIN not programmed or mismatch",
        "P0641" to "Sensor reference voltage A circuit open",
        "P0651" to "Sensor reference voltage B circuit open",
        "P0688" to "ECM/PCM power relay sense circuit",
        "P0701" to "Transmission control system range/performance",
        "P0702" to "Transmission control system electrical",
        "P0706" to "Transmission range sensor range/performance",
        "P0710" to "Transmission fluid temperature sensor",
        "P0711" to "Transmission fluid temperature sensor range/performance",
        "P0712" to "Transmission fluid temperature sensor low",
        "P0713" to "Transmission fluid temperature sensor high",
        "P0716" to "Input/turbine speed sensor range/performance",
        "P0717" to "Input/turbine speed sensor no signal",
        "P0721" to "Output speed sensor range/performance",
        "P0722" to "Output speed sensor no signal",
        "P0725" to "Engine speed input circuit",
        "P0731" to "Gear 1 incorrect ratio",
        "P0732" to "Gear 2 incorrect ratio",
        "P0733" to "Gear 3 incorrect ratio",
        "P0734" to "Gear 4 incorrect ratio",
        "P0735" to "Gear 5 incorrect ratio",
        "P0742" to "Torque converter clutch stuck on",
        "P0743" to "Torque converter clutch electrical",
        "P0748" to "Pressure control solenoid A electrical",
        "P0750" to "Shift solenoid A",
        "P0751" to "Shift solenoid A performance/stuck off",
        "P0752" to "Shift solenoid A stuck on",
        "P0753" to "Shift solenoid A electrical",
        "P0755" to "Shift solenoid B",
        "P0756" to "Shift solenoid B performance/stuck off",
        "P0757" to "Shift solenoid B stuck on",
        "P0758" to "Shift solenoid B electrical",
        "P0760" to "Shift solenoid C",
        "P0761" to "Shift solenoid C performance/stuck off",
        "P0776" to "Pressure control solenoid B performance",
        "P0777" to "Pressure control solenoid B stuck on",
        "P0810" to "Transmission fluid pressure control",
        "P0840" to "Transmission fluid pressure sensor A",
        "P0845" to "Transmission fluid pressure sensor B",
        "P0962" to "Pressure control solenoid A control circuit low",
        "P0963" to "Pressure control solenoid A control circuit high",
        "P0973" to "Shift solenoid A control circuit low",
        "P0974" to "Shift solenoid A control circuit high",
        "P1106" to "MAP sensor circuit intermittent high (Honda)",
        "P1107" to "MAP sensor circuit intermittent low (Honda)",
        "P1128" to "MAP lower than expected (Honda)",
        "P1129" to "MAP higher than expected (Honda)",
        "P1166" to "A/F sensor range/performance (Honda)",
        "P1167" to "A/F sensor heater circuit (Honda)",
        "P1259" to "VTEC system malfunction (Honda)",
        "P1298" to "ELD circuit high (Honda)",
        "P1456" to "EVAP system leak detected (Honda)",
        "P1457" to "EVAP canister vent system (Honda)",
        "P1491" to "EGR valve lift insufficient (Honda)",
        "P1656" to "Electronic throttle control (Honda)",
        "P1705" to "Transmission range switch (Honda)",
        "P1730" to "Shift control system (Honda)",
        "P1731" to "Shift control system (Honda)",
        "P1732" to "Shift control system (Honda)",
        "P1733" to "Shift control system (Honda)",
        "P1734" to "Shift control system (Honda)",
        "P1870" to "Transmission component slipping (generic)",
        "C0035" to "Left front wheel speed sensor",
        "C0040" to "Right front wheel speed sensor",
        "C0045" to "Left rear wheel speed sensor",
        "C0050" to "Right rear wheel speed sensor",
        "C0060" to "ABS pump motor",
        "C0110" to "Pump motor circuit",
        "C0121" to "Valve relay circuit",
        "C1235" to "ABS wheel speed sensor (generic)",
        "B0028" to "Right side airbag deployment control",
        "B0051" to "Deployment commanded",
        "B1000" to "ECU malfunction (body)",
        "B1200" to "Climate control A/C pressure",
        "U0073" to "Control module communication bus off",
        "U0122" to "Lost communication with vehicle dynamics",
        "U0140" to "Lost communication with body control",
        "U0151" to "Lost communication with restraints",
        "U0164" to "Lost communication with HVAC",
        "U0184" to "Lost communication with radio",
        "U0401" to "Invalid data received from ECM/PCM",
        "U0415" to "Invalid data received from ABS",
    )

    private val advice = mapOf(
        "P0562" to "Charging voltage is low. Check battery terminals, ground straps, alternator output at idle vs 2000 rpm, and the battery sensor (if equipped).",
        "P0171" to "ECU is adding fuel (lean). Check for intake leaks, dirty MAF, low fuel pressure, or a stuck-open EVAP purge valve.",
        "P0172" to "ECU is removing fuel (rich). Possible high fuel pressure, leaking injectors, dirty MAF, or a stuck-closed EVAP purge.",
        "P0133" to "Upstream O2 is slow. Often an aging sensor, exhaust leak before the sensor, or contaminated sensor tip.",
        "P0420" to "Catalyst efficiency low. Confirm no exhaust leaks and healthy O2 sensors before replacing the converter.",
        "P0300" to "Random misfire. Check plugs/coils, intake leaks, fuel quality, and compression if it persists.",
        "P0128" to "Thermostat may be stuck open (engine slow to warm). Verify coolant level and thermostat operation.",
        "P0741" to "Torque converter clutch not locking. Check ATF level/condition and TCM adaptive values.",
        "P0700" to "Request to check TCM codes — engine ECU is reporting a transmission fault present.",
    )

    fun describe(code: String): String =
        map[code] ?: "Manufacturer-specific or unknown \u2014 check service data"

    /** Richer "AI assistant" style explanation for the Faults screen. */
    fun explain(code: String): String {
        val base = describe(code)
        val tip = advice[code]
        return if (tip != null) "$base\n\nLikely causes / next checks:\n$tip"
        else "$base\n\nNo curated tips for this code yet — use freeze-frame and live data to confirm conditions when it set."
    }
}
