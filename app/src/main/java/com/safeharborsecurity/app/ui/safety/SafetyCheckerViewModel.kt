package com.safeharborsecurity.app.ui.safety

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.entity.SafetyCheckResultEntity
import com.safeharborsecurity.app.data.repository.SafetyCheckerRepository
import com.safeharborsecurity.app.data.repository.SafetyVerdict
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.safeharborsecurity.app.data.model.QrAnalysisResult
import com.safeharborsecurity.app.data.model.QrRiskLevel
import com.safeharborsecurity.app.data.model.QrType
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.net.ssl.SSLException

data class SafetyCheckerUiState(
    val isAnalyzing: Boolean = false,
    val analyzingMessage: String = "Safe Companion is checking this for you...",
    val verdict: SafetyVerdict? = null,
    val error: String? = null,
    val history: List<SafetyCheckResultEntity> = emptyList(),
    val selectedImageUri: Uri? = null,
    val qrResult: QrAnalysisResult? = null
)

@HiltViewModel
class SafetyCheckerViewModel @Inject constructor(
    private val repository: SafetyCheckerRepository,
    private val prefs: UserPreferences,
    private val pointsRepository: com.safeharborsecurity.app.data.repository.PointsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SafetyCheckerUiState())
    val uiState: StateFlow<SafetyCheckerUiState> = _uiState.asStateFlow()

    // Track last action for retry
    private sealed class LastCheck {
        data class Text(val text: String) : LastCheck()
        data class Url(val url: String) : LastCheck()
        data class Image(val uri: Uri) : LastCheck()
    }
    private var lastCheck: LastCheck? = null

    init {
        viewModelScope.launch {
            repository.getRecentHistory(20).collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
        // 5 points for opening "Is This Safe?" — once per day. Capped so the
        // user can't farm by leaving and reopening the screen.
        viewModelScope.launch {
            pointsRepository.awardPoints(
                eventType = "OPEN_IS_THIS_SAFE",
                points = 5,
                description = "Opened the Is This Safe? checker",
                maxPerDay = 1
            )
        }
    }

    /** Internal helper: 10 points whenever the user actually runs a safety
     *  check (text, url, image, voicemail, etc.). Capped at 10 awards/day so
     *  testers running lots of checks don't farm 1000 points in a session. */
    private fun awardForCheck(method: String) {
        viewModelScope.launch {
            pointsRepository.awardPoints(
                eventType = "USED_SAFETY_CHECK",
                points = 10,
                description = "Used Is This Safe? to check $method",
                maxPerDay = 10
            )
        }
    }

    fun analyzeText(text: String) {
        lastCheck = LastCheck.Text(text)
        awardForCheck("a message")
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, analyzingMessage = "Checking this message for you...", verdict = null, error = null) }
            val apiKey = prefs.apiKey.first()
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(isAnalyzing = false, error = "Please add your API key in Settings first.") }
                return@launch
            }
            repository.analyzeText(apiKey, text)
                .onSuccess { verdict ->
                    _uiState.update { it.copy(isAnalyzing = false, verdict = verdict) }
                }
                .onFailure { _uiState.update { it.copy(isAnalyzing = false, error = "Could not check this right now. Please try again.") } }
        }
    }

    fun analyzeUrl(url: String) {
        lastCheck = LastCheck.Url(url)
        awardForCheck("a website")
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, analyzingMessage = "Checking this web address...", verdict = null, error = null) }
            val apiKey = prefs.apiKey.first()
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(isAnalyzing = false, error = "Please add your API key in Settings first.") }
                return@launch
            }

            // Basic validation — must look like a URL
            val trimmed = url.trim()
            if (trimmed.isBlank() || (!trimmed.contains(".") && !trimmed.startsWith("http"))) {
                _uiState.update { it.copy(isAnalyzing = false, error = "That doesn't look like a web address. Try something like www.example.com") }
                return@launch
            }

            repository.analyzeUrl(apiKey, trimmed)
                .onSuccess { verdict ->
                    _uiState.update { it.copy(isAnalyzing = false, verdict = verdict) }
                }
                .onFailure { e ->
                    val msg = when (e) {
                        is UnknownHostException -> "We couldn't find that website. Please double-check the address and try again."
                        is SocketTimeoutException -> "The website took too long to respond. Please try again."
                        is SSLException -> "There was a security problem connecting to that website. It may not be safe."
                        is MalformedURLException -> "That doesn't look like a web address. Try something like www.example.com"
                        else -> "Could not check this website right now. Please try again in a moment."
                    }
                    _uiState.update { it.copy(isAnalyzing = false, error = msg) }
                }
        }
    }

    fun onImageSelected(uri: Uri) {
        _uiState.update { it.copy(selectedImageUri = uri, verdict = null, error = null) }
    }

    fun clearSelectedImage() {
        _uiState.update { it.copy(selectedImageUri = null) }
    }

    fun analyzeSelectedImage() {
        val uri = _uiState.value.selectedImageUri ?: return
        analyzeImage(uri)
    }

    fun analyzeImage(uri: Uri) {
        lastCheck = LastCheck.Image(uri)
        awardForCheck("a photo")
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, analyzingMessage = "Reading your photo... please wait", verdict = null, error = null, selectedImageUri = null) }
            val apiKey = prefs.apiKey.first()
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(isAnalyzing = false, error = "Please add your API key in Settings first.") }
                return@launch
            }
            _uiState.update { it.copy(analyzingMessage = "Preparing your photo for Safe Companion...") }
            // Short delay so user sees the loading stage change
            kotlinx.coroutines.delay(300)
            _uiState.update { it.copy(analyzingMessage = "Asking Safe Companion to analyse it...") }
            repository.analyzeImage(apiKey, uri)
                .onSuccess { verdict ->
                    _uiState.update { it.copy(isAnalyzing = false, verdict = verdict) }
                }
                .onFailure { e ->
                    val msg = when {
                        e.message?.contains("Could not read photo") == true ->
                            "Could not open the photo. Please try choosing it again."
                        e.message?.contains("Could not process photo") == true ->
                            "Could not read the photo. It may be stored in the cloud — try downloading it to your device first."
                        e.message?.contains("Photo appears to be empty") == true ->
                            "The photo appears to be empty. Please try a different photo."
                        e.message?.contains("API key invalid") == true ->
                            "Your Claude API key isn't working. Open Settings → Claude API Key and add or replace it."
                        e.message?.contains("lacks permission") == true ->
                            "Your Claude API key doesn't have image-analysis access. " +
                                "Check https://console.anthropic.com — you may need to enable Vision in your account."
                        e.message?.contains("Photo too large") == true ->
                            "That photo is too large for Safe Companion to send. Try a smaller one or a screenshot."
                        e.message?.contains("Rate limit") == true ->
                            "Safe Companion has been asked to look at a lot of photos quickly. Please wait a minute and try again."
                        e.message?.contains("Claude is having a problem") == true ->
                            "Claude (the AI) is having a temporary problem. Please try again in a few minutes."
                        e.message?.contains("API error") == true ->
                            "Couldn't reach Claude — ${e.message}"
                        e.message?.contains("Empty response") == true ->
                            "Safe Companion didn't return a result. Please try again."
                        else -> "Something went wrong analysing this photo. Please try again. (${e.message})"
                    }
                    _uiState.update { it.copy(isAnalyzing = false, error = msg) }
                }
        }
    }

    fun retryLastCheck() {
        when (val check = lastCheck) {
            is LastCheck.Text -> analyzeText(check.text)
            is LastCheck.Url -> analyzeUrl(check.url)
            is LastCheck.Image -> analyzeImage(check.uri)
            null -> _uiState.update { it.copy(error = null) }
        }
    }

    fun clearVerdict() {
        _uiState.update { it.copy(verdict = null, error = null, selectedImageUri = null, qrResult = null) }
    }

    fun onQrCodeDetected(result: QrAnalysisResult) {
        _uiState.update { it.copy(qrResult = result, verdict = null, error = null) }

        // Show instant warning if present
        if (result.instantWarning != null && !result.needsClaudeAnalysis) {
            // Low-risk QR with just an instant warning — show as verdict
            _uiState.update {
                it.copy(
                    verdict = SafetyVerdict(
                        verdict = if (result.qrType.riskLevel >= QrRiskLevel.HIGH) "SUSPICIOUS" else "SAFE",
                        summary = result.instantWarning,
                        details = "QR Code type: ${result.qrType.displayName}\nContent: ${result.rawValue}",
                        whatToDoNext = when (result.qrType) {
                            QrType.WIFI -> "Only connect to WiFi networks you trust."
                            QrType.PAYMENT -> "Only make payments to people or businesses you know."
                            QrType.CRYPTO -> "Be very careful with cryptocurrency QR codes."
                            else -> "If you're not sure, ask someone you trust."
                        },
                        contentType = "QR_CODE"
                    )
                )
            }
            return
        }

        // For URLs and high-risk content, run Claude analysis
        if (result.needsClaudeAnalysis) {
            analyzeQrContent(result)
        } else {
            // Safe, low-risk QR code
            _uiState.update {
                it.copy(
                    verdict = SafetyVerdict(
                        verdict = "SAFE",
                        summary = "This QR code looks safe. It contains ${result.qrType.displayName.lowercase()} information.",
                        details = "QR Code type: ${result.qrType.displayName}\nContent: ${result.rawValue}",
                        whatToDoNext = "You can use this QR code safely.",
                        contentType = "QR_CODE"
                    )
                )
            }
        }
    }

    private fun analyzeQrContent(result: QrAnalysisResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, analyzingMessage = "Checking this QR code for you...") }
            val apiKey = prefs.apiKey.first()
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(isAnalyzing = false, error = "Please add your API key in Settings first.") }
                return@launch
            }

            when (result.qrType) {
                QrType.URL -> {
                    // Route URL QR codes through the existing URL analysis
                    repository.analyzeUrl(apiKey, result.rawValue)
                        .onSuccess { verdict ->
                            val enriched = verdict.copy(contentType = "QR_CODE")
                            _uiState.update { it.copy(isAnalyzing = false, verdict = enriched) }
                        }
                        .onFailure { e ->
                            _uiState.update { it.copy(isAnalyzing = false, error = "Could not check this QR code link. Please try again.") }
                        }
                }
                else -> {
                    // For non-URL content, analyze as text with QR context
                    val qrText = "This content came from a QR code (type: ${result.qrType.displayName}):\n\n${result.rawValue}"
                    repository.analyzeText(apiKey, qrText)
                        .onSuccess { verdict ->
                            val enriched = verdict.copy(contentType = "QR_CODE")
                            _uiState.update { it.copy(isAnalyzing = false, verdict = enriched) }
                        }
                        .onFailure {
                            _uiState.update { it.copy(isAnalyzing = false, error = "Could not check this QR code. Please try again.") }
                        }
                }
            }
        }
    }

}
