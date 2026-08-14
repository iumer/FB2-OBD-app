package com.fb2.obd.obd

/**
 * Knowledge base of alternate OBD strategies used when a sensor shows n/s.
 *
 * Research summary for FB2 + cheap ELM327:
 * - Many SAE Mode 01 PIDs (Coolant2 0167, Ambient 0146, LTFT 0107) are genuinely
 *   unsupported by this Civic's ECM bitmask — not an ELM hardware failure.
 * - Honda enhanced Mode 22 packs in this app start as placeholders; real cars
 *   often need different IDs **and** ECU-specific CAN headers (ATSH), which we
 *   did not send before. That is an app/protocol gap, not "the adapter is broken".
 * - Cheap ELM clones can still fail multi-frame / multi-ECU work even with
 *   correct headers — deep search reports that honestly when everything fails.
 *
 * Each [DeepSearchStrategy] is a setup → request → teardown recipe the runner tries.
 */
data class DeepSearchStrategy(
    val id: String,
    val title: String,
    val rationale: String,
    /** AT / ELM setup commands before the PID request. */
    val setup: List<String> = emptyList(),
    /** OBD request (e.g. 0107, 221101). */
    val request: String,
    /** Expected Mode 01 data byte count when request is 01xx; Mode 22 uses flexible extract. */
    val dataBytes: Int = 2,
    val decode: (IntArray) -> Double? = { d -> if (d.isNotEmpty()) d[0].toDouble() else null },
    val unit: String = "",
    /** Commands to restore a safe ELM state after the attempt. */
    val teardown: List<String> = DEFAULT_TEARDOWN,
) {
    /** Adapter-local (no ECU) — e.g. ATRV rail voltage. */
    val isAdapterLocal: Boolean
        get() = request.equals("ATRV", ignoreCase = true)

    /** Simple broadcast Mode 01 force — safe to try even when bus is shaky. */
    val isSimpleForce: Boolean
        get() = setup.any { it.equals("ATSP0", true) } &&
            setup.none { it.startsWith("ATSP") && !it.equals("ATSP0", true) } &&
            !request.startsWith("22") &&
            setup.none { it.startsWith("ATSH") && !it.equals("ATSH7DF", true) }

    /**
     * Honda Mode 22 / TCM-enhanced recipe — excluded from Generic OBD2 deep search
     * so we never thrash ATSH for manufacturer PIDs on non-Honda cars.
     */
    val isHondaSpecific: Boolean
        get() = request.uppercase().startsWith("22") ||
            id.contains("honda", ignoreCase = true) ||
            title.contains("Honda", ignoreCase = true) ||
            title.contains("TCM", ignoreCase = true) ||
            rationale.contains("Honda", ignoreCase = true) ||
            rationale.contains("TCM", ignoreCase = true)

    companion object {
        val DEFAULT_TEARDOWN = listOf(
            "ATAR",      // auto receive
            "ATSH7DF",   // functional broadcast header (11-bit)
            "ATSP0",     // protocol auto
        )
    }
}

data class DeepSearchHit(
    val strategy: DeepSearchStrategy,
    val value: Double,
    val raw: String,
)

data class DeepSearchReport(
    val targetLabel: String,
    val targetId: String,
    val attempts: Int,
    val hit: DeepSearchHit? = null,
    val notes: List<String> = emptyList(),
) {
    val success: Boolean get() = hit != null
}

object DeepSearchKnowledgeBase {

    private fun temp(d: IntArray) = if (d.isNotEmpty()) (d[0] - 40).toDouble() else null
    private fun trim(d: IntArray) = if (d.isNotEmpty()) (d[0] - 128) * 100.0 / 128.0 else null
    private fun volts(d: IntArray) = if (d.size >= 2) (d[0] * 256 + d[1]) / 1000.0 else null
    private fun pct(d: IntArray) = if (d.isNotEmpty()) d[0] * 100.0 / 255.0 else null
    private fun u16(d: IntArray) = if (d.size >= 2) (d[0] * 256 + d[1]).toDouble() else null
    private fun a(d: IntArray) = if (d.isNotEmpty()) d[0].toDouble() else null

