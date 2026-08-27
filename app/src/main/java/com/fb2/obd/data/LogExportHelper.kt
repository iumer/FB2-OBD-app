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
 * Save / share helpers for Debug + Value logs.
 *
 * Car HUs often only expose **Bluetooth** as an ACTION_SEND target, which
 * opens a useless BT search UI. We therefore **always save to Downloads** and
 * only offer Share when a real app (Drive, email, WhatsApp, Files…) exists.
 */
object LogExportHelper {

    data class ExportResult(
        val displayPath: String,
        val absolutePath: String?,
        val mediaStoreUri: Uri?,
    )

    /** Packages that look like share targets but are useless for log export. */
    private val USELESS_SHARE_PACKAGES = listOf(
        "bluetooth",
        "bluetoothopp",
        "btservice",
        "nearby",
        "nearbyshare",
        "android.sharing",
        "intentresolver",
        "com.android.internal.app",
        "com.google.android.gms.nearby",
        "com.samsung.android.app.sharelive",
        "com.miui.mishare",
    )

    fun isUsefulSharePackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg.isBlank()) return false
        return USELESS_SHARE_PACKAGES.none { bad -> pkg.contains(bad) }
    }

    fun hasUsefulShareTargets(context: Context, intent: Intent): Boolean {
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
        return list.any { ri -> isUsefulSharePackage(ri.activityInfo?.packageName.orEmpty()) }
    }

    /**
     * Persist [source] under Downloads/FB2-Diag/ (when possible) and
     * app-specific Documents/exports/ (always).
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
                mediaUri = Uri.fromFile(dest)
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

    /**
     * Lightweight durable copy under app Documents/exports only (no MediaStore).
     * Used on LOG checkpoints so a mid-drive reinstall still has a recoverable file.
     */
    fun mirrorToAppExports(context: Context, source: File, displayName: String): File? {
        if (!source.isFile) return null
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "fb2-log" }
        return runCatching {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "exports",
            ).also { it.mkdirs() }
            val dest = File(dir, safeName)
            source.copyTo(dest, overwrite = true)
            dest
        }.getOrNull()
    }

    /** Best-effort open in the unit's Files / Downloads UI. */
    fun openInFileManager(context: Context, result: ExportResult, mime: String): Boolean {
        val uri = result.mediaStoreUri ?: return false
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            if (view.resolveActivity(context.packageManager) != null) {
                context.startActivity(view)
                true
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Try opening the Downloads collection (API 29+).
                val downloads = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "vnd.android.document/directory",
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (downloads.resolveActivity(context.packageManager) != null) {
                    context.startActivity(downloads)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
