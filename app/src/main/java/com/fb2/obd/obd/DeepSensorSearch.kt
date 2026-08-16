package com.fb2.obd.obd

import com.fb2.obd.data.ObdLogger
import com.fb2.obd.data.ObdSource
import kotlinx.coroutines.delay

/**
 * Runs [DeepSearchKnowledgeBase] strategies against a live [ObdSource].
 *
 * Smart flow (learned from FB2 + cheap ELM clones + Torque):
 * 1. Pause continuous Mode 01 polling so ATSH/ATSP thrash cannot interleave
 *    with the Dash (that produced laggy / wrong values during deep search).
 * 2. Soft-restore the adapter first (don't start mid-UNABLE storm).
 * 3. Prefer adapter-local recipes (ATRV) — they work even when the ECU link is down.
 * 4. Always try simple broadcast Mode 01 forces next (e.g. Coolant 0105) — these
 *    are safe even when a strict bus ping is flaky after ATSP0 SEARCHING.
 * 5. Ping the bus; if healthy, walk the remaining header / Mode 22 list.
 * 6. Always restore broadcast Mode 01 and resume polling (clears Dash hang).
 */
object DeepSensorSearch {

    private val BAD = listOf("NO DATA", "UNABLE", "ERROR", "?", "STOPPED", "BUS INIT")

    private val FULL_RESTORE = listOf("ATD", "ATE0", "ATL0", "ATS0", "ATSH7DF", "ATAR")
    /** Last resort when gentle restore leaves the bus dead — includes ATSP0. */
    private val HARD_RESTORE = listOf("ATD", "ATE0", "ATL0", "ATS0", "ATSP0", "ATSH7DF", "ATAR")

    /** Soft-restore after this many consecutive UNABLE/timeout misses (keep walking the list). */
    private const val RESTORE_EVERY_UNABLE = 2

    suspend fun run(
        source: ObdSource,
        label: String,
        pid: PidDefinition? = null,
        requestHint: String? = null,
        profile: VehicleProfile = VehicleProfile.FB2,
        onProgress: (index: Int, total: Int, title: String) -> Unit = { _, _, _ -> },
    ): DeepSearchReport {
        val all = VehicleProfileConfig.deepSearchStrategies(profile, pid, label, requestHint)
        val notes = mutableListOf(DeepSearchKnowledgeBase.explainLikelyCause(label, pid))
        if (all.isEmpty()) {
            return DeepSearchReport(
                label,
                pid?.id ?: requestHint ?: label,
                0,
                notes = notes + "No strategies registered for this sensor.",
            )
        }

        ObdLogger.logDebug(ObdLogger.Dir.INFO, "DEEP SEARCH begin [$label] ${all.size} strategies")
        source.pausePolling()
        onProgress(0, all.size, "Pausing live Dash poll…")
        delay(80L)

        val adapterLocal = all.filter { it.isAdapterLocal }
        val simpleForce = all.filter { !it.isAdapterLocal && it.isSimpleForce }
        val advanced = all.filter { !it.isAdapterLocal && !it.isSimpleForce }
        var attempts = 0

        try {
            onProgress(0, all.size, "Restoring adapter…")
            restore(source)

            // --- Phase A: adapter-local (ATRV etc.) with retries ---
            for ((i, strategy) in adapterLocal.withIndex()) {
                attempts++
                onProgress(attempts, all.size, strategy.title)
                delay(if (source.isLive) 40L else 120L)
                val hit = tryStrategy(source, strategy)
                if (hit != null) {
                    return success(label, pid, requestHint, attempts, hit, notes)
                }
                if (strategy.request.equals("ATRV", true) && i == 0) {
                    restore(source)
                    attempts++
                    onProgress(attempts, all.size, "Retry ${strategy.title}")
                    tryStrategy(source, strategy)?.let { hit2 ->
                        return success(label, pid, requestHint, attempts, hit2, notes)
                    }
                }
            }

            // --- Phase B: simple Mode 01 forces BEFORE bus verdict ---
            // Coolant 0105 etc. must not be skipped just because ATSP0 SEARCHING
            // made a quick 010C ping look dead while ELM still shows LINKED.
            if (simpleForce.isNotEmpty()) {
                onProgress(attempts.coerceAtLeast(0), all.size, "Trying broadcast Mode 01…")
                restore(source)
                for (strategy in simpleForce) {
                    attempts++
                    onProgress(attempts, all.size, strategy.title)
                    delay(if (source.isLive) 40L else 120L)
                    tryStrategy(source, strategy)?.let { hit ->
                        return success(label, pid, requestHint, attempts, hit, notes)
                    }
                }
            }

            // --- Phase C: bus health for advanced header / Mode 22 ---
            onProgress(attempts.coerceAtLeast(0), all.size, "Checking ECU link…")
            val busOk = busHealthy(source)
            if (!busOk) {
                notes += "ECU link check was shaky after restore (UNABLE / timeout). " +
                    "Already tried ${simpleForce.size} broadcast Mode 01 strategies. " +
                    "Retrying once with protocol search (ATSP0) before skipping advanced."
                restore(source, hard = true)
                val busOkHard = busHealthy(source)
                if (!busOkHard) {
                    notes += "Still shaky after ATSP0. " +
                        "Skipping ${advanced.size} header/Mode 22 strategies that need a solid ECM link."
                    return DeepSearchReport(
                        targetLabel = label,
                        targetId = pid?.id ?: requestHint ?: label,
                        attempts = attempts,
                        hit = null,
                        notes = notes + "Still unable to find this sensor. " +
                            "Tried $attempts / ${all.size} strategies" +
                            if (advanced.isNotEmpty()) " (skipped ${advanced.size} advanced)." else ".",
                    )
                }
            }

            // --- Phase D: walk advanced list while bus healthy ---
            var unableStreak = 0
            for (strategy in advanced) {
                attempts++
                onProgress(attempts, all.size, strategy.title)
                delay(if (source.isLive) 25L else 150L)

                val hit = tryStrategy(source, strategy)
                if (hit != null) {
                    return success(label, pid, requestHint, attempts, hit, notes)
                }

                val ping = source.command("010C")?.uppercase().orEmpty()
                if (BAD.any { ping.contains(it) } || ping.isBlank()) {
                    unableStreak++
                    if (unableStreak % RESTORE_EVERY_UNABLE == 0) {
                        onProgress(attempts, all.size, "Soft-restore after UNABLE…")
                        restore(source)
                    }
                } else {
                    unableStreak = 0
                }
            }
            if (unableStreak > 0) {
                notes += "Finished all ${advanced.size} advanced strategies; last $unableStreak miss(es) looked like UNABLE/timeout."
            }
        } finally {
            // Always restore + resume so Dash never stays frozen after deep search.
            runCatching { restore(source) }
            source.resumePolling()
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "DEEP SEARCH restore + poll resume done [$label]")
        }

