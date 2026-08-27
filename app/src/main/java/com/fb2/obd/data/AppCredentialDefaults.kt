package com.fb2.obd.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Properties

/**
 * Optional sideload defaults from `assets/fb2-secrets.properties` (gitignored).
 *
 * Safety rule: packaged values only fill **blank** Settings prefs.
 * Never overwrite a token the user already pasted — APK updates must not
 * clobber a working GitHub/OpenAI key (and must not push a revoked key
 * that was only present in a build asset).
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

    /**
     * Fill blank credential slots from the APK asset. Does nothing when
     * [currentOpenAi] / [currentGithub] are already set.
     */
    fun applyPackagedCredentials(
        context: Context,
        currentOpenAi: String,
        currentGithub: String,
        setOpenAi: (String) -> Unit,
        setGithub: (String) -> Unit,
    ) {
        ensureLoaded(context)
        if (currentOpenAi.isBlank() && openAiKey.isNotBlank()) {
            setOpenAi(openAiKey)
        }
        if (currentGithub.isBlank() && githubPat.isNotBlank()) {
            setGithub(githubPat)
        }
    }

    /** Pure helper for JVM tests (no Android assets). */
    fun fillBlankOnly(
        packagedOpenAi: String,
        packagedGithub: String,
        currentOpenAi: String,
        currentGithub: String,
    ): Pair<String?, String?> {
        val nextOpenAi =
            if (currentOpenAi.isBlank() && packagedOpenAi.isNotBlank()) packagedOpenAi else null
        val nextGithub =
            if (currentGithub.isBlank() && packagedGithub.isNotBlank()) packagedGithub else null
        return nextOpenAi to nextGithub
    }
}