    private fun can11(header: String, request: String, title: String, rationale: String, bytes: Int, unit: String, decode: (IntArray) -> Double?) =
        DeepSearchStrategy(
            id = "can11_${header}_$request",
            title = title,
            rationale = rationale,
            setup = listOf("ATSP6", "ATSH$header", "ATCRA7E8", "ATFCSH$header"),
            request = request,
            dataBytes = bytes,
            decode = decode,
            unit = unit,
        )

    private fun can29(header: String, request: String, title: String, rationale: String, bytes: Int, unit: String, decode: (IntArray) -> Double?) =
        DeepSearchStrategy(
            id = "can29_${header}_$request",
            title = title,
            rationale = rationale,
            setup = listOf("ATSP6", "ATSH$header", "ATCRA18DAF110", "ATFCSH$header"),
            request = request,
            dataBytes = bytes,
            decode = decode,
            unit = unit,
        )

    private fun forceMode01(request: String, title: String, bytes: Int, unit: String, decode: (IntArray) -> Double?) =
        DeepSearchStrategy(
            id = "force_$request",
            title = title,
            rationale = "Force Mode 01 even if the ECU support bitmask said n/s (some ECUs answer anyway).",
            setup = listOf("ATSP0", "ATSH7DF"),
            request = request,
            dataBytes = bytes,
            decode = decode,
            unit = unit,
        )

    /** Resolve strategies for a catalog PID or a dashboard tile label. */
    fun strategiesFor(pid: PidDefinition?, label: String, requestHint: String? = null): List<DeepSearchStrategy> {
        val key = normalize(label, pid?.id ?: requestHint, pid?.request ?: requestHint)
        val specific = when (key) {
            "coolant2", "0167", "coolant temp sensors" -> coolant2()
            "ambient", "0146", "ambient air temp" -> ambient()
            "ltft", "0107", "ltft bank 1" -> ltft()
            "battery", "0142", "control module voltage", "ecu v" -> battery()
            "atf", "atf temperature", "221101" -> atf()
            "gear", "current gear", "221201", "gear ratio" -> gear()
            "fuel pressure", "fuel rail", "010a", "0123" -> fuelPressure()
            "misfire", "misfire cyl", "total misfire" -> misfire()
            "oil temperature", "221301" -> oilTemp()
            "ac ", "hvac", "climate", "cabin" -> climate()
            else -> emptyList()
        }
        val generic = genericFor(pid, label, requestHint)
        return (specific + generic).distinctBy { it.id }
    }

    private fun normalize(label: String, id: String?, request: String?): String {
        val raw = (request ?: id ?: label).lowercase().trim()
        val lab = label.lowercase().trim()
        return when {
            lab.contains("coolant 2") || lab.contains("coolant temp sensors") || raw.contains("0167") -> "coolant2"
            lab.contains("ambient") || raw.contains("0146") -> "ambient"
            lab.startsWith("ltft") || raw.contains("0107") -> "ltft"
            lab.contains("battery") || lab.contains("ecu v") || lab.contains("control module voltage") || raw.contains("0142") -> "battery"
            lab.contains("atf") || raw.contains("221101") || raw.contains("221102") -> "atf"
            lab.contains("gear") || raw.contains("221201") || raw.contains("01a4") -> "gear"
            lab.contains("fuel") && lab.contains("pressure") || raw.contains("010a") || raw.contains("0123") -> "fuel pressure"
            lab.contains("misfire") -> "misfire"
            lab.contains("oil temp") -> "oil temperature"
            lab.contains("ac ") || lab.contains("hvac") || lab.contains("cabin") || lab.contains("climate") -> "hvac"
            else -> lab.ifBlank { raw }
        }
    }

