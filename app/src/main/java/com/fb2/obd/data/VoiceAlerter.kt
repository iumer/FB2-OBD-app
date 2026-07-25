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
import com.fb2.obd.obd.VoiceAlertDebouncer
import com.fb2.obd.obd.VoiceAlertRules
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Critical / hot-band alarms for the car HU.
 *
 * Default behaviour is **CarPlay / Z-Link safe**: play a short beep + TTS
 * *without* requesting audio focus, so media is not ducked. Many Android HUs
 * duck Z-Link when focus is taken and never restore volume afterward.
 *
 * Optional [duckMediaDuringAlerts] restores the old MAY_DUCK focus path for
 * units that handle unduck correctly.
 *
 * Spike filter: a condition must stay active for ~2.5s ([VoiceAlertDebouncer])
 * before any sound — brief ELM glitches (e.g. timing) do not alarm.
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
    private val debouncer = VoiceAlertDebouncer()
    private var focusRequest: AudioFocusRequest? = null
    private var scoStartedByUs = false
    @Volatile
    private var pendingPhrase: String? = null

    /** Mix with media — avoids CarPlay duck that some HUs never undo. */
    private val mixSpeechAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Louder path when user opts into ducking media. */
    private val duckSpeechAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val alarmAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    @Volatile
    var enabled: Boolean = true

    /**
     * When true, request transient duck focus (can leave CarPlay quiet on
     * buggy HUs). Default false = play over media without ducking.
     */
    @Volatile
    var duckMediaDuringAlerts: Boolean = false

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
            engine.language = Locale.getDefault()
        }
        engine.setSpeechRate(0.95f)
        applyTtsAttributes(engine)
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
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: TTS ready (duckMedia=$duckMediaDuringAlerts)")
        val pending = pendingPhrase
        pendingPhrase = null
        if (pending != null) {
            speakPhrase("pending", pending, playTone = false)
        }
    }

    fun onSnapshot(
        snapshot: VehicleSnapshot,
        thresholds: HealthThresholds,
        atfC: Double? = null,
        tcSlipRpm: Double? = null,
    ) {
        if (!enabled) return
        start()
        val now = System.currentTimeMillis()
        val raw = VoiceAlertRules.evaluate(snapshot, thresholds, atfC, tcSlipRpm)
        // Require sustained out-of-band — ignore split-second spikes.
        val alerts = debouncer.confirm(raw, now)
        val activeKeys = alerts.map { it.key }.toSet()

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

    /** Immediate — bypasses the spike hold (Settings check / toggle). */
    fun speakTest(phrase: String = "Battery critical") {
        start()
        announce("test", phrase, System.currentTimeMillis())
    }

    private fun announce(key: String, phrase: String, nowMs: Long) {
        lastSpokenAt[key] = nowMs
        lastHealth[key] = phrase
        ObdLogger.logDebug(
            ObdLogger.Dir.INFO,
            "VOICE ALERT: $phrase (duckMedia=$duckMediaDuringAlerts a2dp=${hasA2dp()})",
        )
        playAlarmTone()
        speakPhrase(key, phrase, playTone = false)
    }

    private fun speakPhrase(key: String, phrase: String, playTone: Boolean) {
        if (playTone) playAlarmTone()
        val engine = tts
        if (!ready.get() || engine == null) {
            pendingPhrase = phrase
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: TTS not ready — tone only, queued \"$phrase\"")
            return
        }
        applyTtsAttributes(engine)
        prepareAudioForSpeak()
        speaking.set(true)
        val spoken = engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, key)
        if (spoken != TextToSpeech.SUCCESS) {
            speaking.set(false)
            releaseAudioAfterSpeak()
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: TTS speak failed ($spoken)")
        } else {
            // Hard release if utterance callbacks never fire (common on OEM TTS).
            mainHandler.postDelayed({
                if (speaking.get()) {
                    speaking.set(false)
                    releaseAudioAfterSpeak()
                }
            }, 4_000)
        }
    }

    private fun applyTtsAttributes(engine: TextToSpeech) {
        engine.setAudioAttributes(if (duckMediaDuringAlerts) duckSpeechAttrs else mixSpeechAttrs)
    }

    /**
     * Short beep. Prefer ALARM / NOTIFICATION streams — never STREAM_MUSIC
     * when A2DP/CarPlay is up (that fights Z-Link volume).
     */
    fun playAlarmTone() {
        if (duckMediaDuringAlerts) {
            requestFocus(alarmAttrs)
        }
        // Never start SCO for a beep — SCO kills CarPlay media path.
        val streams = if (hasA2dp()) {
            intArrayOf(AudioManager.STREAM_ALARM, AudioManager.STREAM_NOTIFICATION)
        } else {
            intArrayOf(
                AudioManager.STREAM_ALARM,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_MUSIC,
            )
        }
        for (stream in streams) {
            // Never rewrite MUSIC volume — that is what CarPlay/Z-Link uses.
            val restore = if (stream == AudioManager.STREAM_MUSIC) null else ensureAudible(stream)
            val ok = runCatching {
                val tg = ToneGenerator(stream, 90)
                tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 550)
                mainHandler.postDelayed({
                    runCatching { tg.stopTone() }
                    runCatching { tg.release() }
                    restore?.invoke()
                    if (!speaking.get()) releaseAudioAfterSpeak()
                }, 700)
                true
            }.getOrDefault(false)
            if (ok) {
                ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: tone on stream=$stream")
                return
            }
            restore?.invoke()
        }
        ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: alarm tone failed on all streams")
        if (!speaking.get()) releaseAudioAfterSpeak()
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

    private fun prepareAudioForSpeak() {
        if (duckMediaDuringAlerts) {
            requestFocus(duckSpeechAttrs)
            // SCO only when user opted into duck AND there is no A2DP/CarPlay.
            preferBluetoothScoIfNeeded()
        }
    }

    private fun requestFocus(attrs: AudioAttributes) {
        // Drop any previous focus first so we don't stack requests.
        releaseFocusOnly()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { /* keep alert going */ }
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .build()
        focusRequest = req
        val result = audioManager.requestAudioFocus(req)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: audio focus not granted ($result)")
        }
    }

    private fun hasA2dp(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
    }

    /**
     * SCO-only kits (no A2DP): start SCO so TTS leaves the phone speaker.
     * Never touch SCO when A2DP/CarPlay is present — that is the Z-Link break.
     */
    private fun preferBluetoothScoIfNeeded() {
        if (hasA2dp()) {
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: A2DP/CarPlay present — skip SCO")
            return
        }
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasScoDevice = devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
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

    private fun releaseFocusOnly() {
        focusRequest?.let { req ->
            runCatching { audioManager.abandonAudioFocusRequest(req) }
        }
        focusRequest = null
    }

    private fun releaseAudioAfterSpeak() {
        releaseFocusOnly()
        if (scoStartedByUs) {
            runCatching {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
            scoStartedByUs = false
            ObdLogger.logDebug(ObdLogger.Dir.INFO, "Voice alerts: BT SCO stopped")
        }
    }

    fun shutdown() {
        ready.set(false)
        pendingPhrase = null
        debouncer.reset()
        mainHandler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
        releaseAudioAfterSpeak()
    }
}
