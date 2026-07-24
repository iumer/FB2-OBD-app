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

        strategies.forEachIndexed { idx, strategy ->
            onProgress(idx + 1, strategies.size, strategy.title)
            val hit = tryStrategy(source, strategy)
            if (hit != null) {
                ObdLogger.logDebug(
                    ObdLogger.Dir.INFO,
                    "DEEP SEARCH HIT [$label] via ${strategy.id} = ${hit.value} ${strategy.unit}",
                )
                return DeepSearchReport(
                    targetLabel = label,
                    targetId = pid?.id ?: requestHint ?: label,
                    attempts = idx + 1,
                    hit = hit,
                    notes = notes + "Found with: ${strategy.title}",
                )
            }
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
                delay(30L)
            }
            delay(40L)
            val raw = source.command(strategy.request) ?: return null
            val up = raw.uppercase()
            if (BAD.any { up.contains(it) }) return null

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
                delay(20L)
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