    private fun coolant2() = listOf(
        forceMode01("0167", "Force SAE Coolant temp sensors (0167)", 3, "°C") { d ->
            if (d.size >= 3 && (d[0] and 0x02) != 0) (d[2] - 40).toDouble()
            else if (d.size >= 2) (d[1] - 40).toDouble()
            else temp(d)
        },
        forceMode01("0105", "Fallback Coolant 1 (0105)", 1, "°C", ::temp),
        can11("7E0", "0167", "ECM header 7E0 + 0167", "Address ECM directly over CAN 11-bit.", 3, "°C") { d ->
            if (d.size >= 3) (d.getOrElse(2) { d[1] } - 40).toDouble() else temp(d)
        },
        can11("7E0", "221301", "Honda oil/coolant candidate 221301", "Placeholder Honda enhanced temp.", 1, "°C", ::temp),
        can29("18DA10F1", "0167", "ISO-TP ECM + 0167", "29-bit diagnostic addressing used by many Hondas.", 3, "°C") { d ->
            if (d.size >= 2) (d.last() - 40).toDouble() else temp(d)
        },
    )

    private fun ambient() = listOf(
        forceMode01("0146", "Force SAE Ambient (0146)", 1, "°C", ::temp),
        forceMode01("010F", "Fallback Intake air temp (010F)", 1, "°C", ::temp),
        can11("7E0", "0146", "ECM 7E0 + Ambient", "Direct ECM Mode 01 ambient.", 1, "°C", ::temp),
        can11("7E0", "221803", "Honda HVAC ambient candidate", "Climate pack placeholder.", 1, "°C", ::temp),
        can11("7E1", "221803", "Alt ECU 7E1 + HVAC ambient", "Some modules sit on 7E1.", 1, "°C", ::temp),
        can29("18DA10F1", "0146", "ISO-TP + Ambient", "29-bit ECM ambient try.", 1, "°C", ::temp),
    )

    private fun ltft() = listOf(
        forceMode01("0107", "Force SAE LTFT Bank 1 (0107)", 1, "%", ::trim),
        forceMode01("0109", "LTFT Bank 2 (0109)", 1, "%", ::trim),
        forceMode01("0156", "LTFT secondary B1 (0156)", 1, "%", ::trim),
        can11("7E0", "0107", "ECM 7E0 + LTFT", "Direct ECM long-term fuel trim.", 1, "%", ::trim),
        DeepSearchStrategy(
            id = "iso9141_0107",
            title = "ISO 9141 protocol + LTFT",
            rationale = "Some older adapters need an explicit protocol before Mode 01 answers.",
            setup = listOf("ATSP3", "ATSH7DF"),
            request = "0107",
            dataBytes = 1,
            decode = ::trim,
            unit = "%",
        ),
    )

    private fun battery() = listOf(
        // ATRV first — Torque-style adapter rail voltage; works when ECU omits 0142
        // AND when the ECU link is momentarily UNABLE (adapter still powered).
        DeepSearchStrategy(
            id = "atrv",
            title = "ELM adapter voltage (ATRV)",
            rationale = "Reads OBD-plug voltage at the adapter (same source Torque uses). Works even when the ECM does not advertise PID 0142, and even while the ECU bus is flaky.",
            setup = emptyList(),
            request = "ATRV",
            dataBytes = 0,
            decode = { null },
            unit = "V",
            teardown = emptyList(),
        ),
        // ATRV after an explicit soft restore — clones often need a clean buffer.
        DeepSearchStrategy(
            id = "atrv_after_restore",
            title = "Restore + ATRV",
            rationale = "Soft-restore then ATRV — recovers voltage after UNABLE storms / deep-search leftovers.",
            setup = listOf("ATD", "ATE0", "ATL0", "ATS0", "ATSP0"),
            request = "ATRV",
            dataBytes = 0,
            decode = { null },
            unit = "V",
            teardown = emptyList(),
        ),
        forceMode01("0142", "Force Control module voltage (0142)", 2, "V", ::volts),
        can11("7E0", "0142", "ECM 7E0 + voltage", "Direct ECM voltage.", 2, "V", ::volts),
        // Some FB2 clones answer 0142 only after ATSP6 (ISO-CAN fixed).
        DeepSearchStrategy(
            id = "can_auto_0142",
            title = "CAN auto + 0142",
            rationale = "Lock ISO 15765-4 CAN then ask for module voltage.",
            setup = listOf("ATSP6", "ATSH7DF"),
            request = "0142",
            dataBytes = 2,
            decode = ::volts,
            unit = "V",
        ),
        can11("7E0", "22130C", "Honda ECU voltage candidate", "Mode 22 placeholder.", 2, "V", ::volts),
        can11("7E0", "221703", "Honda battery sensor candidate", "Body pack placeholder.", 2, "V", ::volts),
    )

