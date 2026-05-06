package com.safeharborsecurity.app.ui.safety

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.model.VoicemailScanResult
import com.safeharborsecurity.app.data.model.VoicemailStage
import com.safeharborsecurity.app.data.remote.ClaudeApiService
import com.safeharborsecurity.app.data.remote.model.ClaudeMessage
import com.safeharborsecurity.app.data.remote.model.ClaudeRequest
import com.safeharborsecurity.app.util.VoiceInputManager
import com.safeharborsecurity.app.util.VoiceState
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoicemailUiState(
    val stage: VoicemailStage = VoicemailStage.METHOD_SELECT,
    val transcript: String = "",
    val partialTranscript: String = "",
    val isListening: Boolean = false,
    val manualText: String = "",
    val result: VoicemailScanResult? = null,
    val error: String? = null
)

@HiltViewModel
class VoicemailScannerViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val claudeApi: ClaudeApiService,
    private val gson: Gson,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoicemailUiState())
    val uiState: StateFlow<VoicemailUiState> = _uiState.asStateFlow()

    private var voiceInputManager: VoiceInputManager? = null
    private var voiceCollectorJob: Job? = null

    fun selectListenLive() {
        _uiState.update { it.copy(stage = VoicemailStage.LISTEN_LIVE, transcript = "", partialTranscript = "", error = null) }
    }

    fun selectManualText() {
        _uiState.update { it.copy(stage = VoicemailStage.MANUAL_TEXT, manualText = "", error = null) }
    }

    fun updateManualText(text: String) {
        _uiState.update { it.copy(manualText = text) }
    }

    fun startListening() {
        if (voiceInputManager == null) {
            voiceInputManager = VoiceInputManager(appContext)
        }

        val vim = voiceInputManager ?: return
        vim.reset()
        vim.startListening()
        _uiState.update { it.copy(isListening = true, error = null) }

        voiceCollectorJob?.cancel()
        voiceCollectorJob = viewModelScope.launch {
            vim.voiceResult.collect { result ->
                when (result.state) {
                    VoiceState.LISTENING -> {
                        _uiState.update { it.copy(isListening = true) }
                    }
                    VoiceState.RESULT -> {
                        val newText = result.spokenText
                        if (newText.isNotBlank()) {
                            val current = _uiState.value.transcript
                            val updated = if (current.isBlank()) newText else "$current $newText"
                            _uiState.update { it.copy(transcript = updated, partialTranscript = "") }
                        }
                        // Auto-restart for continuous voicemail capture
                        vim.reset()
                        vim.startListening()
                    }
                    VoiceState.PROCESSING -> {
                        // Speech ended, waiting for final result
                    }
                    VoiceState.ERROR -> {
                        // Silence timeout — just stop listening, not an error
                        _uiState.update { it.copy(isListening = false, partialTranscript = "") }
                    }
                    VoiceState.IDLE -> { }
                }
                // Partial results come through spokenText while state is LISTENING
                if (result.state == VoiceState.LISTENING && result.spokenText.isNotBlank()) {
                    _uiState.update { it.copy(partialTranscript = result.spokenText) }
                }
            }
        }
    }

    fun stopListeningAndAnalyse() {
        voiceInputManager?.stopListening()
        voiceCollectorJob?.cancel()
        _uiState.update { it.copy(isListening = false) }

        val transcript = _uiState.value.transcript.trim()
        if (transcript.length < 10) {
            _uiState.update { it.copy(error = "We didn't capture enough of the voicemail. Please try again or type what you heard.") }
            return
        }

        analyseVoicemailText(transcript)
    }

    fun analyseManualText() {
        val text = _uiState.value.manualText.trim()
        if (text.length < 10) {
            _uiState.update { it.copy(error = "Please enter at least a few words of what the voicemail said.") }
            return
        }
        analyseVoicemailText(text)
    }

    private fun analyseVoicemailText(text: String) {
        _uiState.update { it.copy(stage = VoicemailStage.ANALYSING, error = null) }
        viewModelScope.launch {
            val apiKey = prefs.apiKey.first()
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(stage = VoicemailStage.RESULT, error = "Please add your API key in Settings first.") }
                return@launch
            }

            runCatching {
                val response = claudeApi.sendMessage(
                    apiKey = apiKey,
                    request = ClaudeRequest(
                        messages = listOf(ClaudeMessage(role = "user", content = buildVoicemailPrompt(text))),
                        maxTokens = 1024,
                        system = VOICEMAIL_SYSTEM_PROMPT
                    )
                )

                if (!response.isSuccessful) {
                    _uiState.update { it.copy(stage = VoicemailStage.RESULT, error = "Could not analyse this voicemail right now. Please try again.") }
                    return@launch
                }

                val rawText = response.body()?.content?.firstOrNull()?.text ?: ""
                val jsonStr = extractJson(rawText)
                val parsed = gson.fromJson(jsonStr, VoicemailJsonResponse::class.java)

                val result = VoicemailScanResult(
                    verdict = parsed?.verdict ?: "SUSPICIOUS",
                    confidence = parsed?.confidence ?: 50,
                    summary = parsed?.summary ?: "Could not determine safety.",
                    explanation = parsed?.explanation ?: "",
                    scamType = parsed?.scam_type ?: "",
                    redFlags = parsed?.red_flags ?: emptyList(),
                    recommendedAction = parsed?.recommended_action ?: "Be cautious with this voicemail.",
                    transcriptUsed = text
                )

                _uiState.update { it.copy(stage = VoicemailStage.RESULT, result = result, error = null) }
            }.onFailure {
                _uiState.update { it.copy(stage = VoicemailStage.RESULT, error = "Could not analyse this voicemail. Please try again.") }
            }
        }
    }

    fun shareResult(context: Context) {
        val result = _uiState.value.result ?: return
        val shareText = buildString {
            appendLine("Safe Companion Voicemail Check")
            appendLine("Result: ${result.verdict}")
            appendLine()
            appendLine(result.summary)
            if (result.redFlags.isNotEmpty()) {
                appendLine()
                appendLine("Warning signs:")
                result.redFlags.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine(result.recommendedAction)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Safe Companion Voicemail Check Result")
        }
        context.startActivity(Intent.createChooser(intent, "Share result").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun reset() {
        voiceInputManager?.stopListening()
        voiceCollectorJob?.cancel()
        _uiState.value = VoicemailUiState()
    }

    override fun onCleared() {
        super.onCleared()
        voiceInputManager?.destroy()
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    private fun buildVoicemailPrompt(transcript: String): String = """
Analyse this voicemail transcript for scams. The user is an elderly person who received this voicemail.

Voicemail transcript:
${"\"\"\""}
$transcript
${"\"\"\""}

Respond ONLY with valid JSON:
{
  "verdict": "SAFE" or "SUSPICIOUS" or "DANGEROUS",
  "confidence": 0-100,
  "summary": "1-2 sentence plain English summary for an elderly person",
  "explanation": "Why this is safe or dangerous, in simple language",
  "scam_type": "Type of scam if detected, or empty string",
  "red_flags": ["list", "of", "warning", "signs"],
  "recommended_action": "What the person should do next, in plain English"
}
""".trimIndent()

    companion object {
        private const val VOICEMAIL_SYSTEM_PROMPT = "You are Safe Companion, a voicemail scam detector protecting elderly users. You know these common voicemail scam patterns:\n\n" +
            "1. IRS/tax agency threatening arrest for unpaid taxes\n" +
            "2. Social Security number suspension threats\n" +
            "3. Medicare fraud department warnings\n" +
            "4. Grandparent scam — pretending to be a grandchild in trouble\n" +
            "5. Utility company threatening immediate shutoff\n" +
            "6. Bank fraud department asking for account details\n" +
            "7. Car warranty expiration robocalls\n" +
            "8. Prize/lottery winner notifications\n" +
            "9. Tech support claiming virus detected on computer\n" +
            "10. Immigration enforcement threats\n" +
            "11. Debt collector with aggressive threats\n" +
            "12. Fake government agencies\n" +
            "13. Investment opportunity with guaranteed returns\n\n" +
            "Key scam indicators: urgency/threats, gift card/wire/crypto payment requests, secrecy requests, unusual callback numbers, government agencies never call to threaten arrest, real banks never ask for full account details by phone.\n\n" +
            "Respond ONLY with valid JSON. Use plain English suitable for elderly users. Never use jargon."
    }
}

private data class VoicemailJsonResponse(
    val verdict: String? = null,
    val confidence: Int? = null,
    val summary: String? = null,
    val explanation: String? = null,
    val scam_type: String? = null,
    val red_flags: List<String>? = null,
    val recommended_action: String? = null
)
