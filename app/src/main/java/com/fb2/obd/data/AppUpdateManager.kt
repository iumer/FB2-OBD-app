package com.fb2.obd.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.fb2.obd.BuildConfig
import com.fb2.obd.obd.AppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the release catalog, lets the user pick any newer version, downloads
 * that APK, and launches the system installer.
 *
 * Downloaded APKs live under filesDir/updates/ (not cache) and are remembered
 * in SharedPreferences so "Allow installs" → return → Install does not require
 * a second download.
 */
class AppUpdateManager(private val appContext: Context) {

    sealed class UiState {
        data object Idle : UiState()
        data object Checking : UiState()
        data class UpToDate(val message: String) : UiState()
        data class Available(
            val currentName: String,
            val newer: List<AppUpdateChecker.RemoteVersion>,
        ) : UiState()
        data class Downloading(
            val remote: AppUpdateChecker.RemoteVersion,
            val percent: Int,
            val newer: List<AppUpdateChecker.RemoteVersion>,
        ) : UiState()
        data class ReadyToInstall(
            val apk: File,
            val remote: AppUpdateChecker.RemoteVersion,
            val newer: List<AppUpdateChecker.RemoteVersion>,
            /** Non-null when install permission is still needed. */
            val permissionHint: String? = null,
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastNewer: List<AppUpdateChecker.RemoteVersion> = emptyList()

    private val prefs =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val localLabel: String
        get() = "v${BuildConfig.VERSION_NAME}"

    /** Restore a previously downloaded APK so INSTALL reappears without GET. */
    fun restorePendingInstallIfAny() {
        if (_state.value is UiState.Downloading || _state.value is UiState.Checking) return
        val pending = loadPending() ?: return
        val apk = File(pending.path)
        if (!apk.isFile || apk.length() < 10_000L) {
            clearPending()
            return
        }
        // Already installed this (or newer) build — drop stale pending.
        if (pending.versionCode <= BuildConfig.VERSION_CODE) {
            clearPending()
            runCatching { apk.delete() }
            return
        }
        val remote = AppUpdateChecker.RemoteVersion(
            versionCode = pending.versionCode,
            versionName = pending.versionName,
            apkUrl = pending.apkUrl,
            notes = pending.notes,
        )
        val newer = mergeNewer(listOf(remote))
        lastNewer = newer
        val needPerm = !canRequestPackageInstalls()
        _state.value = UiState.ReadyToInstall(
            apk = apk,
            remote = remote,
            newer = newer,
            permissionHint = if (needPerm) {
                "Allow installs from this app, then tap Install (APK already downloaded)"
            } else {
                null
            },
        )
    }

    suspend fun checkForUpdate() {
        _state.value = UiState.Checking
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val catalog = fetchCatalog()
                AppUpdateChecker.compare(
                    localCode = BuildConfig.VERSION_CODE,
                    localName = BuildConfig.VERSION_NAME,
                    catalog = catalog,
                )
            }.getOrElse {
                AppUpdateChecker.Result.Failed(
                    it.message?.take(160) ?: "Could not reach update server",
                )
            }
        }
        // Prefer a pending ready APK over wiping INSTALL after CHECK.
        val pendingReady = peekReadyFromDisk()
        _state.value = when (result) {
            is AppUpdateChecker.Result.UpToDate -> {
                lastNewer = emptyList()
                clearPending()
                UiState.UpToDate("You're up to date — v${result.currentName}")
            }
            is AppUpdateChecker.Result.Available -> {
                lastNewer = result.newer
                if (pendingReady != null &&
                    result.newer.any { it.versionCode == pendingReady.remote.versionCode }
                ) {
                    pendingReady.copy(newer = mergeNewer(result.newer + pendingReady.remote))
                } else {
                    UiState.Available(
                        currentName = result.currentName,
                        newer = result.newer,
                    )
                }
            }
            is AppUpdateChecker.Result.Failed -> {
                if (pendingReady != null) {
                    pendingReady
                } else {
                    lastNewer = emptyList()
                    UiState.Error(result.message)
                }
            }
        }
    }

