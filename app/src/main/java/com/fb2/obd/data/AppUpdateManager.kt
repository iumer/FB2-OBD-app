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
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastNewer: List<AppUpdateChecker.RemoteVersion> = emptyList()

    val localLabel: String
        get() = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

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
        _state.value = when (result) {
            is AppUpdateChecker.Result.UpToDate -> {
                lastNewer = emptyList()
                UiState.UpToDate(
                    "You're up to date — v${result.currentName}",
                )
            }
            is AppUpdateChecker.Result.Available -> {
                lastNewer = result.newer
                UiState.Available(
                    currentName = result.currentName,
                    newer = result.newer,
                )
            }
            is AppUpdateChecker.Result.Failed -> {
                lastNewer = emptyList()
                UiState.Error(result.message)
            }
        }
    }

    suspend fun downloadUpdate(remote: AppUpdateChecker.RemoteVersion): File? {
        val list = lastNewer.ifEmpty {
            (_state.value as? UiState.Available)?.newer
                ?: (_state.value as? UiState.ReadyToInstall)?.newer
                ?: listOf(remote)
        }
        lastNewer = list
        _state.value = UiState.Downloading(remote, 0, list)
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(appContext.cacheDir, "updates").also { it.mkdirs() }
                val safeName = remote.versionName.replace(Regex("[^A-Za-z0-9._-]+"), "_")
                val out = File(dir, "FB2-Diag-$safeName.apk")
                httpDownload(remote.apkUrl, out) { pct ->
                    _state.value = UiState.Downloading(remote, pct, list)
                }
                _state.value = UiState.ReadyToInstall(out, remote, list)
                out
            }.getOrElse {
                _state.value = UiState.Error(it.message?.take(160) ?: "Download failed")
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
            _state.value = UiState.Error("APK missing — download again")
            return false
        }
        if (!canRequestPackageInstalls()) {
            openUnknownSourcesSettings(activity)
            _state.value = UiState.Error("Allow installs from this app, then tap Install again")
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
            true
        }.getOrElse {
            _state.value = UiState.Error(it.message?.take(160) ?: "Install failed")
            false
        }
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
}
