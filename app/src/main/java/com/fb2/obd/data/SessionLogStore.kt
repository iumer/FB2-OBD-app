package com.fb2.obd.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One CSV value-log session saved under app files. */
data class SavedLogFile(
    val fileName: String,
    val absolutePath: String,
    val startedMs: Long,
    val sizeBytes: Long,
) {
    val displayName: String
        get() = fileName.removeSuffix(".csv")
}

/**
 * Persists each logging session as its own timestamped CSV so previous drives
 * are not overwritten when the user starts a new log.
 *
 * Long hauls also use [writeCheckpoint] so a crash mid-drive still leaves a
 * recoverable CSV on disk (not only RAM until STOP LOG).
 */
class SessionLogStore(private val dir: File) {

    init {
        dir.mkdirs()
    }

    fun list(): List<SavedLogFile> {
        dir.mkdirs()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".csv", ignoreCase = true) }
            ?.map { f ->
                SavedLogFile(
                    fileName = f.name,
                    absolutePath = f.absolutePath,
                    startedMs = f.lastModified(),
                    sizeBytes = f.length(),
                )
            }
            ?.sortedByDescending { it.startedMs }
            ?: emptyList()
    }

    fun saveSession(
        csv: String,
        startedMs: Long = System.currentTimeMillis(),
        isDemo: Boolean = false,
    ): SavedLogFile {
        dir.mkdirs()
        val stamp = FILE_FMT.format(Date(startedMs))
        // "demo" in the name so simulated sessions are easy to find/delete later.
        val prefix = if (isDemo) "FB2-log-demo" else "FB2-log"
        var name = "$prefix-$stamp.csv"
        var file = File(dir, name)
        var n = 2
        while (file.exists()) {
            name = "$prefix-$stamp-$n.csv"
            file = File(dir, name)
            n++
        }
        file.writeText(csv)
        return SavedLogFile(
            fileName = file.name,
            absolutePath = file.absolutePath,
            startedMs = startedMs,
            sizeBytes = file.length(),
        )
    }

    /**
     * Create (or reopen) the in-progress session file for periodic checkpoints.
     * Name is stable for the session so each flush overwrites the same path.
     */
    fun beginCheckpointFile(startedMs: Long, isDemo: Boolean = false): SavedLogFile {
        dir.mkdirs()
        val stamp = FILE_FMT.format(Date(startedMs))
        val prefix = if (isDemo) "FB2-log-demo" else "FB2-log"
        val name = "$prefix-$stamp.csv"
        val file = File(dir, name)
        if (!file.exists()) {
            file.writeText("")
        }
        return SavedLogFile(
            fileName = file.name,
            absolutePath = file.absolutePath,
            startedMs = startedMs,
            sizeBytes = file.length(),
        )
    }

    /** Overwrite the active session file with the latest CSV (crash-safe for long trips). */
    fun writeCheckpoint(absolutePath: String, csv: String): SavedLogFile? {
        val file = File(absolutePath)
        file.parentFile?.mkdirs()
        file.writeText(csv)
        return SavedLogFile(
            fileName = file.name,
            absolutePath = file.absolutePath,
            startedMs = file.lastModified(),
            sizeBytes = file.length(),
        )
    }

    fun read(fileName: String): String? {
        val file = File(dir, fileName)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun delete(fileName: String): Boolean {
        val file = File(dir, fileName)
        return file.isFile && file.delete()
    }

    companion object {
        private val FILE_FMT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
