package com.fb2.obd.data

/**
 * User-facing GitHub log-upload errors. Keep HTTP detail so a car HU
 * "Failed 1" is diagnosable without logcat.
 */
object LogUploadErrors {
    const val NO_INTERNET =
        "No internet — HU Wi‑Fi often needs a real data path, not just a connected icon."
    const val NO_TOKEN =
        "Add a GitHub token in Settings → Private keys (fine-grained PAT, Contents: Read & write on ONLY the private logs repo)."

    fun friendly(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return "Upload failed"
        val up = t.uppercase()
        val privateRepo = "${SecureCredentialStore.DEFAULT_OWNER}/${SecureCredentialStore.DEFAULT_REPO}"
        return when {
            up.contains("HTTP 401") || up.contains("BAD CREDENTIALS") ->
                "GitHub token rejected (401). Paste a fine-grained PAT with Contents: Read & write on ONLY $privateRepo."
            up.contains("HTTP 403") || up.contains("RESOURCE NOT ACCESSIBLE") ->
                "GitHub 403 — token needs Contents: Read & write on ONLY the private logs repo ($privateRepo), not the public app repo."
            up.contains("HTTP 404") ->
                "GitHub 404 — private logs repo or path logs/car-uploads/ not found (check Settings owner/repo; default $privateRepo)."
            up.contains("HTTP 413") || up.contains("TOO LARGE") || up.contains("1048576") ->
                "Log file is over GitHub's 1 MB Contents API limit. Save a shorter session and retry."
            up.contains("HTTP 422") ->
                "GitHub 422 — file already exists; retry (app will send the current SHA)."
            up.contains("TIMEOUT") || up.contains("TIMED OUT") || up.contains("SOCKETTIMEOUT") ->
                "Upload timed out on a slow HU link. Retry on stronger Wi‑Fi; large logs are gzipped automatically."
            else -> sanitize(t).take(180)
        }
    }

    private fun sanitize(text: String): String =
        text
            .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9_\\-.]+"), "Bearer …")
            .replace(Regex("sk-[A-Za-z0-9_\\-]{10,}"), "sk-…")
            .replace(Regex("github_pat_[A-Za-z0-9_]+"), "github_pat_…")
            .replace(Regex("ghp_[A-Za-z0-9]+"), "ghp_…")

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
