package com.guardianangel.app.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianangel.app.data.datastore.UserPreferences
import com.guardianangel.app.data.local.entity.MessageEntity
import com.guardianangel.app.data.remote.model.ClaudeMessage
import com.guardianangel.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val inputText: String = "",
    val saveHistory: Boolean = false
)

private const val SESSION_ID = "main_chat"

@HiltViewModel
class GuardianChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val chatRepository: ChatRepository,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val apiKey: StateFlow<String> = prefs.apiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val saveHistory: StateFlow<Boolean> = prefs.saveConversationHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // In-memory message list used when saveHistory=false
    private val inMemoryMessages = mutableListOf<MessageEntity>()

    // In-memory context for API calls (parallel to inMemoryMessages, role/content only)
    private val inMemoryContext = mutableListOf<ClaudeMessage>()

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    @Volatile private var isTtsReady = false

    init {
        // Observe the save-history preference
        viewModelScope.launch {
            saveHistory.collect { save ->
                _uiState.update { it.copy(saveHistory = save) }
                if (save) {
                    // Switch to DB-backed mode: observe Room
                    subscribeToDatabase()
                } else {
                    // Switch to in-memory mode: show in-memory list
                    _uiState.update { it.copy(messages = inMemoryMessages.toList()) }
                }
            }
        }
        initTts()
    }

    private var dbJob: kotlinx.coroutines.Job? = null

    private fun subscribeToDatabase() {
        dbJob?.cancel()
        dbJob = viewModelScope.launch {
            chatRepository.getMessages(SESSION_ID).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.85f)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _uiState.update { it.copy(isSpeaking = true) }
                    }
                    override fun onDone(utteranceId: String?) {
                        _uiState.update { it.copy(isSpeaking = false) }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _uiState.update { it.copy(isSpeaking = false) }
                    }
                })
                isTtsReady = true
            }
        }
    }

    fun onInputChange(text: String) { _uiState.update { it.copy(inputText = text) } }

    fun sendMessage(text: String = _uiState.value.inputText, speakResponse: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "", isLoading = true) }

            val key = apiKey.value
            val save = saveHistory.value

            if (save) {
                // ── Persisted mode ──────────────────────────────────────
                chatRepository.saveMessage(trimmed, isFromUser = true, sessionId = SESSION_ID)

                if (key.isBlank()) {
                    chatRepository.saveMessage(
                        "Please add your API key in Settings to chat with Guardian Angel.",
                        isFromUser = false, sessionId = SESSION_ID
                    )
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                chatRepository.sendToGuardian(apiKey = key, userMessage = trimmed, sessionId = SESSION_ID)
                    .onSuccess { reply ->
                        chatRepository.saveMessage(reply, isFromUser = false, sessionId = SESSION_ID)
                        _uiState.update { it.copy(isLoading = false) }
                        if (speakResponse) speakText(reply)
                    }
                    .onFailure { err ->
                        chatRepository.saveMessage(friendlyError(err), isFromUser = false, sessionId = SESSION_ID)
                        _uiState.update { it.copy(isLoading = false) }
                    }

            } else {
                // ── In-memory mode (privacy-first) ──────────────────────
                val userMsg = MessageEntity(content = trimmed, isFromUser = true, sessionId = SESSION_ID)
                inMemoryMessages.add(userMsg)
                inMemoryContext.add(ClaudeMessage(role = "user", content = trimmed))
                _uiState.update { it.copy(messages = inMemoryMessages.toList()) }

                if (key.isBlank()) {
                    val sysMsg = MessageEntity(
                        content = "Please add your API key in Settings to chat with Guardian Angel.",
                        isFromUser = false, sessionId = SESSION_ID
                    )
                    inMemoryMessages.add(sysMsg)
                    _uiState.update { it.copy(messages = inMemoryMessages.toList(), isLoading = false) }
                    return@launch
                }

                chatRepository.sendInMemory(apiKey = key, userMessage = trimmed, memoryContext = inMemoryContext)
                    .onSuccess { reply ->
                        val replyMsg = MessageEntity(content = reply, isFromUser = false, sessionId = SESSION_ID)
                        inMemoryMessages.add(replyMsg)
                        inMemoryContext.add(ClaudeMessage(role = "assistant", content = reply))
                        _uiState.update { it.copy(messages = inMemoryMessages.toList(), isLoading = false) }
                        if (speakResponse) speakText(reply)
                    }
                    .onFailure { err ->
                        val errMsg = MessageEntity(content = friendlyError(err), isFromUser = false, sessionId = SESSION_ID)
                        inMemoryMessages.add(errMsg)
                        _uiState.update { it.copy(messages = inMemoryMessages.toList(), isLoading = false) }
                    }
            }
        }
    }

    /** Clears in-memory conversation without touching the database. */
    fun clearConversation() {
        inMemoryMessages.clear()
        inMemoryContext.clear()
        _uiState.update { it.copy(messages = emptyList()) }
    }

    private fun friendlyError(err: Throwable): String = when {
        err.message?.contains("connect", ignoreCase = true) == true ->
            "Guardian needs internet to help. Please check your WiFi or data."
        err.message?.contains("Invalid API", ignoreCase = true) == true ->
            "There's an issue with the API key. Please check Settings."
        else -> "Guardian is thinking… try again in a moment."
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { _uiState.update { it.copy(isListening = true) } }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    _uiState.update { it.copy(isListening = false, inputText = text) }
                    if (text.isNotBlank()) sendMessage(text, speakResponse = true)
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                }
                override fun onError(error: Int) {
                    _uiState.update { it.copy(isListening = false) }
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                }
                override fun onEndOfSpeech() { _uiState.update { it.copy(isListening = false) } }
                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEvent(e: Int, p: Bundle?) {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onRmsChanged(r: Float) {}
            })
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(recognizerIntent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _uiState.update { it.copy(isListening = false) }
    }

    fun speakText(text: String) {
        if (!isTtsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guardian_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        _uiState.update { it.copy(isSpeaking = false) }
    }

    override fun onCleared() {
        super.onCleared()
        inMemoryMessages.clear()
        inMemoryContext.clear()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
