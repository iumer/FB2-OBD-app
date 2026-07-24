package com.fb2.obd.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.obd.VoiceAlertRules
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Speaks critical / hot-band warnings using the phone’s built-in Text-to-Speech
 * engine (works offline — no downloaded voice packs required).
 *
 * Same alert key is not repeated more often than [cooldownMs].
 */
class VoiceAlerter(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private val speaking = AtomicBoolean(false)
    private val lastSpokenAt = mutableMapOf<String, Long>()
    private val lastHealth = mutableMapOf<String, String>()

    @Volatile
    var enabled: Boolean = true

    /** Minimum gap between repeats of the same alert. */
    var cooldownMs: Long = 45_000L

    fun start() {
        if (tts != null) return
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: TTS init failed ($status)")
            ready.set(false)
            return
        }
        val result = engine.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fall back to device default locale.
            engine.language = Locale.getDefault()
        }
        engine.setSpeechRate(0.95f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speaking.set(true)
            }

            override fun onDone(utteranceId: String?) {
                speaking.set(false)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                speaking.set(false)
            }
        })
        ready.set(true)
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: TTS ready")
    }

    /**
     * Evaluate snapshot and speak the highest-priority new/rearmed alert.
     */
    fun onSnapshot(
        snapshot: VehicleSnapshot,
        thresholds: HealthThresholds,
        atfC: Double? = null,
        tcSlipRpm: Double? = null,
    ) {
        if (!enabled || !ready.get()) return
        val now = System.currentTimeMillis()
        val alerts = VoiceAlertRules.evaluate(snapshot, thresholds, atfC, tcSlipRpm)
        val activeKeys = alerts.map { it.key }.toSet()

        // Clear latch when condition recovers so next critical re-announces immediately.
        lastHealth.keys.toList().forEach { key ->
            if (key !in activeKeys) {
                lastHealth.remove(key)
            }
        }

        val candidate = alerts.firstOrNull { alert ->
            val last = lastSpokenAt[alert.key] ?: 0L
            val wasActive = lastHealth[alert.key] == alert.phrase
            // Speak if new phrase for this key, or cooldown elapsed.
            (!wasActive) || (now - last >= cooldownMs)
        } ?: return

        // Only one utterance at a time — pick the top priority due alert.
        if (speaking.get()) return
        speak(candidate.key, candidate.phrase, now)
    }

    fun speakTest(phrase: String = "Voice alerts ready") {
        if (!ready.get()) {
            start()
            return
        }
        speak("test", phrase, System.currentTimeMillis())
    }

    private fun speak(key: String, phrase: String, nowMs: Long) {
        val engine = tts ?: return
        lastSpokenAt[key] = nowMs
        lastHealth[key] = phrase
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "VOICE ALERT: $phrase")
        engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, key)
    }

    fun shutdown() {
        ready.set(false)
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
