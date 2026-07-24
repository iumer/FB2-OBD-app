package com.fb2.obd.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.obd.VoiceAlertRules
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Critical / hot-band alarms for the car HU.
 *
 * Always plays an audible **alarm tone** (STREAM_ALARM, with STREAM_MUSIC
 * fallback) so the cabin hears something even when TTS is missing, muted on
 * the nav stream, or still initializing. Also speaks the phrase via offline
 * Text-to-Speech when ready.
 *
 * Same alert key is not repeated more often than [cooldownMs].
 */
class VoiceAlerter(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private val speaking = AtomicBoolean(false)
    private val lastSpokenAt = mutableMapOf<String, Long>()
    private val lastHealth = mutableMapOf<String, String>()
    private var focusRequest: AudioFocusRequest? = null
    private var scoStartedByUs = false
    @Volatile
    private var pendingPhrase: String? = null

    private val speechAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val alarmAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

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
            // Tone path still works; keep pending so a later retry can speak.
            return
        }
        val result = engine.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.getDefault()
        }
        engine.setSpeechRate(0.95f)
        engine.setAudioAttributes(speechAttrs)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speaking.set(true)
            }

            override fun onDone(utteranceId: String?) {
                speaking.set(false)
                releaseAudioAfterSpeak()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                speaking.set(false)
                releaseAudioAfterSpeak()
            }
        })
        ready.set(true)
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: TTS ready")
        val pending = pendingPhrase
        pendingPhrase = null
        if (pending != null) {
            speakPhrase("pending", pending, System.currentTimeMillis(), playTone = false)
        }
    }

    /**
     * Evaluate snapshot and sound the highest-priority new/rearmed alert.
     * Tone plays even when TTS is not ready yet.
     */
    fun onSnapshot(
        snapshot: VehicleSnapshot,
        thresholds: HealthThresholds,
        atfC: Double? = null,
        tcSlipRpm: Double? = null,
    ) {
        if (!enabled) return
        start()
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
            (!wasActive) || (now - last >= cooldownMs)
        } ?: return

        if (speaking.get()) return
        announce(candidate.key, candidate.phrase, now)
    }

    /**
     * Settings / toggle check — plays the same critical alarm tone + phrase
     * used in production (defaults to battery critical).
     */
    fun speakTest(phrase: String = "Battery critical") {
        start()
        announce("test", phrase, System.currentTimeMillis())
    }

    private fun announce(key: String, phrase: String, nowMs: Long) {
        lastSpokenAt[key] = nowMs
        lastHealth[key] = phrase
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "VOICE ALERT: $phrase")
        playAlarmTone()
        speakPhrase(key, phrase, nowMs, playTone = false)
    }

    private fun speakPhrase(key: String, phrase: String, @Suppress("UNUSED_PARAMETER") nowMs: Long, playTone: Boolean) {
        if (playTone) playAlarmTone()
        val engine = tts
        if (!ready.get() || engine == null) {
            pendingPhrase = phrase
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: TTS not ready — tone only, queued \"$phrase\"")
            return
        }
        prepareAudioForSpeak()
        speaking.set(true)
        val spoken = engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, key)
        if (spoken != TextToSpeech.SUCCESS) {
            speaking.set(false)
            releaseAudioAfterSpeak()
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: TTS speak failed ($spoken)")
        }
    }

    /**
     * Loud beep on STREAM_ALARM (cabin-friendly). Falls back to STREAM_MUSIC
     * when alarm volume is unavailable. Temporarily unmutes a zeroed stream.
     */
    fun playAlarmTone() {
        prepareAudioForAlarm()
        val streams = intArrayOf(
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_MUSIC,
        )
        for (stream in streams) {
            val restore = ensureAudible(stream)
            val ok = runCatching {
                val tg = ToneGenerator(stream, 100)
                // Distinct urgent pattern — ~1.2s.
                tg.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1_200)
                mainHandler.postDelayed({
                    runCatching { tg.stopTone() }
                    runCatching { tg.release() }
                    restore?.invoke()
                }, 1_400)
                true
            }.getOrDefault(false)
            if (ok) {
                ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: alarm tone on stream=$stream")
                return
            }
            restore?.invoke()
        }
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: alarm tone failed on all streams")
    }

    private fun ensureAudible(stream: Int): (() -> Unit)? {
        return runCatching {
            val cur = audioManager.getStreamVolume(stream)
            val max = audioManager.getStreamMaxVolume(stream)
            if (cur == 0 && max > 0) {
                val target = (max * 2 / 3).coerceAtLeast(1)
                @Suppress("DEPRECATION")
                audioManager.setStreamVolume(stream, target, 0)
                return@runCatching {
                    runCatching {
                        @Suppress("DEPRECATION")
                        audioManager.setStreamVolume(stream, cur, 0)
                    }
                    Unit
                }
            }
            null
        }.getOrNull()
    }

    private fun prepareAudioForAlarm() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(alarmAttrs)
            .setOnAudioFocusChangeListener { /* keep tone going */ }
            .setAcceptsDelayedFocusGain(false)
            .build()
        focusRequest = req
        val result = audioManager.requestAudioFocus(req)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: alarm focus not granted ($result)")
        }
        preferBluetoothRoute()
        // Release focus shortly after the tone finishes (TTS may still hold its own).
        mainHandler.postDelayed({
            if (!speaking.get()) releaseAudioAfterSpeak()
        }, 1_600)
    }

    private fun prepareAudioForSpeak() {
        requestAudioFocus()
        preferBluetoothRoute()
    }

    private fun requestAudioFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAttrs)
            .setOnAudioFocusChangeListener { /* keep utterance going */ }
            .setAcceptsDelayedFocusGain(false)
            .build()
        focusRequest = req
        val result = audioManager.requestAudioFocus(req)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: audio focus not granted ($result)")
        }
    }

    /**
     * Prefer car Bluetooth when A2DP or SCO is connected.
     * A2DP: navigation usage usually routes via the media/BT path automatically.
     * SCO-only kits: start SCO so TTS leaves the phone speaker.
     */
    private fun preferBluetoothRoute() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasA2dp = devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        val hasScoDevice = devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        if (hasA2dp) {
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: BT A2DP available")
            return
        }
        if (!hasScoDevice && !audioManager.isBluetoothScoAvailableOffCall) return
        if (scoStartedByUs || audioManager.isBluetoothScoOn) return
        runCatching {
            audioManager.startBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = true
            scoStartedByUs = true
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: BT SCO started")
        }.onFailure {
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: BT SCO failed (${it.message})")
            scoStartedByUs = false
        }
    }

    private fun releaseAudioAfterSpeak() {
        focusRequest?.let { req ->
            audioManager.abandonAudioFocusRequest(req)
        }
        focusRequest = null
        if (scoStartedByUs) {
            runCatching {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
            scoStartedByUs = false
        }
    }

    fun shutdown() {
        ready.set(false)
        pendingPhrase = null
        mainHandler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
        releaseAudioAfterSpeak()
    }
}
