package com.fb2.obd.obd

/**
 * Curated sensors for diagnosing rough idle / cold-start issues on Civic FB2-class
 * cars (1.8L port-injection). Combines SAE Mode 01 PIDs that usually exist with
 * Honda Mode 22 candidates (misfire counts, fuel pump/rail pressure). Mode 22
 * addresses are placeholders — probe marks what the ECU actually answers.
 */
object ColdStartIdleCatalog {

    data class Section(
        val title: String,
        val hint: String,
        val pids: List<PidDefinition>,
    )

    /** Alternate Mode 22 IDs for fuel pump / rail pressure (probe which work). */
    private val fuelPressureCandidates: List<PidDefinition> = listOf(
        PidDefinition(
            id = "221310", request = "221310", label = "Fuel pump pressure (cand A)",
            unit = "kPa", category = PidCategory.FUEL, dataBytes = 2, profile = "honda_engine",
            decode = { d -> if (d.size >= 2) (d[0] * 256 + d[1]).toDouble() else null },
        ),
        PidDefinition(
            id = "221311", request = "221311", label = "Fuel pump pressure (cand B)",
            unit = "kPa", category = PidCategory.FUEL, dataBytes = 2, profile = "honda_engine",
            decode = { d -> if (d.size >= 2) (d[0] * 256 + d[1]).toDouble() else null },
        ),
        PidDefinition(
            id = "221312", request = "221312", label = "Fuel rail pressure (Honda)",
            unit = "kPa", category = PidCategory.FUEL, dataBytes = 2, profile = "honda_engine",
            decode = { d -> if (d.size >= 2) (d[0] * 256 + d[1]).toDouble() * 0.1 else null },
        ),
        PidDefinition(
            id = "221313", request = "221313", label = "Fuel pump duty / command",
            unit = "%", category = PidCategory.FUEL, dataBytes = 1, profile = "honda_engine",
            decode = { d -> if (d.isNotEmpty()) d[0] * 100.0 / 255.0 else null },
        ),
    )

    private val idleControlCandidates: List<PidDefinition> = listOf(
        PidDefinition(
            id = "221314", request = "221314", label = "Target idle RPM",
            unit = "rpm", category = PidCategory.ENGINE, dataBytes = 2, profile = "honda_engine",
            decode = { d -> if (d.size >= 2) (d[0] * 256 + d[1]).toDouble() else null },
        ),
        PidDefinition(
            id = "221315", request = "221315", label = "IAC / idle air command",
            unit = "%", category = PidCategory.ENGINE, dataBytes = 1, profile = "honda_engine",
            decode = { d -> if (d.isNotEmpty()) d[0] * 100.0 / 255.0 else null },
        ),
        PidDefinition(
            id = "221316", request = "221316", label = "Total misfire count",
            unit = "", category = PidCategory.ENGINE, dataBytes = 2, profile = "honda_engine",
            decode = { d -> if (d.size >= 2) (d[0] * 256 + d[1]).toDouble() else null },
        ),
    )

    val sections: List<Section> = listOf(
        Section(
            title = "Cold start / idle quality",
            hint = "Capture while cold (coolant < ~50°C) at idle in Park/Neutral, AC off, then again after warm-up.",
            pids = listOfNotNull(
                StandardPidCatalog.byId("010C"), // RPM
                StandardPidCatalog.byId("0105"), // Coolant
                StandardPidCatalog.byId("0167"), // Coolant2
                StandardPidCatalog.byId("010F"), // IAT
                StandardPidCatalog.byId("0146"), // Ambient
                StandardPidCatalog.byId("011F"), // Run time
                StandardPidCatalog.byId("0104"), // Load
                StandardPidCatalog.byId("0111"), // Throttle
                StandardPidCatalog.byId("010B"), // MAP
                StandardPidCatalog.byId("0110"), // MAF
                StandardPidCatalog.byId("010E"), // Timing
                StandardPidCatalog.byId("0142"), // Voltage
            ) + idleControlCandidates.filter { !it.label.contains("misfire", true) },
        ),
        Section(
            title = "Fuel trims & O2 (idle)",
            hint = "At warm idle, STFT should hover near 0 (±5%). Large positive = lean (vacuum leak / low fuel). Large negative = rich.",
            pids = listOfNotNull(
                StandardPidCatalog.byId("0106"),
                StandardPidCatalog.byId("0107"),
                StandardPidCatalog.byId("0108"),
                StandardPidCatalog.byId("0109"),
                StandardPidCatalog.byId("0114"),
                StandardPidCatalog.byId("0115"),
                StandardPidCatalog.byId("0124"),
                StandardPidCatalog.byId("0134"),
                StandardPidCatalog.byId("0144"),
            ),
        ),
        Section(
            title = "Fuel delivery / pump pressure",
            hint = "FB2 is port-injected; SAE fuel-rail PIDs are often n/s. We probe SAE + Honda candidates — send debug log if any answer.",
            pids = listOfNotNull(
                StandardPidCatalog.byId("0103"),
                StandardPidCatalog.byId("010A"),
                StandardPidCatalog.byId("0122"),
                StandardPidCatalog.byId("0123"),
                StandardPidCatalog.byId("0159"),
                StandardPidCatalog.byId("015E"),
            ) + fuelPressureCandidates +
                HondaPidCatalog.engine.pids.filter { it.label.contains("Injector", true) },
        ),
        Section(
            title = "Misfires (per cylinder)",
            hint = "Rising counts on one cylinder → plug/coil/injector/compression. Even counts on all → fuel quality, vacuum leak, or timing. Mode 22 often n/s on clones.",
            pids = HondaPidCatalog.engine.pids.filter { it.label.contains("Misfire", true) } +
                idleControlCandidates.filter { it.label.contains("misfire", true) } +
                HondaPidCatalog.engine.pids.filter { it.label.contains("Knock", true) },
        ),
    )

