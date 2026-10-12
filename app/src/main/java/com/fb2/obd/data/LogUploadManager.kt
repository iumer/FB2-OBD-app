package com.fb2.obd.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Uploads **saved** (finished) session CSVs to GitHub `logs/car-uploads/`
 * and AI report `.txt` files to `logs/ai-reports/` on a **private** logs repo.
 *
 * Requires a fine-grained PAT with Contents: Read and write on ONLY that
 * private repo (Settings → Private keys). Never uploads the in-progress buffer.
 * Never logs the token or Authorization header.
 */
class LogUploadManager(
    context: Context,
    private val sessionLogStore: SessionLogStore,
    private val credentials: SecureCredentialStore,
    private val aiReportStore: AiReportStore? = null,
) {
    data class Status(
        val online: Boolean = false,
        val lastMessage: String = "",
        val uploading: Boolean = false,
        val pendingCount: Int = 0,
        val syncedCount: Int = 0,
    )

    data class FileResult(
        val fileName: String,
        val outcome: Outcome,
        val detail: String = "",
    ) {
        enum class Outcome { UPLOADED, ALREADY_SYNCED, SKIPPED_ACTIVE, FAILED }
    }

    private val app = context.applicationContext
    /** Sync markers only — secrets live in [SecureCredentialStore]. */
    private val prefs: SharedPreferences =
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    var githubOwner: String
        get() = credentials.githubOwner
        set(value) {
            credentials.githubOwner = value
        }

    var githubRepo: String
        get() = credentials.githubRepo
        set(value) {
            credentials.githubRepo = value
        }

    var githubToken: String
        get() = credentials.githubToken
        set(value) {
            credentials.githubToken = value
        }

    fun clearToken() = credentials.clearGithubToken()

    fun maskedToken(): String = SecureCredentialStore.maskSecret(githubToken)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshOnline()
        override fun onLost(network: Network) = refreshOnline()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refreshOnline()
    }

    fun start() {
        refreshOnline()
        refreshCounts()
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(req, callback) }
    }

    fun stop() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    fun isOnline(): Boolean {
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        // Car HUs often have INTERNET without VALIDATED (captive / OEM Wi‑Fi).
        // App update already works on that path — do not block log upload on VALIDATED.
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun refreshOnline() {
        val online = isOnline()
        _status.value = _status.value.copy(online = online)
        refreshCounts()
    }

    fun refreshCounts(skipFileName: String? = null) {
        val files = sessionLogStore.list().filter { saved ->
            if (skipFileName != null && saved.fileName.equals(skipFileName, true)) return@filter false
            // Empty / in-progress checkpoint stubs are not uploadable yet.
            File(saved.absolutePath).length() > 32L
        }
        val aiFiles = aiReportStore?.list().orEmpty().filter {
            File(it.absolutePath).length() > 32L
        }
        val syncedLogs = files.count { isSynced(it.fileName, File(it.absolutePath)) }
        val syncedAi = aiFiles.count { isSynced(it.fileName, File(it.absolutePath)) }
        val total = files.size + aiFiles.size
        val synced = syncedLogs + syncedAi
        _status.value = _status.value.copy(
            pendingCount = (total - synced).coerceAtLeast(0),
            syncedCount = synced,
        )
    }

    private fun isSynced(fileName: String, file: File): Boolean {
        if (!file.isFile) return false
        val sha = sha256Hex(file) ?: return false
        return prefs.getString(syncKey(fileName), null) == sha
    }

    private fun markSynced(fileName: String, sha: String) {
        prefs.edit().putString(syncKey(fileName), sha).apply()
    }

    private fun syncKey(fileName: String) = "synced:$fileName"

    /**
     * Upload finished session files. Skips [skipFileName] (active session file if any).
     */
    suspend fun uploadPending(skipFileName: String? = null): List<FileResult> =
        withContext(Dispatchers.IO) {
            refreshOnline()
            if (!isOnline()) {
                _status.value = _status.value.copy(lastMessage = LogUploadErrors.NO_INTERNET)
                return@withContext emptyList()
            }
            val token = githubToken
            if (token.isBlank()) {
                _status.value = _status.value.copy(
                    lastMessage = LogUploadErrors.NO_TOKEN,
                )
                return@withContext emptyList()
            }
            _status.value = _status.value.copy(uploading = true, lastMessage = "Uploading…")
            val out = mutableListOf<FileResult>()
            try {
                for (saved in sessionLogStore.list()) {
                    if (skipFileName != null && saved.fileName.equals(skipFileName, true)) {
                        out += FileResult(saved.fileName, FileResult.Outcome.SKIPPED_ACTIVE)
                        continue
                    }
                    out += uploadOne(token, saved.fileName, File(saved.absolutePath), REMOTE_DIR)
                }
                for (rep in aiReportStore?.list().orEmpty()) {
                    out += uploadOne(token, rep.fileName, File(rep.absolutePath), REMOTE_AI_DIR)
                }
                val uploaded = out.count { it.outcome == FileResult.Outcome.UPLOADED }
                val already = out.count { it.outcome == FileResult.Outcome.ALREADY_SYNCED }
                val failed = out.count { it.outcome == FileResult.Outcome.FAILED }
                val firstFail = out.firstOrNull { it.outcome == FileResult.Outcome.FAILED }
                    ?.let { "${it.fileName}: ${it.detail}" }
                val msg = LogUploadErrors.summarize(
                    uploaded = uploaded,
                    already = already,
                    failed = failed,
                    firstFailure = firstFail,
                    empty = out.isEmpty(),
                )
                _status.value = _status.value.copy(lastMessage = msg, uploading = false)
                refreshCounts()
            } catch (e: Exception) {
                _status.value = _status.value.copy(
                    uploading = false,
                    lastMessage = LogUploadErrors.friendly(e.message),
                )
            }
            out
        }

    private fun uploadOne(
        token: String,
        fileName: String,
        file: File,
        remoteDir: String,
    ): FileResult {
        if (!file.isFile) {
            return FileResult(fileName, FileResult.Outcome.FAILED, "missing file")
        }
        if (file.length() <= 32L) {
            return FileResult(fileName, FileResult.Outcome.SKIPPED_ACTIVE, "empty / in-progress")
        }
        val sha = sha256Hex(file) ?: return FileResult(fileName, FileResult.Outcome.FAILED, "hash")
        if (prefs.getString(syncKey(fileName), null) == sha) {
            return FileResult(fileName, FileResult.Outcome.ALREADY_SYNCED)
        }
        val result = putGitHubFile(token, remoteDir, fileName, file.readBytes())
        return if (result.isSuccess) {
            markSynced(fileName, sha)
            FileResult(fileName, FileResult.Outcome.UPLOADED)
        } else {
            FileResult(
                fileName,
                FileResult.Outcome.FAILED,
                result.exceptionOrNull()?.message ?: "error",
            )
        }
    }

    private fun putGitHubFile(
        token: String,
        remoteDir: String,
        fileName: String,
        bytes: ByteArray,
    ): Result<Unit> {
        return runCatching {
            val payload = LogUploadPayload.prepare(fileName, bytes)
            val remoteName = payload.first
            val bodyBytes = payload.second
            val path = "$remoteDir/$remoteName"
            val api = "https://api.github.com/repos/$githubOwner/$githubRepo/contents/$path"
            if (bodyBytes.size > MAX_CONTENTS_BYTES) {
                error("too large (${bodyBytes.size} bytes, GitHub Contents API max $MAX_CONTENTS_BYTES)")
            }
            val existingSha = getRemoteSha(token, "$api?ref=$DEFAULT_BRANCH")
            val body = JSONObject().apply {
                put("message", "car upload: $remoteName")
                put("content", Base64.encodeToString(bodyBytes, Base64.NO_WRAP))
                put("branch", DEFAULT_BRANCH)
                if (existingSha != null) put("sha", existingSha)
            }
            val conn = (URL(api).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                doOutput = true
                connectTimeout = 45_000
                readTimeout = 300_000
            }
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body.toString()) }
            val code = conn.responseCode
            val err = conn.errorStream?.bufferedReader()?.readText()
            conn.disconnect()
            if (code !in 200..299) {
                error("HTTP $code ${sanitizeRemoteError(err)}")
            }
        }
    }

    private fun getRemoteSha(token: String, api: String): String? {
        return runCatching {
            val conn = (URL(api).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 20_000
                readTimeout = 45_000
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText()
            conn.disconnect()
            if (code == 200 && text != null) JSONObject(text).optString("sha").ifBlank { null } else null
        }.getOrNull()
    }

    private fun sanitizeRemoteError(err: String?): String =
        (err ?: "")
            .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9_\\-.]+"), "Bearer …")
            .replace(Regex("sk-[A-Za-z0-9_\\-]{10,}"), "sk-…")
            .replace(Regex("github_pat_[A-Za-z0-9_]+"), "github_pat_…")
            .replace(Regex("ghp_[A-Za-z0-9]+"), "ghp_…")
            .take(200)

    private fun sha256Hex(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    companion object {
        private const val PREFS = "log_upload"
        val DEFAULT_OWNER: String get() = SecureCredentialStore.DEFAULT_OWNER
        val DEFAULT_REPO: String get() = SecureCredentialStore.DEFAULT_REPO
        const val REMOTE_DIR = "logs/car-uploads"
        const val REMOTE_AI_DIR = "logs/ai-reports"
        const val DEFAULT_BRANCH = "main"
        private const val MAX_CONTENTS_BYTES = 900_000
        private const val USER_AGENT = "FB2-Diag"
    }
}