    suspend fun downloadUpdate(remote: AppUpdateChecker.RemoteVersion): File? {
        val list = lastNewer.ifEmpty {
            (_state.value as? UiState.Available)?.newer
                ?: (_state.value as? UiState.ReadyToInstall)?.newer
                ?: listOf(remote)
        }
        lastNewer = mergeNewer(list + remote)
        _state.value = UiState.Downloading(remote, 0, lastNewer)
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = updatesDir().also { it.mkdirs() }
                val safeName = remote.versionName.replace(Regex("[^A-Za-z0-9._-]+"), "_")
                val out = File(dir, "FB2-Diag-$safeName.apk")
                httpDownload(remote.apkUrl, out) { pct ->
                    _state.value = UiState.Downloading(remote, pct, lastNewer)
                }
                savePending(out, remote)
                _state.value = UiState.ReadyToInstall(out, remote, lastNewer, permissionHint = null)
                out
            }.getOrElse {
                // Keep a prior good APK if this download failed.
                peekReadyFromDisk()?.let { ready ->
                    _state.value = ready.copy(
                        permissionHint = it.message?.take(120) ?: "Download failed — prior APK still ready",
                    )
                } ?: run {
                    _state.value = UiState.Error(it.message?.take(160) ?: "Download failed")
                }
                null
            }
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}"),
        )
        activity.startActivity(intent)
    }

    fun installApk(activity: Activity, apk: File): Boolean {
        if (!apk.exists()) {
            clearPending()
            _state.value = UiState.Error("APK missing — download again")
            return false
        }
        val ready = _state.value as? UiState.ReadyToInstall
        val remote = ready?.remote
            ?: loadPending()?.let {
                AppUpdateChecker.RemoteVersion(it.versionCode, it.versionName, it.apkUrl, it.notes)
            }
        val newer = ready?.newer ?: lastNewer
        if (!canRequestPackageInstalls()) {
            openUnknownSourcesSettings(activity)
            // Keep ReadyToInstall so the INSTALL button stays — no re-download.
            if (remote != null) {
                savePending(apk, remote)
                _state.value = UiState.ReadyToInstall(
                    apk = apk,
                    remote = remote,
                    newer = newer.ifEmpty { listOf(remote) },
                    permissionHint =
                        "Allow installs from this app, then tap Install (APK already downloaded)",
                )
            }
            return false
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(
                activity,
                "${appContext.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            // Clear permission hint; keep Ready so user can retry if they cancel the installer.
            if (remote != null) {
                _state.value = UiState.ReadyToInstall(
                    apk = apk,
                    remote = remote,
                    newer = newer.ifEmpty { listOf(remote) },
                    permissionHint = null,
                )
            }
            true
        }.getOrElse {
            _state.value = UiState.Error(it.message?.take(160) ?: "Install failed")
            false
        }
    }

    private fun updatesDir(): File = File(appContext.filesDir, "updates")

    private fun mergeNewer(
        list: List<AppUpdateChecker.RemoteVersion>,
    ): List<AppUpdateChecker.RemoteVersion> =
        list
            .distinctBy { it.versionCode }
            .sortedByDescending { it.versionCode }

    private fun peekReadyFromDisk(): UiState.ReadyToInstall? {
        val pending = loadPending() ?: return null
        val apk = File(pending.path)
        if (!apk.isFile || apk.length() < 10_000L) return null
        if (pending.versionCode <= BuildConfig.VERSION_CODE) return null
        val remote = AppUpdateChecker.RemoteVersion(
            versionCode = pending.versionCode,
            versionName = pending.versionName,
            apkUrl = pending.apkUrl,
            notes = pending.notes,
        )
        val newer = mergeNewer(lastNewer + remote)
        return UiState.ReadyToInstall(
            apk = apk,
            remote = remote,
            newer = newer,
            permissionHint = if (!canRequestPackageInstalls()) {
                "Allow installs from this app, then tap Install (APK already downloaded)"
            } else {
                null
            },
        )
    }

    private data class PendingApk(
        val path: String,
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val notes: String,
    )

    private fun savePending(apk: File, remote: AppUpdateChecker.RemoteVersion) {
        prefs.edit()
            .putString(KEY_PATH, apk.absolutePath)
            .putInt(KEY_CODE, remote.versionCode)
            .putString(KEY_NAME, remote.versionName)
            .putString(KEY_URL, remote.apkUrl)
            .putString(KEY_NOTES, remote.notes)
            .apply()
    }

    private fun loadPending(): PendingApk? {
        val path = prefs.getString(KEY_PATH, null) ?: return null
        val code = prefs.getInt(KEY_CODE, 0)
        val name = prefs.getString(KEY_NAME, null) ?: return null
        if (code <= 0 || name.isBlank()) return null
        return PendingApk(
            path = path,
            versionCode = code,
            versionName = name,
            apkUrl = prefs.getString(KEY_URL, "").orEmpty(),
            notes = prefs.getString(KEY_NOTES, "").orEmpty(),
        )
    }

    private fun clearPending() {
        prefs.edit().clear().apply()
    }

    private fun fetchCatalog(): AppUpdateChecker.Catalog {
        runCatching {
            val api = httpGetText(AppUpdateChecker.VERSIONS_JSON_API_URL)
            return AppUpdateChecker.parseCatalogFromGitHubContents(api)
        }.onFailure { first ->
            ObdLogger.logDebug(
                ObdLogger.Dir.INFO,
                "versions.json API failed (${first.message}); trying raw",
            )
        }
        runCatching {
            val raw = httpGetText(AppUpdateChecker.VERSIONS_JSON_URL, cacheBust = true)
            return AppUpdateChecker.parseCatalog(raw)
        }.onFailure { second ->
            ObdLogger.logDebug(
                ObdLogger.Dir.INFO,
                "versions.json raw failed (${second.message}); falling back to version.json",
            )
        }
        runCatching {
            val api = httpGetText(AppUpdateChecker.VERSION_JSON_API_URL)
            return AppUpdateChecker.parseCatalogFromGitHubContents(api)
        }
        val raw = httpGetText(AppUpdateChecker.VERSION_JSON_URL, cacheBust = true)
        return AppUpdateChecker.parseCatalog(raw)
    }

    private fun httpGetText(url: String, cacheBust: Boolean = false): String {
        val fetchUrl = if (cacheBust) {
            val sep = if (url.contains('?')) "&" else "?"
            "$url${sep}t=${System.currentTimeMillis()}"
        } else {
            url
        }
        val conn = (URL(fetchUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code fetching $url")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpDownload(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code downloading APK")
            }
            val total = conn.contentLengthLong.coerceAtLeast(0L)
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        if (total > 0L) {
                            val pct = ((readTotal * 100L) / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.flush()
                }
            }
            onProgress(100)
            if (dest.length() < 10_000L) {
                dest.delete()
                throw IllegalStateException("Downloaded file too small — check network")
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val PREFS = "app_update_pending"
        private const val KEY_PATH = "apk_path"
        private const val KEY_CODE = "version_code"
        private const val KEY_NAME = "version_name"
        private const val KEY_URL = "apk_url"
        private const val KEY_NOTES = "notes"
    }
}