    val allPids: List<PidDefinition> =
        sections.flatMap { it.pids }.distinctBy { it.id }

    /**
     * Rule-of-thumb tips from probed samples. Conservative — never claims a part
     * is bad, only what to check next.
     */
    fun analyze(results: List<PidProbeResult>): List<String> {
        val byLabel = results.associateBy { it.pid.label }
        fun v(label: String): Double? = byLabel[label]?.takeIf { it.supported }?.sample

        val tips = mutableListOf<String>()

        val rpm = v("RPM")
        if (rpm != null) {
            when {
                rpm < 550 -> tips += "Idle RPM is very low (<550) — check for stalling, dirty throttle body, vacuum leaks, or IAC issues."
                rpm in 550.0..850.0 -> tips += "Idle RPM looks in a normal warm band (~${rpm.toInt()})."
                rpm > 1200 && (v("Speed") ?: 0.0) < 1 -> tips += "High idle (>1200) with vehicle stopped — thermostat/IAC/throttle stop/vacuum or pending codes."
            }
        }

        val coolant = v("Coolant temp")
        if (coolant != null && coolant < 50) {
            tips += "Engine is still cold (${coolant.toInt()}°C) — open-loop enrichment is normal; re-check after coolant > ~80°C."
        }

        val stft = v("STFT Bank 1")
        val ltft = v("LTFT Bank 1")
        if (stft != null && kotlin.math.abs(stft) > 10) {
            tips += "STFT B1 is ${"%.1f".format(stft)}% — large short-term correction; look for intake leaks, MAF dirt, or fuel delivery."
        }
        if (ltft != null && kotlin.math.abs(ltft) > 10) {
            tips += "LTFT B1 is ${"%.1f".format(ltft)}% — long-term adaptation is off; freeze-frame + smoke test / fuel pressure next."
        }

        val volts = v("Control module voltage")
        if (volts != null && volts < 12.8 && (rpm ?: 0.0) > 600) {
            tips += "Charging voltage low while running (${"%.1f".format(volts)} V) — weak battery/alternator can cause rough idle and misfires."
        }

        val misfires = listOf("Misfire cyl 1", "Misfire cyl 2", "Misfire cyl 3", "Misfire cyl 4")
            .mapNotNull { label -> v(label)?.let { label to it } }
        if (misfires.isNotEmpty()) {
            val hot = misfires.filter { it.second > 0 }
            if (hot.isEmpty()) {
                tips += "Per-cylinder misfire counters are at 0 (good for this sample)."
            } else {
                tips += "Misfire activity on: " + hot.joinToString { "${it.first.removePrefix("Misfire ")}=${it.second.toInt()}" } +
                    ". Swap coil/plug to another cylinder to confirm if the count follows the part."
            }
        } else if (results.any { it.pid.label.contains("Misfire", true) && !it.supported }) {
            tips += "Per-cylinder misfire Mode 22 PIDs returned n/s — keep the debug log; we will remap Honda misfire addresses from your capture."
        }

        val fuelSaes = listOf("Fuel pressure", "Fuel rail pressure (rel)", "Fuel rail pressure (abs)", "Fuel rail abs pressure")
        val anyFuel = fuelSaes.any { byLabel[it]?.supported == true } ||
            results.any { it.pid.label.contains("Fuel pump", true) && it.supported }
        if (!anyFuel) {
            tips += "No fuel-pressure PID answered on this ECU (common on FB2). Use injector PW + trims + rail candidates; a mechanical gauge on the rail is the gold standard if rough idle persists."
        } else {
            tips += "At least one fuel-pressure related PID answered — note the value at idle cold vs warm and under light throttle."
        }

        val map = v("MAP")
        if (map != null && rpm != null && rpm < 900) {
            // Warm idle MAP on NA 1.8 often ~25–40 kPa; very high MAP at idle → vacuum leak / late timing
            if (map > 55) tips += "MAP high at idle (${map.toInt()} kPa) — possible vacuum leak, EGR issue, or incorrect load calculation."
        }

        if (tips.isEmpty()) tips += "Probe complete — compare cold vs warm samples and share Debug log for PID refinement."
        return tips
    }
}
