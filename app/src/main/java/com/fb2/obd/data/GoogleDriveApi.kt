package com.fb2.obd.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DriveFolder(
    val id: String,
    val name: String,
)

/**
 * Minimal Google Drive v3 REST client via [HttpURLConnection].
 * Never logs the access token or Authorization header.
 */
class GoogleDriveApi(
    private val tokenProvider: suspend () -> String,
) {

    suspend fun listFolders(parentId: String = "root"): List<DriveFolder> =
        withContext(Dispatchers.IO) {
            val token = tokenProvider()
            val q = "mimeType = 'application/vnd.google-apps.folder' and " +
                "'$parentId' in parents and trashed = false"
            val url = buildString {
                append("https://www.googleapis.com/drive/v3/files?")
                append("q=").append(URLEncoder.encode(q, "UTF-8"))
                append("&spaces=drive")
                append("&pageSize=100")
                append("&fields=").append(URLEncoder.encode("files(id,name)", "UTF-8"))
                append("&orderBy=").append(URLEncoder.encode("name", "UTF-8"))
            }
            val json = httpJson(token, "GET", url)
            val files = json.optJSONArray("files") ?: JSONArray()
            buildList {
                for (i in 0 until files.length()) {
                    val o = files.getJSONObject(i)
                    add(DriveFolder(id = o.getString("id"), name = o.getString("name")))
                }
            }
        }

    suspend fun createFolder(name: String, parentId: String = "root"): DriveFolder =
        withContext(Dispatchers.IO) {
            val token = tokenProvider()
            val body = JSONObject().apply {
                put("name", name)
                put("mimeType", "application/vnd.google-apps.folder")
                put("parents", JSONArray().put(parentId))
            }
            val json = httpJson(
                token = token,
                method = "POST",
                url = "https://www.googleapis.com/drive/v3/files?fields=id,name",
                jsonBody = body.toString(),
            )
            DriveFolder(id = json.getString("id"), name = json.getString("name"))
        }

    /** Find a non-trashed file with exact [name] under [parentId]. */
    suspend fun findChildId(name: String, parentId: String): String? =
        withContext(Dispatchers.IO) {
            val token = tokenProvider()
            val q = "name = '${escapeQuery(name)}' and '$parentId' in parents and trashed = false"
            val url = buildString {
                append("https://www.googleapis.com/drive/v3/files?")
                append("q=").append(URLEncoder.encode(q, "UTF-8"))
                append("&spaces=drive")
                append("&pageSize=1")
                append("&fields=").append(URLEncoder.encode("files(id)", "UTF-8"))
            }
            val json = httpJson(token, "GET", url)
            val files = json.optJSONArray("files") ?: return@withContext null
            if (files.length() == 0) null else files.getJSONObject(0).optString("id").ifBlank { null }
        }

    /**
     * Create or replace a file in [parentId].
     * If a same-named file already exists, updates its media (keeps one copy).
     */
    suspend fun uploadOrUpdate(
        parentId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): Unit = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        val existing = findChildIdWithToken(token, fileName, parentId)
        if (existing != null) {
            uploadMedia(
                token,
                "PATCH",
                "https://www.googleapis.com/upload/drive/v3/files/$existing?uploadType=media",
                bytes,
                mimeType,
            )
        } else {
            multipartCreate(token, parentId, fileName, bytes, mimeType)
        }
    }

    suspend fun uploadFile(parentId: String, file: File, remoteName: String, mimeType: String) {
        uploadOrUpdate(parentId, remoteName, file.readBytes(), mimeType)
    }

    private fun findChildIdWithToken(token: String, name: String, parentId: String): String? {
        val q = "name = '${escapeQuery(name)}' and '$parentId' in parents and trashed = false"
        val url = buildString {
            append("https://www.googleapis.com/drive/v3/files?")
            append("q=").append(URLEncoder.encode(q, "UTF-8"))
            append("&spaces=drive")
            append("&pageSize=1")
            append("&fields=").append(URLEncoder.encode("files(id)", "UTF-8"))
        }
        val json = httpJson(token, "GET", url)
        val files = json.optJSONArray("files") ?: return null
        return if (files.length() == 0) null else files.getJSONObject(0).optString("id").ifBlank { null }
    }

    private fun multipartCreate(
        token: String,
        parentId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ) {
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(parentId))
        }.toString()
        val boundary = "fb2_" + System.currentTimeMillis()
        val body = ByteArrayOutputStream()
        fun write(s: String) = body.write(s.toByteArray(StandardCharsets.UTF_8))
        write("--$boundary\r\n")
        write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        write(metadata)
        write("\r\n--$boundary\r\n")
        write("Content-Type: $mimeType\r\n\r\n")
        body.write(bytes)
        write("\r\n--$boundary--\r\n")
        val payload = body.toByteArray()

        val conn = (URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 45_000
            readTimeout = 300_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            setRequestProperty("Content-Length", payload.size.toString())
        }
        try {
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText()
                error("Drive HTTP $code ${sanitize(err)}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun uploadMedia(
        token: String,
        method: String,
        url: String,
        bytes: ByteArray,
        mimeType: String,
    ) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doOutput = true
            connectTimeout = 45_000
            readTimeout = 300_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", mimeType)
            setRequestProperty("Content-Length", bytes.size.toString())
        }
        try {
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText()
                error("Drive HTTP $code ${sanitize(err)}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpJson(
        token: String,
        method: String,
        url: String,
        jsonBody: String? = null,
    ): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        try {
            if (jsonBody != null) {
                conn.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { it.write(jsonBody) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                error("Drive HTTP $code ${sanitize(text)}")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_FOLDER_NAME = "FB2 Diag Logs"
        const val REMOTE_LOGS_SUBFOLDER = "car-uploads"
        const val REMOTE_AI_SUBFOLDER = "ai-reports"

        fun mimeForName(fileName: String): String = when {
            fileName.endsWith(".gz", true) -> "application/gzip"
            fileName.endsWith(".csv", true) -> "text/csv"
            fileName.endsWith(".txt", true) -> "text/plain"
            else -> "application/octet-stream"
        }

        fun escapeQuery(value: String): String =
            value.replace("\\", "\\\\").replace("'", "\\'")

        fun sanitize(err: String?): String =
            (err ?: "")
                .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer …")
                .replace(Regex("ya29\\.[A-Za-z0-9._\\-]+"), "ya29…")
                .replace(Regex("sk-[A-Za-z0-9_\\-]{10,}"), "sk-…")
                .take(200)
    }
}
