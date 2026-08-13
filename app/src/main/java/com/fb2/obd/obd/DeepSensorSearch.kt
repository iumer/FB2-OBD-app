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
 * 4. Ping the bus (with retries); if ECU is unreachable, only try simple broadcast
 *    Mode 01 forces and **report how many header strategies were skipped**.
 * 5. When the bus is healthy, walk the **full** strategy list (no silent 1/N abort).
 * 6. Always restore broadcast Mode 01 and resume polling.
 */
object DeepSensorSearch {

    private val BAD = listOf("NO DATA", "UNABLE", "ERROR", "?", "STOPPED", "BUS INIT")

    private val FULL_RESTORE = listOf("ATD", "ATE0", "ATL0", "ATS0", "ATSP0", "ATSH7DF", "ATAR")

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
        val needsBus = all.filter { !it.isAdapterLocal }
        var attempts = 0
        var skipped = 0

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
                // One soft restore + immediate retry for ATRV — clones often need a clean buffer.
                if (strategy.request.equals("ATRV", true) && i == 0) {
                    restore(source)
                    attempts++
                    onProgress(attempts, all.size, "Retry ${strategy.title}")
                    tryStrategy(source, strategy)?.let { hit2 ->
                        return success(label, pid, requestHint, attempts, hit2, notes)
                    }
                }
            }

            // --- Phase B: is the ECU link alive? (retry — ATSP0 SEARCHING needs time) ---
            onProgress(attempts.coerceAtLeast(0), all.size, "Checking ECU link…")
            val busOk = busHealthy(source)
            if (!busOk) {
                notes += "ECU link check failed (UNABLE / timeout after restore). " +
                    "Mode 22 / ATSH strategies need a live ECM — not skipped silently."
                restore(source)
                val simple = needsBus.filter { it.isSimpleForce }
                skipped = needsBus.size - simple.size
                for (strategy in simple) {
                    attempts++
                    onProgress(attempts, all.size, strategy.title)
                    delay(30L)
                    tryStrategy(source, strategy)?.let { hit ->
                        return success(label, pid, requestHint, attempts, hit, notes)
                    }
                }
                if (skipped > 0) {
                    notes += "Skipped $skipped of ${all.size} header/Mode 22 strategies while the ECU link is down " +
                        "(trying them would thrash ATSH/ATSP and lag the Dash). " +
                        "Reconnect / wait for LIVE, then run deep research again."
                }
                return DeepSearchReport(
                    targetLabel = label,
                    targetId = pid?.id ?: requestHint ?: label,
                    attempts = attempts,
                    hit = null,
                    notes = notes + "Still unable to find this sensor while the ECU link is down. " +
                        "Tried $attempts / ${all.size} strategies" +
                        if (skipped > 0) " (skipped $skipped)." else ".",
                )
            }

            // --- Phase C: walk the FULL needsBus list while bus was healthy at start ---
            var unableStreak = 0
            for (strategy in needsBus) {
                attempts++
                onProgress(attempts, all.size, strategy.title)
                delay(if (source.isLive) 25L else 150L)

                val hit = tryStrategy(source, strategy)
                if (hit != null) {
                    return success(label, pid, requestHint, attempts, hit, notes)
                }

                // Peek bus health; soft-restore periodically so we don't leave a weird ATSH.
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
                notes += "Finished all ${needsBus.size} ECU strategies; last $unableStreak miss(es) looked like UNABLE/timeout."
            }
        } finally {
            restore(source)
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

    private suspend fun restore(source: ObdSource) {
        FULL_RESTORE.forEach { cmd ->
            runCatching { source.command(cmd) }
            delay(20L)
        }
    }

    private suspend fun busHealthy(source: ObdSource): Boolean {
        // ATSP0 often prints SEARCHING… — give the clone a few chances.
        repeat(3) { attempt ->
            val ping = source.command("010C")?.uppercase().orEmpty()
            if (ping.isNotBlank() && BAD.none { ping.contains(it) } &&
                (ping.contains("41") || ping.contains("0C"))
            ) {
                return true
            }
            if (attempt < 2) delay(220L)
        }
        return false
    }

    private suspend fun tryStrategy(source: ObdSource, strategy: DeepSearchStrategy): DeepSearchHit? {
        try {
            strategy.setup.forEach { cmd ->
                source.command(cmd)
                delay(20L)
            }
            delay(25L)
            val raw = source.command(strategy.request) ?: return null
            val up = raw.uppercase()
            if (BAD.any { up.contains(it) }) return null

            // ATRV returns "12.6V" — not a Mode 01/22 hex frame.
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
