package com.fb2.obd.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Properties

/**
 * Pre-filled credentials for the user's private sideload build only.
 * Loaded from `assets/fb2-secrets.properties` (not committed — see `.example`).
 * Values persist in app-private SharedPreferences after first use.
 */
internal object AppCredentialDefaults {

    private var loaded = false
    private var openAiKey = ""
    private var githubPat = ""
    private var fingerprint = ""

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            context.assets.open("fb2-secrets.properties").use { stream ->
                Properties().apply { load(stream) }.let { props ->
                    openAiKey = props.getProperty("openai_api_key", "").trim()
                    githubPat = props.getProperty("github_pat", "").trim()
                    fingerprint = "${openAiKey.takeLast(12)}|${githubPat.takeLast(8)}"
                }
            }
        }
    }

    fun openAiKey(context: Context): String {
        ensureLoaded(context)
        return openAiKey
    }

    fun githubPat(context: Context): String {
        ensureLoaded(context)
        return githubPat
    }

    /**
     * When the packaged secrets file changes (new APK), push keys into prefs so
     * Settings fields and runtime use the updated token without a manual re-paste.
     */
    fun applyPackagedCredentials(
        context: Context,
        openAi: (String) -> Unit,
        github: (String) -> Unit,
    ) {
        ensureLoaded(context)
        if (fingerprint.isBlank()) return
        val prefs: SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val applied = prefs.getString(KEY_FP, null).orEmpty()
        if (applied == fingerprint) return
        if (openAiKey.isNotBlank()) openAi(openAiKey)
        if (githubPat.isNotBlank()) github(githubPat)
        prefs.edit().putString(KEY_FP, fingerprint).apply()
    }

    private const val PREFS = "fb2_packaged_creds"
    private const val KEY_FP = "secrets_fingerprint"
}
