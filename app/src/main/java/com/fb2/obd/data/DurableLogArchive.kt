package com.fb2.obd.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Keeps session CSVs / AI reports discoverable after an uninstall+reinstall
 * (common on car HUs when signing keys differ) by re-importing from the
 * durable Downloads/FB2-Diag mirror and app Documents/exports/.
 *
 * In-place updates already keep `filesDir`; this only fills gaps.
 */
object DurableLogArchive {

    data class ImportResult(
        val csvImported: Int = 0,
        val aiImported: Int = 0,
    )

    /**
     * Copy any missing `FB2-log-*.csv` / `FB2-ai-*.txt` from durable locations
     * into the app-private [valueLogsDir] / [aiReportsDir].
     */
    fun importMissing(
        context: Context,
        valueLogsDir: File,
        aiReportsDir: File,
    ): ImportResult {
        valueLogsDir.mkdirs()
        aiReportsDir.mkdirs()
        var csv = 0
        var ai = 0

        collectCandidates(context).forEach { src ->
            val name = src.name
            when {
                name.startsWith("FB2-log", ignoreCase = true) &&
                    name.endsWith(".csv", ignoreCase = true) -> {
                    val dest = File(valueLogsDir, name)
                    if (!dest.exists() && src.length() > 0L) {
                        runCatching {
                            src.copyTo(dest, overwrite = false)
                            csv++
                        }
                    }
                }
                name.startsWith("FB2-ai", ignoreCase = true) &&
                    name.endsWith(".txt", ignoreCase = true) -> {
                    val dest = File(aiReportsDir, name)
                    if (!dest.exists() && src.length() > 0L) {
                        runCatching {
                            src.copyTo(dest, overwrite = false)
                            ai++
                        }
                    }
                }
            }
        }
        return ImportResult(csvImported = csv, aiImported = ai)
    }

    /** File handles for durable copies (temp MediaStore dumps live under cache). */
    private fun collectCandidates(context: Context): List<File> {
        val out = mutableListOf<File>()

        // App-specific Documents/exports (survives in-place update; dies on uninstall).
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?.let { File(it, "exports") }
            ?.takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isFile }
            ?.let { out.addAll(it) }

        // Legacy public Downloads/FB2-Diag/
        @Suppress("DEPRECATION")
        runCatching {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "FB2-Diag",
            )
            if (dir.isDirectory) {
                dir.listFiles()?.filter { it.isFile }?.let { out.addAll(it) }
            }
        }

        // MediaStore Downloads (API 29+) — copy into cache then treat as sources.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                out.addAll(mediaStoreFb2DiagFiles(context))
            }
        }

        // Prefer larger copy when the same name appears twice.
        return out
            .groupBy { it.name }
            .values
            .map { group -> group.maxBy { it.length() } }
    }

    private fun mediaStoreFb2DiagFiles(context: Context): List<File> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%FB2-Diag%")
        val tmpDir = File(context.cacheDir, "durable_import").also { it.mkdirs() }
        val result = mutableListOf<File>()
        resolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Downloads.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                if (!name.startsWith("FB2-log", true) && !name.startsWith("FB2-ai", true)) continue
                val id = cursor.getLong(idIdx)
                val uri = ContentUris.withAppendedId(collection, id)
                val dest = File(tmpDir, name)
                runCatching {
                    resolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (dest.isFile && dest.length() > 0L) result.add(dest)
                }
            }
        }
        return result
    }
}
