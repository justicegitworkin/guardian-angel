package com.safeharborsecurity.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.model.VoiceMode
import com.safeharborsecurity.app.data.model.VoiceTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EL_DEBUG"

@Singleton
class SafeHarborVoiceManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val elevenLabsTTS: ElevenLabsTTSManager,
    private val googleCloudTTS: GoogleCloudTTSManager,
    private val prefs: UserPreferences
) {
    private var androidTts: TextToSpeech? = null
    private var isTtsReady = false

    // Source C: Audio focus management — request once per conversation, release when done
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { }
                .build()
            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        try {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {}
        hasAudioFocus = false
        audioFocusRequest = null
    }

    private val _currentTier = MutableStateFlow(VoiceTier.ANDROID_TTS)
    val currentTier: StateFlow<VoiceTier> = _currentTier.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Debug log lines visible on screen (last 6 lines)
    private val _debugLog = MutableStateFlow("")
    val debugLog: StateFlow<String> = _debugLog.asStateFlow()

    private val debugLines = mutableListOf<String>()

    fun addDebugLine(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date())
        val line = "[$time] $message"
        Log.d(TAG, message)
        synchronized(debugLines) {
            debugLines.add(line)
            if (debugLines.size > 6) debugLines.removeAt(0)
            _debugLog.value = debugLines.joinToString("\n")
        }
    }

    // Android TTS persona settings
    private data class TtsPersonaSettings(
        val speechRate: Float = 0.85f,
        val pitch: Float = 1.0f,
        val locale: Locale = Locale.US
    )

    private val androidTtsMap = mapOf(
        "GRACE" to TtsPersonaSettings(0.75f, 0.95f, Locale.US),
        "JAMES" to TtsPersonaSettings(0.85f, 0.90f, Locale.US),
        "SOPHIE" to TtsPersonaSettings(0.90f, 1.0f, Locale.UK),
        "GEORGE" to TtsPersonaSettings(0.60f, 0.85f, Locale.UK)
    )

    fun initialize() {
        if (androidTts != null) return
        androidTts = TextToSpeech(appContext) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS
            if (isTtsReady) {
                androidTts?.language = Locale.US
                addDebugLine("AndroidTTS: initialized OK")
            } else {
                addDebugLine("AndroidTTS: init FAILED status=$status")
            }
        }
    }

    suspend fun speak(
        text: String,
        personaId: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {}
    ) {
        if (text.isBlank()) {
            onDone()
            return
        }

        // Source C: Request audio focus before speaking
        requestAudioFocus()
        _isSpeaking.value = true

        val mode = prefs.voiceMode.first()
        val elevenLabsKey = prefs.elevenLabsApiKey.first()
        val googleKey = prefs.googleNeuralApiKey.first()
        val online = isOnline()

        // Check A — Key read (Part B1: never log prefix/length so key entropy isn't leaked).
        addDebugLine("Check A: EL key=${if (elevenLabsKey.isNotBlank()) "SET" else "MISSING"}")

        val tier = selectTier(mode, elevenLabsKey, googleKey, online)
        _currentTier.value = tier

        // Check C — Tier selection
        addDebugLine("Check C: Tier=$tier | mode=$mode | online=$online | elKeyBlank=${elevenLabsKey.isBlank()}")

        // Mirror the tier diagnostics into logcat so "ElevenLabs not playing"
        // reports can be debugged without needing the in-app debug panel.
        // Never log the actual key — only its presence.
        Log.d(
            TAG,
            "speak() tier=$tier mode=$mode online=$online " +
                "elKeyPresent=${elevenLabsKey.isNotBlank()} " +
                "googleKeyPresent=${googleKey.isNotBlank()}"
        )

        when (tier) {
            VoiceTier.ELEVEN_LABS -> {
                addDebugLine("ElevenLabs: calling API...")
                val result = elevenLabsTTS.speak(text, elevenLabsKey, personaId, onStart) {
                    _isSpeaking.value = false
                    addDebugLine("Playing: ElevenLabs audio done")
                    onDone()
                }
                if (result.isSuccess) {
                    addDebugLine("ElevenLabs: got audio bytes")
                } else {
                    val err = result.exceptionOrNull()?.message ?: "unknown"
                    addDebugLine("ElevenLabs: FAILED — $err")
                    Log.w(TAG, "ElevenLabs failed, falling back", result.exceptionOrNull())
                    fallbackSpeak(text, personaId, googleKey, online, onStart, onDone)
                }
            }
            VoiceTier.GOOGLE_NEURAL -> {
                addDebugLine("GoogleNeural: calling API...")
                val result = googleCloudTTS.speak(text, googleKey, personaId, onStart) {
                    _isSpeaking.value = false
                    onDone()
                }
                if (result.isFailure) {
                    addDebugLine("GoogleNeural: FAILED — ${result.exceptionOrNull()?.message}")
                    speakWithAndroidTts(text, personaId, onStart, onDone)
                }
            }
            VoiceTier.ANDROID_TTS -> {
                // Check F — Android TTS fallback
                addDebugLine("Check F: Fallback — using Android TTS")
                speakWithAndroidTts(text, personaId, onStart, onDone)
            }
        }
    }

    private suspend fun fallbackSpeak(
        text: String, personaId: String, googleKey: String, online: Boolean,
        onStart: () -> Unit, onDone: () -> Unit
    ) {
        // Source H: 150ms delay before fallback to prevent audio blip between engines
        delay(150)
        if (googleKey.isNotBlank() && online) {
            _currentTier.value = VoiceTier.GOOGLE_NEURAL
            addDebugLine("Fallback: trying Google Neural...")
            val result = googleCloudTTS.speak(text, googleKey, personaId, onStart) {
                _isSpeaking.value = false
                onDone()
            }
            if (result.isFailure) {
                addDebugLine("Fallback: Google Neural also failed")
                delay(150) // Source H: delay before second fallback too
                speakWithAndroidTts(text, personaId, onStart, onDone)
            }
        } else {
            addDebugLine("Check F: Fallback — using Android TTS (no Google key)")
            speakWithAndroidTts(text, personaId, onStart, onDone)
        }
    }

    private suspend fun speakWithAndroidTts(
        text: String, personaId: String,
        onStart: () -> Unit, onDone: () -> Unit
    ) {
        _currentTier.value = VoiceTier.ANDROID_TTS
        addDebugLine("AndroidTTS: speaking now")

        if (androidTts == null) {
            // No engine handle at all — try to (re)initialize, then give up gracefully.
            addDebugLine("AndroidTTS: engine null — attempting re-init")
            initialize()
        }

        // User report: voice never speaks back even though text appears.
        // Most common cause: TextToSpeech onInit callback hasn't fired yet
        // when the user finishes their first utterance. Suspend (don't block
        // the main thread) up to ~2.5s for the engine to come online before
        // declaring silent failure.
        if (!isTtsReady) {
            addDebugLine("AndroidTTS: not ready — waiting up to 2500ms")
            val waitDeadline = System.currentTimeMillis() + 2500L
            while (!isTtsReady && System.currentTimeMillis() < waitDeadline) {
                delay(100)
            }
        }

        if (!isTtsReady || androidTts == null) {
            // Surface the failure to the user via a Toast so they don't think
            // the app is broken when no voice tier is available (e.g., no
            // ElevenLabs / Google key AND no system TTS engine installed).
            addDebugLine("AndroidTTS: STILL NOT READY after wait — surfacing toast")
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        appContext,
                        "Voice unavailable. Add an ElevenLabs key in Settings, " +
                            "or install a Text-to-Speech engine in Android Settings → " +
                            "Accessibility → Text-to-speech.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (_: Exception) { /* best effort */ }
            _isSpeaking.value = false
            onDone()
            return
        }

        val settings = androidTtsMap[personaId] ?: androidTtsMap["GRACE"]!!
        androidTts?.apply {
            language = settings.locale
            setSpeechRate(settings.speechRate)
            setPitch(settings.pitch)

            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { onStart() }
                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    onDone()
                }
                @Deprecated("Deprecated")
                override fun onError(utteranceId: String?) {
                    addDebugLine("AndroidTTS: utterance ERROR")
                    _isSpeaking.value = false
                    onDone()
                }
            })

            speak(text, TextToSpeech.QUEUE_FLUSH, null, "safeharbor_${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        elevenLabsTTS.stop()
        googleCloudTTS.stop()
        androidTts?.stop()
        _isSpeaking.value = false
        // Source C: Release audio focus when stopping
        abandonAudioFocus()
    }

    fun release() {
        stop()
        elevenLabsTTS.release()
        googleCloudTTS.release()
        androidTts?.shutdown()
        androidTts = null
        isTtsReady = false
        abandonAudioFocus()
    }

    private fun selectTier(mode: String, elevenLabsKey: String, googleKey: String, online: Boolean): VoiceTier {
        return when (mode) {
            VoiceMode.BEST_QUALITY.name -> {
                when {
                    elevenLabsKey.isNotBlank() && online -> VoiceTier.ELEVEN_LABS
                    googleKey.isNotBlank() && online -> VoiceTier.GOOGLE_NEURAL
                    else -> VoiceTier.ANDROID_TTS
                }
            }
            VoiceMode.SAVE_COST.name -> {
                when {
                    googleKey.isNotBlank() && online -> VoiceTier.GOOGLE_NEURAL
                    else -> VoiceTier.ANDROID_TTS
                }
            }
            VoiceMode.OFFLINE_ONLY.name -> VoiceTier.ANDROID_TTS
            else -> { // AUTO
                when {
                    elevenLabsKey.isNotBlank() && online -> VoiceTier.ELEVEN_LABS
                    googleKey.isNotBlank() && online -> VoiceTier.GOOGLE_NEURAL
                    else -> VoiceTier.ANDROID_TTS
                }
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
