package com.fb2.obd.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persists the OpenAI API key (same pattern as GitHub PAT). */
class AiAnalysisStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    companion object {
        private const val PREFS = "ai_analysis"
        private const val KEY_API = "openai_api_key"
    }
}

/**
 * Minimal OpenAI Chat Completions client via [HttpURLConnection]
 * (no Retrofit/OkHttp — matches [LogUploadManager]).
 */
class OpenAiClient(
    private val apiKeyProvider: () -> String,
    private val model: String = DEFAULT_MODEL,
) {
    data class Result(
        val text: String,
        val model: String,
    )

    suspend fun complete(systemPrompt: String, userMessage: String): Result =
        withContext(Dispatchers.IO) {
            val key = apiKeyProvider().trim()
            require(key.isNotBlank()) { "OpenAI API key missing — add it in Settings" }

            val body = JSONObject().apply {
                put("model", model)
                put("temperature", 0.2)
                put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userMessage)),
                )
            }

            val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 120_000
            }
            try {
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                    it.write(body.toString())
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.let { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText() }
                    ?: ""
                if (code !in 200..299) {
                    error(friendlyHttpError(code, text))
                }
                val json = JSONObject(text)
                val content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                require(content.isNotBlank()) { "OpenAI returned an empty report" }
                Result(text = content, model = json.optString("model", model))
            } catch (e: Exception) {
                // Re-throw with a clean message when the failure is clearly offline/timeout.
                val friendly = AiAnalysisErrors.friendlyMessage(e)
                if (friendly == AiAnalysisErrors.NO_INTERNET ||
                    friendly == AiAnalysisErrors.REQUEST_TIMEOUT
                ) {
                    error(friendly)
                }
                throw e
            } finally {
                conn.disconnect()
            }
        }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val API_URL = "https://api.openai.com/v1/chat/completions"

        private fun friendlyHttpError(code: Int, body: String): String {
            val snippet = body.trim().take(180)
            return when (code) {
                401, 403 -> "OpenAI API key was rejected. Check Settings → AI analysis."
                429 -> "OpenAI rate limit reached. Wait a moment and try again."
                in 500..599 -> "OpenAI server error ($code). Try again shortly."
                else -> "OpenAI HTTP $code${if (snippet.isNotBlank()) ": $snippet" else ""}"
            }
        }
    }
}