    private fun atf() = listOf(
        can11("7E1", "221101", "TCM 7E1 + ATF 221101", "Common TCM secondary address try.", 1, "°C", ::temp),
        can11("7E0", "221101", "ECM header + ATF (unlikely)", "Sometimes TCM shares bus via ECM gateway.", 1, "°C", ::temp),
        can29("18DA1EF1", "221101", "ISO-TP TCM + ATF", "29-bit TCM diagnostic header try.", 1, "°C", ::temp),
        can29("18DA10F1", "221101", "ISO-TP ECM + ATF", "Gatewayed ATF via ECM.", 1, "°C", ::temp),
        can11("7E1", "221102", "TCM + ATF alt ID 221102", "Alternate placeholder.", 1, "°C", ::temp),
        DeepSearchStrategy(
            id = "broadcast_221101",
            title = "Broadcast 221101 (no header change)",
            rationale = "Default functional addressing — what the app used before.",
            setup = listOf("ATSP0", "ATSH7DF"),
            request = "221101",
            dataBytes = 1,
            decode = ::temp,
            unit = "°C",
        ),
    )

    private fun gear() = listOf(
        forceMode01("01A4", "Force SAE gear ratio (01A4)", 4, "") { d ->
            if (d.size >= 4 && (d[0] and 0x02) != 0) ((256 * d[2]) + d[3]) / 1000.0 else u16(d)
        },
        can11("7E1", "221201", "TCM current gear raw", "Honda TCM gear placeholder.", 1, "", ::a),
        can11("7E1", "221203", "TCM gear ratio live", "Honda TCM ratio placeholder.", 2, "", ::u16),
        can29("18DA1EF1", "221201", "ISO-TP TCM gear", "29-bit TCM gear try.", 1, "", ::a),
    )

    private fun fuelPressure() = listOf(
        forceMode01("010A", "SAE Fuel pressure (010A)", 1, "kPa") { d -> a(d)?.times(3.0) },
        forceMode01("0123", "SAE Fuel rail abs (0123)", 2, "kPa") { d -> u16(d)?.times(10.0) },
        forceMode01("0159", "SAE Fuel rail abs (0159)", 2, "kPa") { d -> u16(d)?.times(10.0) },
        can11("7E0", "221310", "Honda fuel pump cand A", "Enhanced placeholder.", 2, "kPa", ::u16),
        can11("7E0", "221311", "Honda fuel pump cand B", "Enhanced placeholder.", 2, "kPa", ::u16),
        can11("7E0", "221312", "Honda fuel rail pressure", "Enhanced placeholder.", 2, "kPa", ::u16),
    )

    private fun misfire() = listOf(
        can11("7E0", "221308", "Misfire cyl 1", "Honda misfire placeholders.", 2, "", ::u16),
        can11("7E0", "221309", "Misfire cyl 2", "Honda misfire placeholders.", 2, "", ::u16),
        can11("7E0", "22130A", "Misfire cyl 3", "Honda misfire placeholders.", 2, "", ::u16),
        can11("7E0", "22130B", "Misfire cyl 4", "Honda misfire placeholders.", 2, "", ::u16),
        can11("7E0", "221316", "Total misfire count", "Honda total misfire placeholder.", 2, "", ::u16),
        can29("18DA10F1", "221316", "ISO-TP total misfire", "29-bit ECM misfire try.", 2, "", ::u16),
    )

