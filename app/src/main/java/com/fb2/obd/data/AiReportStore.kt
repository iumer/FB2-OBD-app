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
     * Write a report with metadata header + AI body.
     * @return saved file info
     */
    fun saveReport(
        body: String,
        sourceLabel: String,
        windowMinutes: Int,
        model: String,
        createdMs: Long = System.currentTimeMillis(),
    ): SavedAiReport {
        dir.mkdirs()
        val stamp = FILE_FMT.format(Date(createdMs))
        var name = "FB2-ai-$stamp.txt"
        var file = File(dir, name)
        var n = 2
        while (file.exists()) {
            name = "FB2-ai-$stamp-$n.txt"
            file = File(dir, name)
            n++
        }
        val text = buildString {
            appendLine("# FB2-OBD AI diagnostic report")
            appendLine("# created_ms=$createdMs")
            appendLine("# source=$sourceLabel")
            appendLine("# window_minutes=$windowMinutes")
            appendLine("# model=$model")
            appendLine("# vehicle=Honda Civic FB2 2013 R18 PK UG AT (D/D3/D2/D1)")
            appendLine()
            append(body.trim())
            appendLine()
        }
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
    }
}
