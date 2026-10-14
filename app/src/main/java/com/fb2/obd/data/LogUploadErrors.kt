package com.fb2.obd.data

/**
 * User-facing cloud log-upload errors (Google Drive).
 */
object LogUploadErrors {
    const val NO_INTERNET =
        "No internet — HU Wi‑Fi often needs a real data path, not just a connected icon."
    const val NO_ACCOUNT =
        "Sign in with Google in Settings → Cloud backup, then choose a Drive folder."
    const val NO_FOLDER =
        "Choose a Google Drive folder in Settings → Cloud backup."

    /** @deprecated kept for older tests; prefer [NO_ACCOUNT]. */
    const val NO_TOKEN = NO_ACCOUNT

    fun friendly(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return "Upload failed"
        val up = t.uppercase()
        return when {
            up.contains("NOT SIGNED IN") || up.contains("SIGN_IN") ->
                NO_ACCOUNT
            up.contains("HTTP 401") || up.contains("INVALID_GRANT") || up.contains("AUTH") ->
                "Google Sign-In expired. Sign out and sign in again in Settings → Cloud backup."
            up.contains("HTTP 403") || up.contains("INSUFFICIENT") ->
                "Google Drive permission denied. Sign in again and allow Drive access."
            up.contains("HTTP 404") ->
                "Drive folder not found. Pick the folder again in Settings → Cloud backup."
            up.contains("HTTP 413") || up.contains("TOO LARGE") ->
                "Log file is too large for upload. Save a shorter session and retry."
            up.contains("TIMEOUT") || up.contains("TIMED OUT") || up.contains("SOCKETTIMEOUT") ->
                "Upload timed out on a slow HU link. Retry on stronger Wi‑Fi; large logs are gzipped automatically."
            else -> GoogleDriveApi.sanitize(t).take(180)
        }
    }

    fun summarize(
        uploaded: Int,
        already: Int,
        failed: Int,
        firstFailure: String?,
        empty: Boolean,
    ): String = buildString {
        if (uploaded > 0) append("Uploaded $uploaded. ")
        if (already > 0) append("Already synced $already. ")
        if (failed > 0) {
            append("Failed $failed")
            val detail = firstFailure?.let { friendly(it) }
            if (!detail.isNullOrBlank()) append(" — $detail")
            append(". ")
        }
        if (uploaded == 0 && already > 0 && failed == 0) append("All logs/reports already synced.")
        if (empty) append("No saved logs or AI reports to upload.")
    }.trim()
}