    private fun oilTemp() = listOf(
        forceMode01("015C", "SAE Engine oil temp (015C)", 1, "°C", ::temp),
        can11("7E0", "221301", "Honda oil temp 221301", "Enhanced placeholder.", 1, "°C", ::temp),
    )

    private fun climate() = listOf(
        can11("7E0", "221801", "AC compressor status", "Climate pack placeholder.", 1, "", ::a),
        can11("7E0", "221802", "AC pressure", "Climate pack placeholder.", 2, "kPa", ::u16),
        can11("7E0", "221803", "Cabin/ambient HVAC", "Climate pack placeholder.", 1, "°C", ::temp),
        can11("7E1", "221803", "Alt header HVAC ambient", "Secondary module try.", 1, "°C", ::temp),
    )

    private fun genericFor(pid: PidDefinition?, label: String, requestHint: String?): List<DeepSearchStrategy> {
        val req = (pid?.request ?: requestHint)?.uppercase()?.filter { it.isDigit() || it in 'A'..'F' }
            ?: return emptyList()
        if (req.length < 4) return emptyList()
        val bytes = pid?.dataBytes ?: 2
        val unit = pid?.unit.orEmpty()
        val decode = pid?.decode ?: { d -> a(d) }
        return listOf(
            DeepSearchStrategy(
                id = "generic_force_$req",
                title = "Force $req (broadcast)",
                rationale = "Retry the same request ignoring support bitmask.",
                setup = listOf("ATSP0", "ATSH7DF"),
                request = req,
                dataBytes = bytes,
                decode = decode,
                unit = unit,
            ),
            can11("7E0", req, "ECM 7E0 + $req", "Direct ECM header.", bytes, unit, decode),
            can11("7E1", req, "ECU 7E1 + $req", "Secondary module header.", bytes, unit, decode),
            can29("18DA10F1", req, "ISO-TP ECM + $req", "29-bit ECM try.", bytes, unit, decode),
            DeepSearchStrategy(
                id = "generic_iso9141_$req",
                title = "ISO 9141 + $req",
                rationale = "Explicit legacy protocol.",
                setup = listOf("ATSP3", "ATSH7DF"),
                request = req,
                dataBytes = bytes,
                decode = decode,
                unit = unit,
            ),
            DeepSearchStrategy(
                id = "generic_can29_500_$req",
                title = "CAN 29/500 + $req",
                rationale = "Some clones need ATSP7 before extended headers.",
                setup = listOf("ATSP7", "ATSH18DA10F1"),
                request = req,
                dataBytes = bytes,
                decode = decode,
                unit = unit,
            ),
        )
    }

    fun explainLikelyCause(label: String, pid: PidDefinition?): String {
        val key = normalize(label, pid?.id, pid?.request)
        return when (key) {
            "battery" ->
                "Battery on this Civic is usually adapter rail voltage (ATRV), same as Torque — PID 0142 is often missing from the ECM bitmask. Deep analysis restores the ELM, retries ATRV, then only tries ECU headers if the bus is alive (it will not thrash ATSP/ATSH while UNABLE)."
            "coolant2", "ambient", "ltft" ->
                "Usually the ECM does not advertise this SAE PID (not an ELM failure). Deep search still forces the request and tries ECU headers."
            "atf", "gear", "misfire", "hvac", "oil temperature", "fuel pressure" ->
                "Often needs Honda Mode 22 IDs + the correct CAN header (ATSH). Our catalog starts with placeholders — deep search tries common ECM/TCM headers and alternate IDs."
            else ->
                "Could be ECU-unsupported, wrong Mode 22 ID, missing CAN header, or a limited ELM clone. Deep search tries broadcast, ECM/TCM headers, and protocol switches."
        }
    }
}
