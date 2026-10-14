package com.fb2.obd.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Uploads **saved** (finished) session CSVs and AI report `.txt` files to the
 * user's Google Drive folder (Settings → Cloud backup).
 *
 * Never uploads the in-progress buffer. Never logs OAuth tokens.
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
    /** Sync markers only — Drive folder id lives in [SecureCredentialStore]. */
    private val prefs: SharedPreferences =
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val driveApi = GoogleDriveApi(
        tokenProvider = { GoogleDriveAuth.accessToken(app) },
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    var driveFolderId: String
        get() = credentials.driveFolderId
        set(value) {
            credentials.driveFolderId = value
        }

    var driveFolderName: String
        get() = credentials.driveFolderName
        set(value) {
            credentials.driveFolderName = value
        }

    var driveAccountEmail: String
        get() = credentials.driveAccountEmail
        set(value) {
            credentials.driveAccountEmail = value
        }

    fun isDriveConfigured(): Boolean =
        GoogleDriveAuth.isSignedIn(app) && driveFolderId.isNotBlank()

    fun refreshAccountFromGoogle() {
        val email = GoogleDriveAuth.accountEmail(app)
        if (email.isNotBlank()) driveAccountEmail = email
        if (!GoogleDriveAuth.isSignedIn(app)) {
            driveAccountEmail = ""
        }
    }

    suspend fun signOutDrive() {
        GoogleDriveAuth.signOut(app)
        credentials.clearDrive()
    }

    suspend fun listFolders(parentId: String = "root"): List<DriveFolder> =
        driveApi.listFolders(parentId)

    suspend fun createDefaultFolder(): DriveFolder {
        val folder = driveApi.createFolder(GoogleDriveApi.DEFAULT_FOLDER_NAME, parentId = "root")
        driveFolderId = folder.id
        driveFolderName = folder.name
        ensureSubfolders(folder.id)
        return folder
    }

    fun setFolder(folder: DriveFolder) {
        driveFolderId = folder.id
        driveFolderName = folder.name
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshOnline()
        override fun onLost(network: Network) = refreshOnline()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refreshOnline()
    }

    fun start() {
        refreshAccountFromGoogle()
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
            refreshAccountFromGoogle()
            if (!isOnline()) {
                _status.value = _status.value.copy(lastMessage = LogUploadErrors.NO_INTERNET)
                return@withContext emptyList()
            }
            if (!GoogleDriveAuth.isSignedIn(app)) {
                _status.value = _status.value.copy(lastMessage = LogUploadErrors.NO_ACCOUNT)
                return@withContext emptyList()
            }
            val rootId = driveFolderId
            if (rootId.isBlank()) {
                _status.value = _status.value.copy(lastMessage = LogUploadErrors.NO_FOLDER)
                return@withContext emptyList()
            }
            _status.value = _status.value.copy(uploading = true, lastMessage = "Uploading…")
            val out = mutableListOf<FileResult>()
            try {
                val logsFolderId = ensureChildFolder(rootId, GoogleDriveApi.REMOTE_LOGS_SUBFOLDER)
                val aiFolderId = ensureChildFolder(rootId, GoogleDriveApi.REMOTE_AI_SUBFOLDER)
                for (saved in sessionLogStore.list()) {
                    if (skipFileName != null && saved.fileName.equals(skipFileName, true)) {
                        out += FileResult(saved.fileName, FileResult.Outcome.SKIPPED_ACTIVE)
                        continue
                    }
                    out += uploadOne(logsFolderId, saved.fileName, File(saved.absolutePath))
                }
                for (rep in aiReportStore?.list().orEmpty()) {
                    out += uploadOne(aiFolderId, rep.fileName, File(rep.absolutePath))
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

    private suspend fun ensureSubfolders(rootId: String) {
        ensureChildFolder(rootId, GoogleDriveApi.REMOTE_LOGS_SUBFOLDER)
        ensureChildFolder(rootId, GoogleDriveApi.REMOTE_AI_SUBFOLDER)
    }

    private suspend fun ensureChildFolder(parentId: String, name: String): String {
        val existing = driveApi.findChildId(name, parentId)
        if (existing != null) return existing
        return driveApi.createFolder(name, parentId).id
    }

    private suspend fun uploadOne(
        parentId: String,
        fileName: String,
        file: File,
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
        return try {
            val payload = LogUploadPayload.prepare(fileName, file.readBytes())
            val remoteName = payload.first
            val bodyBytes = payload.second
            driveApi.uploadOrUpdate(
                parentId = parentId,
                fileName = remoteName,
                bytes = bodyBytes,
                mimeType = GoogleDriveApi.mimeForName(remoteName),
            )
            markSynced(fileName, sha)
            FileResult(fileName, FileResult.Outcome.UPLOADED)
        } catch (e: Exception) {
            FileResult(
                fileName,
                FileResult.Outcome.FAILED,
                e.message ?: "error",
            )
        }
    }

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
    }
}
