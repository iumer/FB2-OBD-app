package com.fb2.obd.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for OpenAI key + Google Drive folder selection.
 * Migrates once from old plain prefs / GitHub token slots, then clears them.
 */
class SecureCredentialStore(context: Context) {

    private val app = context.applicationContext
    private val prefs: SharedPreferences = createEncryptedPrefs(app)

    init {
        migrateFromPlainPrefs()
    }

    var openAiApiKey: String
        get() = prefs.getString(KEY_OPENAI, null)?.trim().orEmpty()
        set(value) {
            prefs.edit().putString(KEY_OPENAI, value.trim()).apply()
        }

    var driveFolderId: String
        get() = prefs.getString(KEY_DRIVE_FOLDER_ID, null)?.trim().orEmpty()
        set(value) {
            prefs.edit().putString(KEY_DRIVE_FOLDER_ID, value.trim()).apply()
        }

    var driveFolderName: String
        get() = prefs.getString(KEY_DRIVE_FOLDER_NAME, null)?.trim().orEmpty()
        set(value) {
            prefs.edit().putString(KEY_DRIVE_FOLDER_NAME, value.trim()).apply()
        }

    var driveAccountEmail: String
        get() = prefs.getString(KEY_DRIVE_EMAIL, null)?.trim().orEmpty()
        set(value) {
            prefs.edit().putString(KEY_DRIVE_EMAIL, value.trim()).apply()
        }

    fun clearOpenAi() {
        prefs.edit().remove(KEY_OPENAI).apply()
    }

    fun clearDrive() {
        prefs.edit()
            .remove(KEY_DRIVE_FOLDER_ID)
            .remove(KEY_DRIVE_FOLDER_NAME)
            .remove(KEY_DRIVE_EMAIL)
            .apply()
    }

    private fun migrateFromPlainPrefs() {
        val aiPlain = app.getSharedPreferences(PLAIN_AI_PREFS, Context.MODE_PRIVATE)
        val uploadPlain = app.getSharedPreferences(PLAIN_UPLOAD_PREFS, Context.MODE_PRIVATE)
        val legacyOpenAi = aiPlain.getString(PLAIN_AI_KEY, null)?.trim().orEmpty()
        val editor = prefs.edit()
        var changed = false
        if (openAiApiKey.isBlank() && legacyOpenAi.isNotEmpty()) {
            editor.putString(KEY_OPENAI, legacyOpenAi)
            changed = true
        }
        if (changed) editor.apply()
        // Wipe plaintext AI key and any legacy GitHub token slots.
        aiPlain.edit().clear().apply()
        uploadPlain.edit()
            .remove(PLAIN_TOKEN_KEY)
            .remove(PLAIN_OWNER_KEY)
            .remove(PLAIN_REPO_KEY)
            .apply()
        // Drop encrypted GitHub leftovers from older builds (uploads are Drive now).
        prefs.edit()
            .remove(KEY_GITHUB)
            .remove(KEY_OWNER)
            .remove(KEY_REPO)
            .apply()
    }

    companion object {
        private const val PREFS_FILE = "fb2_secure_credentials"
        private const val KEY_OPENAI = "openai_api_key"
        private const val KEY_DRIVE_FOLDER_ID = "drive_folder_id"
        private const val KEY_DRIVE_FOLDER_NAME = "drive_folder_name"
        private const val KEY_DRIVE_EMAIL = "drive_account_email"
        private const val KEY_GITHUB = "github_token"
        private const val KEY_OWNER = "github_owner"
        private const val KEY_REPO = "github_repo"

        private const val PLAIN_AI_PREFS = "ai_analysis"
        private const val PLAIN_AI_KEY = "openai_api_key"
        private const val PLAIN_UPLOAD_PREFS = "log_upload"
        private const val PLAIN_TOKEN_KEY = "github_token"
        private const val PLAIN_OWNER_KEY = "github_owner"
        private const val PLAIN_REPO_KEY = "github_repo"

        /** Mask a secret for Settings display (never show the full value after save). */
        fun maskSecret(value: String): String {
            val v = value.trim()
            if (v.isEmpty()) return ""
            val prefix = when {
                v.startsWith("sk-") -> "sk-"
                v.startsWith("github_pat_") -> "github_pat_"
                v.startsWith("ghp_") -> "ghp_"
                else -> v.take(3)
            }
            val tail = v.takeLast(4)
            return if (v.length <= prefix.length + 4) {
                "$prefix…"
            } else {
                "$prefix…$tail"
            }
        }

        private fun createEncryptedPrefs(app: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                app,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
