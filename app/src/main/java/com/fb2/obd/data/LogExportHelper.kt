package com.fb2.obd.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Share / export helpers for Debug + Value logs.
 * Car HUs often have **zero** ACTION_SEND targets → fall back to saving into
 * public Downloads (MediaStore) + app Documents, then copy the path.
 */
object LogExportHelper {

    data class ExportResult(
        val displayPath: String,
        val absolutePath: String?,
        val mediaStoreUri: Uri?,
    )

    fun hasShareTargets(context: Context, intent: Intent): Boolean {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return list.any { ri ->
            val pkg = ri.activityInfo?.packageName.orEmpty()
            pkg.isNotBlank() &&
                !pkg.contains("intentresolver", ignoreCase = true) &&
                !pkg.contains("com.android.internal.app", ignoreCase = true)
        }
    }

    /**
     * Persist [source] under Downloads/FB2-Diag/ (when possible) and
     * app-specific Documents/exports/ (always). Returns a human path for the UI.
     */
    fun exportFile(context: Context, source: File, displayName: String, mime: String): ExportResult {
        require(source.isFile) { "missing ${source.absolutePath}" }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "fb2-log" }

        val appCopy = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "exports",
        ).also { it.mkdirs() }.let { dir -> File(dir, safeName) }
        source.copyTo(appCopy, overwrite = true)

        var mediaUri: Uri? = null
        var publicLabel: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/FB2-Diag",
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = context.contentResolver.insert(collection, values)
                    ?: error("MediaStore insert failed")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                } ?: error("openOutputStream failed")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                mediaUri = uri
                publicLabel = "Downloads/FB2-Diag/$safeName"
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "FB2-Diag",
                ).also { it.mkdirs() }
                val dest = File(dir, safeName)
                source.copyTo(dest, overwrite = true)
                publicLabel = dest.absolutePath
            }
        }

        val display = buildString {
            if (publicLabel != null) {
                appendLine(publicLabel)
            }
            append(appCopy.absolutePath)
        }.trim()
        return ExportResult(
            displayPath = display,
            absolutePath = appCopy.absolutePath,
            mediaStoreUri = mediaUri,
        )
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
