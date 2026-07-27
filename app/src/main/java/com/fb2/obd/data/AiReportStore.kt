package com.fb2.obd.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One saved AI diagnostic report (.txt). */
data class SavedAiReport(
    val fileName: String,
    val absolutePath: String,
    val createdMs: Long,
    val sizeBytes: Long,
) {
    val displayName: String get() = fileName.removeSuffix(".txt")
}

/**
 * Saves Analyze-via-AI reports under `{filesDir}/ai_reports/` and mirrors to
 * Downloads/FB2-Diag/ai-reports/ when possible.
 */
class AiReportStore(
    private val dir: File,
    private val appContext: Context? = null,
) {
    init {
        dir.mkdirs()
    }

    fun list(): List<SavedAiReport> {
        dir.mkdirs()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".txt", ignoreCase = true) }
            ?.map { f ->
                SavedAiReport(
                    fileName = f.name,
                    absolutePath = f.absolutePath,
                    createdMs = f.lastModified(),
                    sizeBytes = f.length(),
                )
            }
            ?.sortedByDescending { it.createdMs }
            ?: emptyList()
    }

    fun read(fileName: String): String? {
        val file = File(dir, fileName)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()
    }

    /**
     * Write a report with metadata header + AI findings + the readings window
     * that was sent to the model (so you can audit values offline).
     */
    fun saveReport(
        body: String,
        sourceLabel: String,
        windowMinutes: Int,
        model: String,
        readingsAppendix: String = "",
        createdMs: Long = System.currentTimeMillis(),
        isDemo: Boolean = false,
    ): SavedAiReport {
        dir.mkdirs()
        val stamp = FILE_FMT.format(Date(createdMs))
        // "demo" in the name so simulated AI reports are easy to find/delete later.
        val prefix = if (isDemo) "FB2-ai-demo" else "FB2-ai"
        var name = "$prefix-$stamp.txt"
        var file = File(dir, name)
        var n = 2
        while (file.exists()) {
            name = "$prefix-$stamp-$n.txt"
            file = File(dir, name)
            n++
        }
        val text = buildFullReportText(
            body = body,
            sourceLabel = sourceLabel,
            windowMinutes = windowMinutes,
            model = model,
            readingsAppendix = readingsAppendix,
            createdMs = createdMs,
            isDemo = isDemo,
        )
        file.writeText(text)
        mirrorToDownloads(file, name)
        return SavedAiReport(
            fileName = file.name,
            absolutePath = file.absolutePath,
            createdMs = createdMs,
            sizeBytes = file.length(),
        )
    }

    private fun mirrorToDownloads(source: File, displayName: String) {
        val ctx = appContext ?: return
        runCatching {
            LogExportHelper.exportFile(
                context = ctx,
                source = source,
                displayName = displayName,
                mime = "text/plain",
            )
        }
    }

    companion object {
        private val FILE_FMT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

        fun buildFullReportText(
            body: String,
            sourceLabel: String,
            windowMinutes: Int,
            model: String,
            readingsAppendix: String,
            createdMs: Long,
            isDemo: Boolean = false,
        ): String = buildString {
            appendLine("# FB2-OBD AI diagnostic report")
            appendLine("# created_ms=$createdMs")
            appendLine("# source=$sourceLabel")
            appendLine("# window_minutes=$windowMinutes")
            appendLine("# model=$model")
            appendLine("# vehicle=Honda Civic FB2 2013 R18 PK UG AT (D/D3/D2/D1)")
            if (isDemo) {
                appendLine("# mode=demo")
                appendLine("# note: Readings are from DEMO (simulated), not a live ELM/vehicle connection.")
            }
            appendLine()
            appendLine("===== AI FINDINGS =====")
            appendLine()
            append(body.trim())
            appendLine()
            if (readingsAppendix.isNotBlank()) {
                appendLine()
                appendLine("===== READINGS SENT TO AI (audit table) =====")
                appendLine()
                append(readingsAppendix.trim())
                appendLine()
            }
        }
    }
}
