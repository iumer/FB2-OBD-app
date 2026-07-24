package com.fb2.obd.obd

import com.fb2.obd.data.ObdLogger
import com.fb2.obd.data.ObdSource
import kotlinx.coroutines.delay

/**
 * Runs [DeepSearchKnowledgeBase] strategies against a live [ObdSource].
 * Safe teardown is always attempted so the main poll loop can resume.
 */
object DeepSensorSearch {

    private val BAD = listOf("NO DATA", "UNABLE", "ERROR", "?", "STOPPED", "BUS INIT")

    private val FULL_RESTORE = listOf("ATD", "ATE0", "ATL0", "ATS0", "ATSP0", "ATSH7DF", "ATAR")

    suspend fun run(
        source: ObdSource,
        label: String,
        pid: PidDefinition? = null,
        requestHint: String? = null,
        onProgress: (index: Int, total: Int, title: String) -> Unit = { _, _, _ -> },
    ): DeepSearchReport {
        val strategies = DeepSearchKnowledgeBase.strategiesFor(pid, label, requestHint)
        val notes = mutableListOf(DeepSearchKnowledgeBase.explainLikelyCause(label, pid))
        if (strategies.isEmpty()) {
            return DeepSearchReport(label, pid?.id ?: requestHint ?: label, 0, notes = notes + "No strategies registered for this sensor.")
        }
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "DEEP SEARCH begin [$label] ${strategies.size} strategies")

        var unableStreak = 0
        try {
            strategies.forEachIndexed { idx, strategy ->
                onProgress(idx + 1, strategies.size, strategy.title)
                delay(if (source.isLive) 30L else 180L)

                if (unableStreak >= 3) {
                    notes += "Aborted early — adapter kept returning UNABLE TO CONNECT (ECU link lost)."
                    return DeepSearchReport(
                        targetLabel = label,
                        targetId = pid?.id ?: requestHint ?: label,
                        attempts = idx,
                        hit = null,
                        notes = notes,
                    )
                }

                val hit = tryStrategy(source, strategy)
                if (hit != null) {
                    ObdLogger.logDebug(
                        ObdLogger.Dir.INFO,
                        "DEEP SEARCH HIT [$label] via ${strategy.id} = ${hit.value} ${strategy.unit}",
                    )
                    onProgress(idx + 1, strategies.size, "FOUND — ${strategy.title}")
                    delay(if (source.isLive) 60L else 350L)
                    return DeepSearchReport(
                        targetLabel = label,
                        targetId = pid?.id ?: requestHint ?: label,
                        attempts = idx + 1,
                        hit = hit,
                        notes = notes + "Found with: ${strategy.title}",
                    )
                }
                // Track consecutive bus failures across strategies.
                // tryStrategy already tore down; peek with a cheap RPM ping.
                val ping = source.command("010C")?.uppercase().orEmpty()
                if (BAD.any { ping.contains(it) } || ping.isBlank()) unableStreak++
                else unableStreak = 0
            }
        } finally {
            // Always restore broadcast Mode 01 so Dash polling isn't left on a weird header.
            FULL_RESTORE.forEach { cmd ->
                runCatching { source.command(cmd) }
                delay(25L)
            }
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "DEEP SEARCH restore done [$label]")
        }

        ObdLogger.logDebug(ObdLogger.Dir.INFO, "DEEP SEARCH miss [$label] after ${strategies.size} tries")
        return DeepSearchReport(
            targetLabel = label,
            targetId = pid?.id ?: requestHint ?: label,
            attempts = strategies.size,
            hit = null,
            notes = notes + "Still unable to find this sensor. Likely unsupported on this ECU, wrong Honda ID, or the adapter cannot reach that module. Try again later after capturing a debug log.",
        )
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
                delay(15L)
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
