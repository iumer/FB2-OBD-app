package com.fb2.obd.obd

/**
 * Pure helpers for the in-app updater.
 *
 * Catalog on branch `latest`:
 *   dist/versions.json
 *
 * Prefer the GitHub Contents API — raw.githubusercontent.com can lag hours.
 * [VERSION_JSON_URL] remains as a one-entry fallback for older clients.
 */
object AppUpdateChecker {

    const val VERSIONS_JSON_URL =
        "https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/versions.json"
    const val VERSIONS_JSON_API_URL =
        "https://api.github.com/repos/iumer/FB2-OBD-app/contents/dist/versions.json?ref=latest"

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
        val notes: String = "",
    )

    data class Catalog(
        val latest: RemoteVersion,
        val releases: List<RemoteVersion>,
    )

    sealed class Result {
        data class UpToDate(
            val currentName: String,
            val currentCode: Int,
            val latestName: String,
            val latestCode: Int,
        ) : Result()

        data class Available(
            val currentName: String,
            val currentCode: Int,
            /** Older → newer, so 0.1.16 … 0.1.20 when sitting on 0.1.15. */
            val newer: List<RemoteVersion>,
        ) : Result()

        data class Failed(val message: String) : Result()
    }

    /**
     * Versions the user can jump to: strictly newer than the installed
     * [localCode]. After installing 0.1.18, a later check only returns 0.1.19+.
     */
    fun newerThan(localCode: Int, releases: List<RemoteVersion>): List<RemoteVersion> =
        releases
            .distinctBy { it.versionCode }
            .filter { it.versionCode > localCode }
            .sortedBy { it.versionCode }

    fun compare(
        localCode: Int,
        localName: String,
        catalog: Catalog,
    ): Result {
        val newer = newerThan(localCode, catalog.releases)
        return if (newer.isEmpty()) {
            Result.UpToDate(
                currentName = localName,
                currentCode = localCode,
                latestName = catalog.latest.versionName,
                latestCode = catalog.latest.versionCode,
            )
        } else {
            Result.Available(
                currentName = localName,
                currentCode = localCode,
                newer = newer,
            )
        }
    }

    fun parseCatalog(json: String): Catalog {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("empty update catalog")
        }
        val hasReleases = trimmed.contains("\"releases\"")
        val hasLatest = trimmed.contains("\"latest\"")
        if (!hasReleases && !hasLatest) {
            val one = parseVersionObject(trimmed)
            return Catalog(latest = one, releases = listOf(one))
        }
        val latestObj = extractObjectAfterKey(trimmed, "latest")
        val releaseObjs = extractArrayAfterKey(trimmed, "releases").map { parseVersionObject(it) }
        val latest = when {
            latestObj != null -> parseVersionObject(latestObj)
            releaseObjs.isNotEmpty() -> releaseObjs.maxBy { it.versionCode }
            else -> throw IllegalArgumentException("versions.json has no latest or releases")
        }
        val merged = (listOf(latest) + releaseObjs)
            .distinctBy { it.versionCode }
            .sortedBy { it.versionCode }
        return Catalog(latest = latest, releases = merged)
    }

    /** Decode GitHub Contents API base64 payload into catalog JSON text. */
    fun decodeGitHubContentsJson(apiResponse: String): String {
        val encoded = stringField(apiResponse, "content")
            ?: throw IllegalArgumentException("GitHub contents missing content field")
        val cleaned = encoded.replace("\\n", "").replace("\n", "").trim()
        val bytes = java.util.Base64.getDecoder().decode(cleaned)
        return String(bytes, Charsets.UTF_8)
    }

    fun parseCatalogFromGitHubContents(apiResponse: String): Catalog =
        parseCatalog(decodeGitHubContentsJson(apiResponse))

    internal fun parseVersionObject(json: String): RemoteVersion {
        val code = intField(json, "versionCode")
            ?: throw IllegalArgumentException("release missing versionCode")
        val name = stringField(json, "versionName")?.ifBlank { null }
            ?: throw IllegalArgumentException("release missing versionName")
        val apk = stringField(json, "apkUrl")?.ifBlank { null } ?: DEFAULT_APK_URL
        val notes = stringField(json, "notes").orEmpty()
        return RemoteVersion(
            versionCode = code,
            versionName = name,
            apkUrl = apk,
            notes = notes,
        )
    }

    internal fun extractObjectAfterKey(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        val brace = json.indexOf('{', keyIdx)
        if (brace < 0) return null
        return extractBalanced(json, brace, '{', '}')
    }

    internal fun extractArrayAfterKey(json: String, key: String): List<String> {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return emptyList()
        val bracket = json.indexOf('[', keyIdx)
        if (bracket < 0) return emptyList()
        val body = extractBalanced(json, bracket, '[', ']') ?: return emptyList()
        val inner = body.substring(1, body.length - 1)
        val objects = mutableListOf<String>()
        var i = 0
        while (i < inner.length) {
            val start = inner.indexOf('{', i)
            if (start < 0) break
            val obj = extractBalanced(inner, start, '{', '}') ?: break
            objects += obj
            i = start + obj.length
        }
        return objects
    }

    private fun extractBalanced(text: String, start: Int, open: Char, close: Char): String? {
        if (start !in text.indices || text[start] != open) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun intField(json: String, key: String): Int? {
        val re = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
        return re.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun stringField(json: String, key: String): String? {
        val re = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val raw = re.find(json)?.groupValues?.getOrNull(1) ?: return null
        return raw.replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