        ObdLogger.logDebug(ObdLogger.Dir.INFO, "DEEP SEARCH miss [$label] after $attempts / ${all.size} tries")
        return DeepSearchReport(
            targetLabel = label,
            targetId = pid?.id ?: requestHint ?: label,
            attempts = attempts,
            hit = null,
            notes = notes + "Still unable to find this sensor after trying $attempts / ${all.size} strategies. " +
                "Likely unsupported on this ECU, wrong Honda Mode 22 ID, or the adapter cannot reach that module. " +
                "Capture a debug log and try again when LIVE is stable.",
        )
    }

    private fun success(
        label: String,
        pid: PidDefinition?,
        requestHint: String?,
        attempts: Int,
        hit: DeepSearchHit,
        notes: MutableList<String>,
    ): DeepSearchReport {
        ObdLogger.logDebug(
            ObdLogger.Dir.INFO,
            "DEEP SEARCH HIT [$label] via ${hit.strategy.id} = ${hit.value} ${hit.strategy.unit}",
        )
        return DeepSearchReport(
            targetLabel = label,
            targetId = pid?.id ?: requestHint ?: label,
            attempts = attempts,
            hit = hit,
            notes = notes + "Found with: ${hit.strategy.title}",
        )
    }

    private suspend fun restore(source: ObdSource, hard: Boolean = false) {
        val seq = if (hard) HARD_RESTORE else FULL_RESTORE
        seq.forEach { cmd ->
            runCatching { source.command(cmd) }
            delay(30L)
        }
        // Brief settle; hard path needs longer for ATSP0 SEARCHING…
        delay(if (hard) 120L else 40L)
    }

    private suspend fun busHealthy(source: ObdSource): Boolean {
        // ATSP0 often prints SEARCHING… — give the clone several chances.
        repeat(5) { attempt ->
            val ping = source.command("010C")?.uppercase().orEmpty()
            if (ping.isNotBlank() && BAD.none { ping.contains(it) } &&
                (ping.contains("41") || ping.contains("0C"))
            ) {
                return true
            }
            // Also accept a coolant ping — proves Mode 01 is alive.
            val cool = source.command("0105")?.uppercase().orEmpty()
            if (cool.isNotBlank() && BAD.none { cool.contains(it) } &&
                (cool.contains("41") || cool.contains("05"))
            ) {
                return true
            }
            if (attempt < 4) delay(280L)
        }
        return false
    }

    private suspend fun tryStrategy(source: ObdSource, strategy: DeepSearchStrategy): DeepSearchHit? {
        try {
            strategy.setup.forEach { cmd ->
                source.command(cmd)
                delay(25L)
            }
            delay(30L)
            val raw = source.command(strategy.request) ?: return null
            val up = raw.uppercase()
            if (BAD.any { up.contains(it) }) return null

            if (strategy.request.equals("ATRV", ignoreCase = true)) {
                val v = ObdResponseParser.parseAtVoltage(raw) ?: return null
                return DeepSearchHit(strategy, v, raw)
            }

            val bytes = when {
                strategy.request.startsWith("01") && strategy.request.length == 4 ->
                    ObdResponseParser.rawDataBytes(strategy.request, strategy.dataBytes, raw)
                strategy.request.startsWith("22") -> extractMode22(strategy.request, raw)
                else -> ObdResponseParser.rawDataBytes(
                    if (strategy.request.length >= 4) strategy.request.take(4) else strategy.request,
                    strategy.dataBytes,
                    raw,
                )
            } ?: return null

            val value = strategy.decode(bytes) ?: return null
            return DeepSearchHit(strategy, value, raw)
        } finally {
            strategy.teardown.forEach { cmd ->
                runCatching { source.command(cmd) }
                delay(12L)
            }
        }
    }

    private fun extractMode22(request: String, raw: String): IntArray? {
        val pidHex = request.removePrefix("22")
        val header = "62$pidHex".uppercase()
        val cleaned = raw.replace(">", " ").replace("\r", " ").replace("\n", " ").uppercase()
        val joined = cleaned.filter { it.isDigit() || it in 'A'..'F' }
        val idx = joined.indexOf(header)
        if (idx < 0) return null
        val data = joined.substring(idx + header.length).chunked(2)
            .filter { it.length == 2 }.mapNotNull { it.toIntOrNull(16) }
        return if (data.isEmpty()) null else data.toIntArray()
    }
}
