package com.fb2.obd.obd

/**
 * Pure version-check helpers for in-app updates against the always-latest sideload.
 *
 * Manifest on branch `latest`:
 *   https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/version.json
 *
 * Prefer [VERSION_JSON_API_URL] — raw.githubusercontent.com CDN can lag hours behind
 * the repo; the Contents API reflects the branch immediately.
 */
object AppUpdateChecker {

    const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/version.json"
    const val VERSION_JSON_API_URL =
        "https://api.github.com/repos/iumer/FB2-OBD-app/contents/dist/version.json?ref=latest"
    const val DEFAULT_APK_URL =
        "https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk"

    data class RemoteVersion(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String = DEFAULT_APK_URL,
    )

    sealed class Result {
        data class UpToDate(
            val currentName: String,
            val currentCode: Int,
            val remoteName: String,
            val remoteCode: Int,
        ) : Result()

        data class Available(
            val currentName: String,
            val currentCode: Int,
            val remote: RemoteVersion,
        ) : Result()

        data class Failed(val message: String) : Result()
    }

    fun compare(
        localCode: Int,
        localName: String,
        remote: RemoteVersion,
    ): Result = when {
        remote.versionCode > localCode -> Result.Available(localName, localCode, remote)
        else -> Result.UpToDate(localName, localCode, remote.versionName, remote.versionCode)
    }

    /**
     * Minimal JSON parse — avoids pulling org.json Android-only types into unit tests.
     * Expected shape: {"versionCode":21,"versionName":"0.1.21","apkUrl":"https://..."}
     */
    fun parseVersionJson(json: String): RemoteVersion {
        val code = intField(json, "versionCode")
            ?: throw IllegalArgumentException("version.json missing versionCode")
        val name = stringField(json, "versionName")?.ifBlank { null }
            ?: throw IllegalArgumentException("version.json missing versionName")
        val apk = stringField(json, "apkUrl")?.ifBlank { null } ?: DEFAULT_APK_URL
        return RemoteVersion(versionCode = code, versionName = name, apkUrl = apk)
    }

    /** Decode GitHub Contents API base64 payload into version.json text. */
    fun decodeGitHubContentsVersionJson(apiResponse: String): String {
        val encoded = stringField(apiResponse, "content")
            ?: throw IllegalArgumentException("GitHub contents missing content field")
        val cleaned = encoded.replace("\\n", "").replace("\n", "").trim()
        val bytes = java.util.Base64.getDecoder().decode(cleaned)
        return String(bytes, Charsets.UTF_8)
    }

    fun parseVersionJsonFromGitHubContents(apiResponse: String): RemoteVersion =
        parseVersionJson(decodeGitHubContentsVersionJson(apiResponse))

    private fun intField(json: String, key: String): Int? {
        val re = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
        return re.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun stringField(json: String, key: String): String? {
        val re = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return re.find(json)?.groupValues?.getOrNull(1)
    }
}
