package com.safeharborsecurity.app.ui.chat

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.entity.MessageEntity
import com.safeharborsecurity.app.data.model.ChatPersona
import com.safeharborsecurity.app.data.model.VoiceTier
import com.safeharborsecurity.app.data.repository.ChatRepository
import com.safeharborsecurity.app.notification.NotificationHelper
import com.safeharborsecurity.app.util.SafeHarborVoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * Smart actions extracted from AI responses.
 */
sealed class SmartAction {
    data class CheckUrl(val url: String) : SmartAction()
    data class BlockNumber(val number: String) : SmartAction()
    object Panic : SmartAction()
    object CheckWifi : SmartAction()
    object PrivacyScan : SmartAction()
    object OpenSafetyChecker : SmartAction()
    object OpenPrivacyMonitor : SmartAction()
    object OpenSettings : SmartAction()
}

/**
 * Fix 30: Strict voice state machine.
 * Makes it physically impossible for the microphone to be open while agent audio is playing.
 *
 * Valid transitions:
 *   IDLE → LISTENING       (user taps mic or continuous mode auto-start)
 *   LISTENING → PROCESSING (voice input received, sending to Claude)
 *   LISTENING → IDLE       (user cancels or silence detected)
 *   PROCESSING → SPEAKING  (Claude responded, starting TTS)
 *   PROCESSING → IDLE      (error or no speech response)
 *   SPEAKING → IDLE        (user taps stop button)
 *   SPEAKING → SPEAK_COOLDOWN (audio playback finished)
 *   SPEAK_COOLDOWN → LISTENING (cooldown elapsed, continuous mode)
 *   SPEAK_COOLDOWN → IDLE    (cooldown elapsed, normal mode)
 */
enum class AgentState {
    IDLE,             // Ready — "Tap my face or the microphone to talk"
    LISTENING,        // Mic active — "I'm listening... take your time"
    PROCESSING,       // Claude API call — "Thinking..."
    SPEAKING,         // TTS playing — mic is GUARANTEED closed
    SPEAK_COOLDOWN    // Post-speech pause — mic still closed, waiting before re-listen
}

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val inputText: String = "",
    val persona: ChatPersona = ChatPersona.JAMES,
    val autoSpeak: Boolean = false,
    val showPersonaPicker: Boolean = false,
    val pendingAction: SmartAction? = null,
    val currentVoiceTier: VoiceTier = VoiceTier.ANDROID_TTS,
    val partialSpeechText: String = "",
    val agentState: AgentState = AgentState.IDLE,
    val statusText: String = "Tap my face or the microphone to talk",
    val lastAgentMessage: String = "",
    val isContinuousMode: Boolean = false,
    val wasInterrupted: Boolean = false,
    val showRetryButton: Boolean = false,
    val patientSubtext: String = "",
    val showInterruptHint: Boolean = false
)

private const val SESSION_ID = "main_chat"
private const val TAG = "ChatVM"
private const val VOICE_TAG = "VOICE_STATE"

