package com.safeharborsecurity.app.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceState {
    IDLE, LISTENING, PROCESSING, RESULT, ERROR
}

data class VoiceResult(
    val state: VoiceState = VoiceState.IDLE,
    val spokenText: String = "",
    val error: String? = null
)

class VoiceInputManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInput"
    }

    private val _voiceResult = MutableStateFlow(VoiceResult())
    val voiceResult: StateFlow<VoiceResult> = _voiceResult.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var savedMusicVolume: Int = -1

    // Fix 30: AcousticEchoCanceler and NoiseSuppressor
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    init {
        // B3: Confirm AcousticEchoCanceler / NoiseSuppressor availability up front
        // so log readers can verify whether self-hearing is the device's fault.
        val aec = AcousticEchoCanceler.isAvailable()
        val ns = NoiseSuppressor.isAvailable()
        if (aec) {
            Log.d(TAG, "AEC available on this device — will be enabled on each audio session")
        } else {
            Log.w(TAG, "AEC NOT available on this device — self-hearing risk")
        }
        if (ns) {
            Log.d(TAG, "NoiseSuppressor available on this device")
        } else {
            Log.w(TAG, "NoiseSuppressor NOT available on this device")
        }
    }

    /** Source A: Mute STREAM_MUSIC to suppress SpeechRecognizer start beep */
    private fun muteBeepTone() {
        try {
            savedMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not mute beep tone", e)
        }
    }

    /** Source A: Restore STREAM_MUSIC volume after 400ms delay */
    private fun restoreVolumeDelayed() {
        handler.postDelayed({
            try {
                if (savedMusicVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
                    savedMusicVolume = -1
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore volume", e)
            }
        }, 400)
    }

    /**
     * Fix 30: Enable AcousticEchoCanceler and NoiseSuppressor on the audio session
     * if available on the device.
     */
    fun enableAudioProcessing(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler?.release()
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "AcousticEchoCanceler enabled on session $audioSessionId")
                }
            } else {
                Log.d(TAG, "AcousticEchoCanceler not available on this device")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable AcousticEchoCanceler", e)
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor?.release()
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d(TAG, "NoiseSuppressor enabled on session $audioSessionId")
                }
            } else {
                Log.d(TAG, "NoiseSuppressor not available on this device")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable NoiseSuppressor", e)
        }
    }

    private fun releaseAudioProcessing() {
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        echoCanceler = null
        noiseSuppressor = null
    }

    fun startListening() {
        if (!isAvailable) {
            _voiceResult.value = VoiceResult(
                state = VoiceState.ERROR,
                error = "Voice recognition is not available on this device."
            )
            return
        }

        _voiceResult.value = VoiceResult(state = VoiceState.LISTENING)

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                    restoreVolumeDelayed()
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) { }

                override fun onBufferReceived(buffer: ByteArray?) { }

                override fun onEndOfSpeech() {
                    _voiceResult.value = _voiceResult.value.copy(state = VoiceState.PROCESSING)
                }

                override fun onError(error: Int) {
                    muteBeepTone()
                    restoreVolumeDelayed()

                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything. Tap the microphone and speak."
                        SpeechRecognizer.ERROR_AUDIO -> "There was a problem with the microphone."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Please check your internet connection and try again."
                        else -> "Something went wrong. Please try again."
                    }
                    _voiceResult.value = VoiceResult(state = VoiceState.ERROR, error = msg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "Recognized: $text")
                    _voiceResult.value = VoiceResult(state = VoiceState.RESULT, spokenText = text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = partial?.firstOrNull() ?: return
                    _voiceResult.value = _voiceResult.value.copy(spokenText = text)
                }

                override fun onEvent(eventType: Int, params: Bundle?) { }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        muteBeepTone()
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    /**
     * Fix 30: Hard stop — calls both stopListening() AND cancel(), releases audio processing,
     * and ensures the recognizer is fully torn down. Use this when transitioning to SPEAKING
     * to guarantee the microphone is closed.
     */
    fun stopListeningImmediately() {
        Log.d(TAG, "stopListeningImmediately: killing all recognition")
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        releaseAudioProcessing()
    }

    fun reset() {
        _voiceResult.value = VoiceResult()
    }

    fun destroy() {
        if (savedMusicVolume >= 0) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
            } catch (_: Exception) {}
            savedMusicVolume = -1
        }
        releaseAudioProcessing()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
