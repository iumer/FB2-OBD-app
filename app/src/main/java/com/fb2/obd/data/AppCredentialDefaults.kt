package com.fb2.obd.data

import android.content.Context
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

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            context.assets.open("fb2-secrets.properties").use { stream ->
                Properties().apply { load(stream) }.let { props ->
                    openAiKey = props.getProperty("openai_api_key", "").trim()
                    githubPat = props.getProperty("github_pat", "").trim()
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
}