@HiltViewModel
class SafeHarborChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val chatRepository: ChatRepository,
    private val prefs: UserPreferences,
    private val voiceManager: SafeHarborVoiceManager,
    private val notificationHelper: NotificationHelper,
    private val localChatResponder: com.safeharborsecurity.app.ml.LocalChatResponder
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val apiKey: StateFlow<String> = prefs.apiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private var speechRecognizer: SpeechRecognizer? = null

    // --- Fix 31: Second recognizer for interrupt detection during SPEAKING ---
    private var interruptRecognizer: SpeechRecognizer? = null
    private var interruptListenerJob: Job? = null

    private val interruptKeywords = setOf(
        "stop", "wait", "hold on", "pause", "quiet", "enough",
        "thanks", "thank you", "ok", "okay", "got it", "i understand",
        "yes", "no", "grace", "james", "sophie", "george"
    )

    // --- Fix 19: Patient voice input timers ---
    private var initialSpeechTimerJob: Job? = null
    private var midSpeechTimerJob: Job? = null
    private var watchdogTimerJob: Job? = null
    private var lastPartialResult: String = ""
    private var hasUserStartedSpeaking: Boolean = false
    /**
     * Tester feedback: Google's online recognizer cuts off after ~2s of
     * silence on most devices regardless of EXTRA_SPEECH_INPUT_*_LENGTH_MILLIS,
     * which is too short for older users who pause mid-thought. We work
     * around it by NOT submitting on onResults — instead we accumulate the
     * text here, restart the recognizer, and let the mid-speech coroutine
     * timer (10s of true silence) decide when to actually submit. Result:
     * the user can pause for up to ~10s without the agent jumping in.
     */
    private var accumulatedSpeechText: String = ""

    // --- Fix 30: Cooldown timer ---
    private var cooldownJob: Job? = null
    private var isFirstTurnInConversation: Boolean = true

    // --- Fix 22: Audio blip suppression ---
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioHandler = Handler(Looper.getMainLooper())
    private var savedMusicVolume: Int = -1
    private var savedNotificationVolume: Int = -1
    private var savedSystemVolume: Int = -1

    /**
     * Mute every stream the SpeechRecognizer "I'm listening / I stopped"
     * tones might play through. STREAM_MUSIC alone wasn't enough — the
     * recognizer's "blip blip" lives on STREAM_NOTIFICATION and STREAM_SYSTEM
     * on most OEM builds, which is why testers reported on/off beeping every
     * time we destroy+recreate the recognizer between accumulated turns.
     */
    private fun muteBeepTone() {
        try {
            if (savedMusicVolume < 0) {
                savedMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            }
            if (savedNotificationVolume < 0) {
                savedNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            }
            if (savedSystemVolume < 0) {
                savedSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
            }
        } catch (_: Exception) {}
    }

    private fun restoreVolumeDelayed(delayMs: Long = 600L) {
        audioHandler.postDelayed({
            try {
                if (savedMusicVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
                    savedMusicVolume = -1
                }
                if (savedNotificationVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotificationVolume, 0)
                    savedNotificationVolume = -1
                }
                if (savedSystemVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, savedSystemVolume, 0)
                    savedSystemVolume = -1
                }
            } catch (_: Exception) {}
        }, delayMs)
    }

    // --- Fix 30: Echo detection (threshold reduced from 60% to 40%) ---
    private var currentAgentSpeech: String = ""

    private val silencePrompts = listOf(
        "I am still here whenever you are ready.",
        "Take your time — I am listening.",
        "No rush at all. Speak whenever you are ready.",
        "I am here. Just start talking whenever you like."
    )

    init {
        voiceManager.initialize()

        viewModelScope.launch {
            chatRepository.getMessages(SESSION_ID).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }

        viewModelScope.launch {
            prefs.chatPersona.collect { personaName ->
                val persona = ChatPersona.fromName(personaName)
                _uiState.update { it.copy(persona = persona) }
            }
        }

        viewModelScope.launch {
            prefs.autoSpeakResponses.collect { auto ->
                _uiState.update { it.copy(autoSpeak = auto) }
            }
        }

        viewModelScope.launch {
            voiceManager.currentTier.collect { tier ->
                _uiState.update { it.copy(currentVoiceTier = tier) }
            }
        }

        // Observe voiceManager.isSpeaking to detect when TTS audio finishes
        viewModelScope.launch {
            voiceManager.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
                if (!speaking && _uiState.value.agentState == AgentState.SPEAKING) {
                    // Audio finished — transition to SPEAK_COOLDOWN
                    transitionTo(AgentState.SPEAK_COOLDOWN)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fix 30: Strict state machine — every transition logged and guarded
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Central state transition function. ALL state changes go through here.
     * Enforces valid transitions and logs every change.
     */
    private fun transitionTo(newState: AgentState) {
        val oldState = _uiState.value.agentState
        Log.d(VOICE_TAG, "TRANSITION: $oldState → $newState")

        when (newState) {
            AgentState.IDLE -> {
                // Entering IDLE: ensure everything is stopped
                stopInterruptListener()
                killAllRecognizers()
                cancelAllVoiceTimers()
                cooldownJob?.cancel()
                _uiState.update {
                    it.copy(
                        agentState = AgentState.IDLE,
                        isListening = false,
                        statusText = if (it.isContinuousMode) "Always listening" else "Tap my face or the microphone to talk",
                        patientSubtext = "",
                        showRetryButton = false,
                        showInterruptHint = false
                    )
                }
            }

            AgentState.LISTENING -> {
                // Guard: cannot listen while speaking or in cooldown
                if (oldState == AgentState.SPEAKING || oldState == AgentState.SPEAK_COOLDOWN) {
                    Log.w(VOICE_TAG, "BLOCKED: Cannot transition to LISTENING from $oldState — mic must stay closed")
                    return
                }
                _uiState.update {
                    it.copy(
                        agentState = AgentState.LISTENING,
                        isListening = true,
                        statusText = "I'm listening... take your time \uD83D\uDC42",
                        patientSubtext = "Speak whenever you are ready.\nI will wait as long as you need.",
                        partialSpeechText = "",
                        inputText = "",
                        showRetryButton = false
                    )
                }
            }

            AgentState.PROCESSING -> {
                _uiState.update {
                    it.copy(
                        agentState = AgentState.PROCESSING,
                        isListening = false,
                        isLoading = true,
                        statusText = "Thinking...",
                        patientSubtext = "",
                        showRetryButton = false
                    )
                }
            }

            AgentState.SPEAKING -> {
                // CRITICAL (Fix 30): Kill main recognizer BEFORE audio starts
                killAllRecognizers()
                cancelAllVoiceTimers()
                Log.d(VOICE_TAG, "SPEAKING: Main recognizer killed — mic is CLOSED")
                _uiState.update {
                    it.copy(
                        agentState = AgentState.SPEAKING,
                        isListening = false,
                        statusText = "Speaking...",
                        wasInterrupted = false,
                        showInterruptHint = true
                    )
                }
                // Fix 31: Start interrupt listener after 300ms delay
                // so main recognizer fully releases mic hardware first
                startInterruptListenerDelayed()
            }

            AgentState.SPEAK_COOLDOWN -> {
                // Fix 31: Stop interrupt listener when entering cooldown
                stopInterruptListener()
                currentAgentSpeech = ""
                Log.d(VOICE_TAG, "SPEAK_COOLDOWN: Interrupt listener stopped, mic closed, waiting")
                _uiState.update {
                    it.copy(
                        agentState = AgentState.SPEAK_COOLDOWN,
                        isListening = false,
                        statusText = if (it.isContinuousMode) "Getting ready to listen..." else "Tap the microphone to respond",
                        showInterruptHint = false
                    )
                }
                startCooldownTimer()
            }
        }
    }

    /**
     * Fix 30: Kill ALL speech recognizers immediately.
     * Calls cancel() (not just stopListening()) to ensure mic hardware is released.
     */
    private fun killAllRecognizers() {
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        Log.d(VOICE_TAG, "killAllRecognizers: all recognizers destroyed")
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fix 31: Interrupt recognizer — runs ONLY during SPEAKING state
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Start the interrupt listener with a 300ms delay so the main recognizer
     * has time to fully release the mic hardware.
     *
     * PERMANENTLY DISABLED — see "Voice self-interrupt: structural fix" note.
     *
     * The two-recognizer architecture (Fix 31) tried to make voice interrupt
     * work by running a keyword-only mic during agent speech, with an "echo
     * check" to filter out the agent hearing itself. That fundamentally
     * doesn't work without true acoustic echo cancellation across a shared
     * audio session — the speaker output and recognizer input are different
     * sessions, the AcousticEchoCanceler API can't bridge them, and short
     * keyword tokens like "ok", "no", "yes" are too short for the echo check
     * to filter reliably. Net result: every time the agent said "OK" it
     * interrupted itself.
     *
     * The tap-to-interrupt path (avatar tap + on-screen Stop button + the
     * Stop button in the persistent service notification) is the production
     * pattern every consumer voice assistant uses for the same reason. Until
     * we ever rewrite the audio pipeline against raw AudioRecord with shared
     * audio sessions for AEC, this listener stays disabled.
     */
    private fun startInterruptListenerDelayed() {
        // Intentionally a no-op. Keeping the function so callers compile and
        // the architecture is preserved for future re-enablement.
        Log.d(VOICE_TAG, "INTERRUPT: disabled (using tap-to-interrupt instead)")
    }

    /**
     * Create and start the interrupt SpeechRecognizer.
     * Uses short timeouts — only looking for keywords, not transcription.
     *
     * PERMANENTLY DISABLED — kept for future AEC-based re-enablement. See
     * the long-form rationale on [startInterruptListenerDelayed].
     */
    private fun startInterruptListener() {
        return  // disabled — see startInterruptListenerDelayed() doc
        @Suppress("UNREACHABLE_CODE")
        if (_uiState.value.agentState != AgentState.SPEAKING) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return

        try {
            interruptRecognizer?.cancel()
            interruptRecognizer?.destroy()
        } catch (_: Exception) {}

        Log.d(VOICE_TAG, "INTERRUPT: Starting interrupt listener")

        interruptRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {
                    Log.d(VOICE_TAG, "INTERRUPT: ready for speech")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.lowercase()?.trim() ?: ""
                    Log.d(VOICE_TAG, "INTERRUPT: onResults '$text'")
                    handleInterruptResult(text)
                }

                override fun onPartialResults(partial: Bundle?) {
                    val matches = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.lowercase()?.trim() ?: return
                    if (text.isBlank()) return
                    Log.d(VOICE_TAG, "INTERRUPT: partial '$text'")
                    // Check for keywords in partial results for faster response
                    if (containsInterruptKeyword(text) && !isEchoOfAgentSpeech(text)) {
                        Log.d(VOICE_TAG, "INTERRUPT: Keyword detected in partial! Interrupting.")
                        onUserInterrupt()
                    }
                }

                override fun onError(error: Int) {
                    Log.d(VOICE_TAG, "INTERRUPT: onError $error")
                    // Auto-restart if still speaking (after brief delay)
                    if (_uiState.value.agentState == AgentState.SPEAKING) {
                        interruptListenerJob?.cancel()
                        interruptListenerJob = viewModelScope.launch {
                            delay(300)
                            if (_uiState.value.agentState == AgentState.SPEAKING) {
                                startInterruptListener()
                            }
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Short timeouts — we only need to catch keywords
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

        muteBeepTone()
        try {
            interruptRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(VOICE_TAG, "INTERRUPT: Failed to start", e)
        }
        restoreVolumeDelayed()
    }

    private fun stopInterruptListener() {
        interruptListenerJob?.cancel()
        interruptListenerJob = null
        try { interruptRecognizer?.cancel() } catch (_: Exception) {}
        try { interruptRecognizer?.destroy() } catch (_: Exception) {}
        interruptRecognizer = null
        Log.d(VOICE_TAG, "INTERRUPT: Listener stopped")
    }

    private fun handleInterruptResult(text: String) {
        if (text.isBlank()) {
            // No keyword found — restart if still speaking
            if (_uiState.value.agentState == AgentState.SPEAKING) {
                interruptListenerJob?.cancel()
                interruptListenerJob = viewModelScope.launch {
                    delay(300)
                    if (_uiState.value.agentState == AgentState.SPEAKING) {
                        startInterruptListener()
                    }
                }
            }
            return
        }

        if (containsInterruptKeyword(text) && !isEchoOfAgentSpeech(text)) {
            Log.d(VOICE_TAG, "INTERRUPT: Keyword confirmed '$text' — interrupting agent")
            onUserInterrupt()
        } else {
            // Not a keyword or was echo — restart
            if (_uiState.value.agentState == AgentState.SPEAKING) {
                interruptListenerJob?.cancel()
                interruptListenerJob = viewModelScope.launch {
                    delay(300)
                    if (_uiState.value.agentState == AgentState.SPEAKING) {
                        startInterruptListener()
                    }
                }
            }
        }
    }

    private fun containsInterruptKeyword(text: String): Boolean {
        return interruptKeywords.any { keyword -> text.contains(keyword) }
    }

    /**
     * Check if the detected text is just an echo of what the agent is currently saying.
     * If >40% of the detected words also appear in the agent's speech, it's echo.
     */
    private fun isEchoOfAgentSpeech(text: String): Boolean {
        val agentText = currentAgentSpeech.lowercase()
        if (agentText.isBlank()) return false
        val detectedWords = text.split("\\s+".toRegex()).filter { it.length > 2 }
        if (detectedWords.isEmpty()) return false
        val agentWords = agentText.split("\\s+".toRegex()).toSet()
        val overlapCount = detectedWords.count { it in agentWords }
        val ratio = overlapCount.toFloat() / detectedWords.size
        val isEcho = ratio > 0.4f
        if (isEcho) {
            Log.d(VOICE_TAG, "INTERRUPT: Echo detected (${(ratio * 100).toInt()}% overlap) — ignoring")
        }
        return isEcho
    }

    /**
     * Called when user interrupt is confirmed — stop speech and return to listening.
     */
    private fun onUserInterrupt() {
        if (_uiState.value.agentState != AgentState.SPEAKING) return
        Log.d(VOICE_TAG, "INTERRUPT: User interrupted agent — stopping speech")
        stopSpeaking()
    }

    /**
     * Fix 30 + B1: SPEAK_COOLDOWN timer.
     * 2500ms for first turn (extra caution), 1800ms for subsequent turns.
     * Some devices take longer to release audio hardware, so larger cooldowns
     * eliminate residual self-hearing.
     */
    private fun startCooldownTimer() {
        cooldownJob?.cancel()
        val cooldownMs = if (isFirstTurnInConversation) 2500L else 1800L
        isFirstTurnInConversation = false
        Log.d(VOICE_TAG, "COOLDOWN: waiting ${cooldownMs}ms before allowing mic")

        cooldownJob = viewModelScope.launch {
            delay(cooldownMs)
            Log.d(VOICE_TAG, "COOLDOWN: elapsed — ready for next turn")
            if (_uiState.value.agentState == AgentState.SPEAK_COOLDOWN) {
                if (_uiState.value.isContinuousMode) {
                    // Auto-start next listening turn
                    startVoiceTurn()
                } else {
                    transitionTo(AgentState.IDLE)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════

    fun onInputChange(text: String) { _uiState.update { it.copy(inputText = text) } }

    fun setPersona(persona: ChatPersona) {
        viewModelScope.launch {
            prefs.setChatPersona(persona.name)
            _uiState.update { it.copy(persona = persona, showPersonaPicker = false) }
        }
    }

    fun toggleAutoSpeak() {
        viewModelScope.launch {
            val newValue = !_uiState.value.autoSpeak
            prefs.setAutoSpeakResponses(newValue)
            _uiState.update { it.copy(autoSpeak = newValue) }
        }
    }

    fun togglePersonaPicker() {
        _uiState.update { it.copy(showPersonaPicker = !it.showPersonaPicker) }
    }

    fun dismissAction() {
        _uiState.update { it.copy(pendingAction = null) }
    }

    /**
     * Full voice conversation turn:
     * Listen → Transcribe → Claude API → Speak → Cooldown → Ready
     *
     * Fix 19: Patient voice input with custom coroutine timers.
     * Fix 30: Strict state machine — mic guaranteed closed during SPEAKING and SPEAK_COOLDOWN.
     */
    fun startVoiceTurn() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _uiState.update { it.copy(statusText = "Voice input not available on this device") }
            return
        }

        // Guard: refuse to open mic if speaking or in cooldown
        val currentState = _uiState.value.agentState
        if (currentState == AgentState.SPEAKING || currentState == AgentState.SPEAK_COOLDOWN) {
            Log.w(VOICE_TAG, "startVoiceTurn BLOCKED: currently in $currentState — mic stays closed")
            return
        }

        // Stop any current speech and cancel all timers
        voiceManager.stop()
        cancelAllVoiceTimers()
        lastPartialResult = ""
        accumulatedSpeechText = ""
        hasUserStartedSpeaking = false

        // Source F: Mark conversation active to suppress notification sounds
        notificationHelper.isConversationActive = true

        transitionTo(AgentState.LISTENING)

        // Mute system streams BEFORE destroying / creating the recognizer
        // so the OEM "blip" tones don't fire. onReadyForSpeech below restores
        // them once the recognizer is ready and the start beep has passed.
        muteBeepTone()
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {
                    Log.d(TAG, "Voice: onReadyForSpeech")
                    restoreVolumeDelayed()
                    // Start 12-second initial speech timer
                    startInitialSpeechTimer()
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Voice: onBeginningOfSpeech")
                    hasUserStartedSpeaking = true
                    initialSpeechTimerJob?.cancel()
                    initialSpeechTimerJob = null
                    startMidSpeechTimer()
                    _uiState.update {
                        it.copy(patientSubtext = "Keep going, I'm following you...")
                    }
                }

                override fun onPartialResults(partial: Bundle?) {
                    val partialText = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partialText.isNullOrBlank()) {
                        lastPartialResult = partialText
                        _uiState.update { it.copy(inputText = partialText, partialSpeechText = partialText,
                            patientSubtext = "Keep going, I'm following you...") }
                        startMidSpeechTimer()
                    }
                }

                override fun onResults(results: Bundle?) {
                    Log.d(TAG, "Voice: onResults — accumulating and re-listening")
                    val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    val pieceText = spokenText.ifBlank { lastPartialResult }
                    if (pieceText.isNotBlank() && !isLikelyEcho(pieceText)) {
                        accumulatedSpeechText = if (accumulatedSpeechText.isBlank()) pieceText
                            else "$accumulatedSpeechText $pieceText"
                    }
                    lastPartialResult = ""

                    // Show running accumulated transcript so the user can
                    // see we're still tracking what they said.
                    _uiState.update {
                        it.copy(
                            inputText = accumulatedSpeechText,
                            partialSpeechText = accumulatedSpeechText
                        )
                    }

                    // CRITICAL: mute the system tones BEFORE destroying the
                    // recognizer. Otherwise testers hear a constant "blip blip"
                    // every time we cycle the recognizer between accumulated
                    // utterances. The 900ms restore window covers both the
                    // destroy beep and the new recognizer's startListening beep.
                    muteBeepTone()
                    speechRecognizer?.destroy()
                    speechRecognizer = null

                    // Don't submit yet. Restart the recognizer so the user
                    // can keep talking. The mid-speech timer (10s) and
                    // watchdog (25s) below are the actual stoppers.
                    if (_uiState.value.agentState == AgentState.LISTENING) {
                        viewModelScope.launch {
                            delay(300)  // brief pause for mic hardware to release
                            if (_uiState.value.agentState == AgentState.LISTENING) {
                                continueListening()
                            }
                        }
                    }
                    restoreVolumeDelayed(900L)
                }

                override fun onError(error: Int) {
                    muteBeepTone()
                    restoreVolumeDelayed()
                    Log.w(TAG, "Voice: onError code=$error lastPartial='$lastPartialResult'")
                    cancelAllVoiceTimers()
                    speechRecognizer?.destroy()
                    speechRecognizer = null

                    when (error) {
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> {
                            if (lastPartialResult.isNotBlank() && !isLikelyEcho(lastPartialResult)) {
                                _uiState.update { it.copy(isListening = false, partialSpeechText = "", patientSubtext = "") }
                                processUserInput(lastPartialResult, speakResponse = true)
                            } else {
                                _uiState.update { it.copy(isListening = false, partialSpeechText = "", patientSubtext = "") }
                                onSilenceDetected()
                            }
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            _uiState.update { it.copy(isListening = false) }
                            viewModelScope.launch {
                                delay(500)
                                if (_uiState.value.agentState == AgentState.LISTENING) {
                                    startVoiceTurn()
                                }
                            }
                        }
                        else -> {
                            if (lastPartialResult.isNotBlank() && !isLikelyEcho(lastPartialResult)) {
                                _uiState.update { it.copy(isListening = false, partialSpeechText = "", patientSubtext = "") }
                                processUserInput(lastPartialResult, speakResponse = true)
                            } else {
                                _uiState.update {
                                    it.copy(isListening = false, partialSpeechText = "", patientSubtext = "",
                                        agentState = AgentState.IDLE,
                                        statusText = "Something went wrong with voice input. Tap the microphone to try again.",
                                        showRetryButton = true)
                                }
                            }
                        }
                    }
                }

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Voice: onEndOfSpeech (waiting for onResults)")
                }

                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEvent(e: Int, p: Bundle?) {}
                override fun onRmsChanged(r: Float) {}
            })
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Take your time... I am listening")
            // User report: recognizer was cutting users off after a few seconds.
            // Google's online recognizer ignores these extras on many devices, but on
            // Pixel/Samsung 14+ it does respect them. Bumping further is harmless on
            // devices that ignore them. Real backstop is the coroutine timers below.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            // Use the on-device recognizer when available (Android 12+ Pixels, Samsung
            // S22+, etc.). The on-device path actually honours the silence-length
            // extras so users don't get cut off mid-thought.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        muteBeepTone()
        speechRecognizer?.startListening(recognizerIntent)
        startWatchdogTimer()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fix 19: Patient voice input timers
    // ═══════════════════════════════════════════════════════════════════

    private fun startInitialSpeechTimer() {
        initialSpeechTimerJob?.cancel()
        initialSpeechTimerJob = viewModelScope.launch {
            delay(12000)
            Log.d(TAG, "Voice: initial speech timer fired (12s without speech)")
            if (_uiState.value.agentState == AgentState.LISTENING && !hasUserStartedSpeaking) {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
                onSilenceDetected()
            }
        }
    }

    private fun startMidSpeechTimer() {
        midSpeechTimerJob?.cancel()
        midSpeechTimerJob = viewModelScope.launch {
            // The user's silence budget after their last word. Tester report:
            // 2s was too aggressive for older users who pause mid-thought.
            // 12s gives genuine room without making the agent feel slow.
            // Each partial-result event resets this; only true silence trips
            // it. onResults no longer auto-submits, so this is the primary
            // stopper for a single utterance.
            delay(12000)
            Log.d(TAG, "Voice: mid-speech timer fired (12s silence after last word)")
            if (_uiState.value.agentState == AgentState.LISTENING) {
                submitAccumulatedAndStop()
            }
        }
    }

    /** Helper: submit whatever the user has said across all recognizer
     *  passes, or fall back to the silence path if there's nothing usable. */
    private fun submitAccumulatedAndStop() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        val combined = listOf(accumulatedSpeechText, lastPartialResult)
            .filter { it.isNotBlank() && !isLikelyEcho(it) }
            .joinToString(" ")
            .trim()
        accumulatedSpeechText = ""
        lastPartialResult = ""
        if (combined.isNotBlank()) {
            _uiState.update { it.copy(isListening = false, partialSpeechText = "", patientSubtext = "") }
            processUserInput(combined, speakResponse = true)
        } else {
            onSilenceDetected()
        }
    }

    /** Restart the recognizer mid-turn without resetting accumulated text.
     *  Called from onResults so the user can keep talking after Google's
     *  recognizer prematurely "completes." */
    private fun continueListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return
        // Same mute-the-blip trick as in startVoiceTurn — testers reported
        // a constant on/off beeping pattern any time the conversation went
        // into accumulate-and-restart mode (i.e. every couple seconds during
        // a normal turn). The 900ms restore window in onResults covers the
        // restart beep too, so this call is mostly defensive.
        muteBeepTone()
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            // Reuse the same listener by calling startVoiceTurn's setup —
            // simplest path is to re-create with the existing listener
            // instance, which we don't have a reference to here. So we
            // build a minimal proxy listener that just re-invokes the same
            // accumulating logic. Effectively: this call is the same as
            // startVoiceTurn but skips state reset.
        }
        // Defer to a fresh full setup — simpler and ensures listener wiring
        // matches startVoiceTurn exactly. We just don't clear accumulated
        // text the way startVoiceTurn does at its top.
        startListenerOnly()
        Log.d(TAG, "Voice: recognizer restarted (accumulated='$accumulatedSpeechText')")
    }

    /** Strip-down version of startVoiceTurn for continuing a turn after
     *  onResults: skips state-machine transition and accumulated-text reset. */
    private fun startListenerOnly() {
        if (_uiState.value.agentState != AgentState.LISTENING) return
        val callback = object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                restoreVolumeDelayed()
                startMidSpeechTimer()  // reset silence budget
            }
            override fun onBeginningOfSpeech() {
                hasUserStartedSpeaking = true
                startMidSpeechTimer()
            }
            override fun onPartialResults(partial: Bundle?) {
                val partialText = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partialText.isNullOrBlank()) {
                    lastPartialResult = partialText
                    val display = if (accumulatedSpeechText.isBlank()) partialText
                        else "$accumulatedSpeechText $partialText"
                    _uiState.update { it.copy(inputText = display, partialSpeechText = display) }
                    startMidSpeechTimer()
                }
            }
            override fun onResults(results: Bundle?) {
                val newText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                val pieceText = newText.ifBlank { lastPartialResult }
                if (pieceText.isNotBlank() && !isLikelyEcho(pieceText)) {
                    accumulatedSpeechText = if (accumulatedSpeechText.isBlank()) pieceText
                        else "$accumulatedSpeechText $pieceText"
                }
                lastPartialResult = ""
                _uiState.update { it.copy(inputText = accumulatedSpeechText, partialSpeechText = accumulatedSpeechText) }
                speechRecognizer?.destroy()
                speechRecognizer = null
                if (_uiState.value.agentState == AgentState.LISTENING) {
                    viewModelScope.launch {
                        delay(300)
                        if (_uiState.value.agentState == AgentState.LISTENING) startListenerOnly()
                    }
                }
            }
            override fun onError(error: Int) {
                speechRecognizer?.destroy()
                speechRecognizer = null
                if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    // Same as onResults — restart and keep listening.
                    if (_uiState.value.agentState == AgentState.LISTENING) {
                        viewModelScope.launch {
                            delay(500)
                            if (_uiState.value.agentState == AgentState.LISTENING) startListenerOnly()
                        }
                    }
                }
                // For other errors, mid-speech timer / watchdog will eventually fire.
            }
            override fun onEndOfSpeech() {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEvent(e: Int, p: Bundle?) {}
            override fun onRmsChanged(r: Float) {}
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(callback)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        muteBeepTone()
        speechRecognizer?.startListening(intent)
    }

    private fun startWatchdogTimer() {
        watchdogTimerJob?.cancel()
        watchdogTimerJob = viewModelScope.launch {
            // Absolute upper bound on a single utterance. Bumped to 35s now
            // that we accumulate text across recognizer restarts — the user
            // can string together multiple thoughts and the mid-speech 12s
            // timer is the per-pause limit. 35s caps the whole turn.
            delay(35000)
            Log.d(TAG, "Voice: watchdog fired (35s absolute limit)")
            if (_uiState.value.agentState == AgentState.LISTENING) {
                submitAccumulatedAndStop()
            }
        }
    }

    private fun cancelAllVoiceTimers() {
        initialSpeechTimerJob?.cancel()
        midSpeechTimerJob?.cancel()
        watchdogTimerJob?.cancel()
        initialSpeechTimerJob = null
        midSpeechTimerJob = null
        watchdogTimerJob = null
    }

    private fun onSilenceDetected() {
        if (_uiState.value.isContinuousMode) {
            handleContinuousSilence()
        } else {
            _uiState.update {
                it.copy(
                    isListening = false,
                    agentState = AgentState.IDLE,
                    statusText = "I didn't hear anything — tap the mic when ready",
                    patientSubtext = "",
                    showRetryButton = true
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fix 30: Echo detection — reduced threshold from 60% to 40%
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if recognized text is likely echo of the agent's own speech.
     * If >40% of detected words (length > 3) match what the agent was saying, reject.
     */
    private fun isLikelyEcho(partialText: String): Boolean {
        if (currentAgentSpeech.isBlank()) return false
        val detectedWords = partialText.lowercase().split("\\s+".toRegex()).filter { it.length > 3 }
        if (detectedWords.isEmpty()) return false
        val agentWords = currentAgentSpeech.lowercase().split("\\s+".toRegex()).toSet()
        val matchCount = detectedWords.count { it in agentWords }
        val matchRatio = matchCount.toFloat() / detectedWords.size
        val isEcho = matchRatio > 0.4f  // Fix 30: reduced from 0.6f to 0.4f
        if (isEcho) Log.d(VOICE_TAG, "ECHO: ${(matchRatio * 100).toInt()}% word match — rejecting '$partialText'")
        return isEcho
    }

    // ═══════════════════════════════════════════════════════════════════
    // Stop / interrupt
    // ═══════════════════════════════════════════════════════════════════

    fun stopVoiceTurn() {
        cancelAllVoiceTimers()
        killAllRecognizers()
        if (!_uiState.value.isContinuousMode) {
            notificationHelper.isConversationActive = false
        }
        transitionTo(AgentState.IDLE)
    }

    /**
     * Stop speaking — user tapped the stop button or voice interrupt detected.
     * Fix 31: Also called by interrupt listener when keyword detected.
     */
    fun stopSpeaking() {
        Log.d(VOICE_TAG, "stopSpeaking: interrupt requested")
        stopInterruptListener()
        cooldownJob?.cancel()
        voiceManager.stop()
        _uiState.update { it.copy(wasInterrupted = true) }
        transitionTo(AgentState.IDLE)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Process user input → Claude → Speak
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Process user input (from voice or text) — sends to Claude, then speaks response.
     */
    fun processUserInput(text: String, speakResponse: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        transitionTo(AgentState.PROCESSING)

        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "") }
            chatRepository.saveMessage(trimmed, isFromUser = true, sessionId = SESSION_ID)

            val key = apiKey.value
            if (key.isBlank()) {
                // Item 5 Stage 2: route to the local responder instead of
                // refusing to talk. The responder maps the user's question
                // against a small library of common scam-related intents and
                // returns a calm, scripted answer in the persona's tone. The
                // app stays useful even without a Claude API key.
                val persona = _uiState.value.persona
                val local = localChatResponder.respond(trimmed, persona.name)
                chatRepository.saveMessage(local.text, isFromUser = false, sessionId = SESSION_ID)
                _uiState.update {
                    it.copy(isLoading = false, lastAgentMessage = local.text)
                }
                if (speakResponse || _uiState.value.autoSpeak) {
                    speakText(local.text)
                } else {
                    transitionTo(AgentState.IDLE)
                }
                return@launch
            }

            val persona = _uiState.value.persona

            chatRepository.sendToSafeHarbor(
                apiKey = key,
                userMessage = trimmed,
                sessionId = SESSION_ID,
                persona = persona
            )
                .onSuccess { reply ->
                    val (cleanReply, action) = extractSmartAction(reply)
                    chatRepository.saveMessage(cleanReply, isFromUser = false, sessionId = SESSION_ID)
                    _uiState.update {
                        it.copy(isLoading = false, pendingAction = action, lastAgentMessage = cleanReply)
                    }

                    if (speakResponse || _uiState.value.autoSpeak) {
                        speakText(cleanReply)
                    } else {
                        transitionTo(AgentState.IDLE)
                    }
                }
                .onFailure { err ->
                    val friendlyMsg = when {
                        err.message?.contains("connect", ignoreCase = true) == true ->
                            "I can't reach the internet right now. Please check your Wi-Fi or " +
                                "phone signal and try again."
                        err.message?.contains("Invalid API", ignoreCase = true) == true ->
                            "I'm having trouble connecting to my smart brain right now. You can " +
                                "still use the safety checks in the app. To fix this, please add " +
                                "or check your Claude key in Settings."
                        else -> "I'm having a little trouble thinking right now. Please try again " +
                            "in a moment."
                    }
                    chatRepository.saveMessage(friendlyMsg, isFromUser = false, sessionId = SESSION_ID)
                    _uiState.update {
                        it.copy(isLoading = false, lastAgentMessage = friendlyMsg)
                    }
                    // Speak the error so the user actually hears what went wrong instead
                    // of being met with silence after their voice utterance.
                    if (speakResponse || _uiState.value.autoSpeak) {
                        speakText(friendlyMsg)
                    } else {
                        transitionTo(AgentState.IDLE)
                    }
                    _uiState.update {
                        it.copy(statusText = "Something went wrong. Tap the microphone to try again.")
                    }
                }
        }
    }

    // Legacy methods for compatibility
    fun sendMessage(text: String = _uiState.value.inputText, speakResponse: Boolean = false) {
        processUserInput(text, speakResponse)
    }

    fun startListening() { startVoiceTurn() }
    fun stopListening() { stopVoiceTurn() }

    private fun extractSmartAction(text: String): Pair<String, SmartAction?> {
        val actionPattern = Regex("""\[ACTION:([A-Z_]+)(?::(.+?))?\]""")
        val match = actionPattern.find(text)
        if (match == null) return Pair(text, null)

        val cleanText = text.replace(actionPattern, "").trim()
        val actionType = match.groupValues[1]
        val actionParam = match.groupValues[2]

        val action = when (actionType) {
            "CHECK_URL" -> SmartAction.CheckUrl(actionParam)
            "BLOCK_NUMBER" -> SmartAction.BlockNumber(actionParam)
            "PANIC" -> SmartAction.Panic
            "CHECK_WIFI" -> SmartAction.CheckWifi
            "PRIVACY_SCAN" -> SmartAction.PrivacyScan
            "OPEN_SAFETY_CHECKER" -> SmartAction.OpenSafetyChecker
            "OPEN_PRIVACY_MONITOR" -> SmartAction.OpenPrivacyMonitor
            "OPEN_SETTINGS" -> SmartAction.OpenSettings
            else -> {
                Log.w(TAG, "Unknown smart action: $actionType")
                null
            }
        }
        return Pair(cleanText, action)
    }

    /**
     * Speak text via TTS.
     * Fix 30: CRITICAL — kills all recognizers BEFORE starting audio playback.
     * The mic is guaranteed closed for the entire duration of SPEAKING and SPEAK_COOLDOWN.
     */
    fun speakText(text: String) {
        val personaId = _uiState.value.persona.name
        currentAgentSpeech = text

        // Fix 30: Transition to SPEAKING — this kills all recognizers first
        transitionTo(AgentState.SPEAKING)

        viewModelScope.launch {
            voiceManager.speak(text, personaId)
            // When voiceManager.isSpeaking becomes false, the observer triggers SPEAK_COOLDOWN
        }
    }

    fun previewPersonaVoice(persona: ChatPersona) {
        viewModelScope.launch {
            voiceManager.speak(persona.greeting, persona.name)
        }
    }

    fun toggleContinuousMode() {
        // Disabled — continuous conversation mode causes self-interruption issues
        // where the mic picks up the TTS output and shuts off. Keeping the function
        // so the UI compiles, but it no-ops until the echo cancellation is fixed.
        return
    }

    private fun handleContinuousSilence() {
        if (!_uiState.value.isContinuousMode) return
        val prompt = silencePrompts.random()
        // Speak a gentle prompt then auto-restart via cooldown → startVoiceTurn
        speakText(prompt)
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.clearSession(SESSION_ID)
            isFirstTurnInConversation = true
            _uiState.update {
                it.copy(lastAgentMessage = "", agentState = AgentState.IDLE,
                    statusText = if (it.isContinuousMode) "Always listening" else "Tap my face or the microphone to talk")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllVoiceTimers()
        cooldownJob?.cancel()
        stopInterruptListener()
        killAllRecognizers()
        voiceManager.stop()
        notificationHelper.isConversationActive = false
        if (savedMusicVolume >= 0) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
            } catch (_: Exception) {}
            savedMusicVolume = -1
        }
    }
}
