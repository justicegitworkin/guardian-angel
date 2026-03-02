package com.guardianangel.app.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianangel.app.data.datastore.UserPreferences
import com.guardianangel.app.data.local.entity.CallLogEntity
import com.guardianangel.app.data.repository.CallRepository
import com.guardianangel.app.notification.NotificationHelper
import com.guardianangel.app.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.util.*
import javax.inject.Inject

/** Maximum transcript buffer size (in chars) to prevent unbounded heap growth on long calls. */
private const val MAX_TRANSCRIPT_CHARS = 4_000

/** Minimum milliseconds between successive API analysis calls (debounce). */
private const val ANALYSIS_DEBOUNCE_MS = 12_000L

@AndroidEntryPoint
class CallOverlayService : Service() {

    @Inject lateinit var callRepository: CallRepository
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var callerNumber = ""
    private var transcriptBuffer = StringBuilder()
    private var logId = 0L
    private var lastAnalysisTime = 0L

    // Observable state for the overlay UI — driven from the service, read by Compose
    private val overlayState = MutableStateFlow(OverlayUiState())

    companion object {
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_RISK = "extra_risk"
        const val EXTRA_WARNING = "extra_warning"
        const val NOTIF_ID = 7777
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        callerNumber = intent?.getStringExtra(EXTRA_NUMBER) ?: "Unknown"
        val initialRisk = intent?.getStringExtra(EXTRA_RISK) ?: "UNKNOWN"
        val initialWarning = intent?.getStringExtra(EXTRA_WARNING) ?: ""

        overlayState.value = OverlayUiState(riskLevel = initialRisk, warning = initialWarning)

        startForeground(NOTIF_ID, notificationHelper.showCallScreeningNotification(callerNumber))

        scope.launch {
            logId = callRepository.insertCallLog(
                CallLogEntity(
                    callerNumber = callerNumber,
                    riskLevel = initialRisk,
                    summary = initialWarning,
                    wasScreened = true
                )
            )
        }

        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }

        val composeView = ComposeView(this).apply {
            setContent {
                val state by overlayState.collectAsState()
                GuardianAngelTheme {
                    CallOverlayContent(
                        callerNumber = callerNumber,
                        riskLevel = state.riskLevel,
                        warning = state.warning,
                        isScreening = state.isScreening,
                        onAnswer = { removeOverlay() },
                        onDecline = { removeOverlay() },
                        onScreen = {
                            overlayState.update { it.copy(isScreening = true) }
                            startScreeningWithSpeech()
                        },
                        onHangUp = { removeOverlay() }
                    )
                }
            }
        }

        overlayView = composeView
        windowManager?.addView(composeView, params)
    }

    private fun startScreeningWithSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return

                    // Cap buffer to avoid unbounded growth on long calls
                    if (transcriptBuffer.length + text.length > MAX_TRANSCRIPT_CHARS) {
                        val overflow = (transcriptBuffer.length + text.length) - MAX_TRANSCRIPT_CHARS
                        transcriptBuffer.delete(0, overflow)
                    }
                    transcriptBuffer.append(' ').append(text)

                    // Debounce: only call the API if enough time has elapsed since last call
                    val now = System.currentTimeMillis()
                    if (now - lastAnalysisTime >= ANALYSIS_DEBOUNCE_MS) {
                        lastAnalysisTime = now
                        analyzeTranscript()
                    }

                    startListeningLoop(this@apply)
                }

                override fun onError(error: Int) { startListeningLoop(this@apply) }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(e: Int, p: Bundle?) {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onRmsChanged(r: Float) {}
            })
        }

        startListeningLoop(speechRecognizer!!)
    }

    private fun startListeningLoop(recognizer: SpeechRecognizer) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    private fun analyzeTranscript() {
        val transcript = transcriptBuffer.toString().trim()
        if (transcript.length < 20) return

        scope.launch(Dispatchers.IO) {
            val apiKey = userPreferences.apiKey.first()
            if (apiKey.isBlank()) return@launch

            callRepository.analyzeCallChunk(apiKey, transcript).onSuccess { result ->
                withContext(Dispatchers.Main) {
                    overlayState.update {
                        it.copy(riskLevel = result.riskLevel, warning = result.warning ?: "")
                    }
                    if (result.riskLevel == "SCAM" && result.warning != null) {
                        notificationHelper.showScamCallWarning(callerNumber, result.warning)
                    }
                }

                val current = callRepository.getCallLogById(logId) ?: return@onSuccess
                callRepository.updateCallLog(
                    current.copy(
                        riskLevel = result.riskLevel,
                        summary = result.warning ?: "",
                        transcript = transcript
                    )
                )
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
        speechRecognizer?.destroy()
        speechRecognizer = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private data class OverlayUiState(
    val riskLevel: String = "UNKNOWN",
    val warning: String = "",
    val isScreening: Boolean = false
)

@Composable
fun CallOverlayContent(
    callerNumber: String,
    riskLevel: String,
    warning: String,
    isScreening: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
    onHangUp: () -> Unit
) {
    val bgColor = when (riskLevel) {
        "SCAM" -> ScamRed
        "SUSPICIOUS" -> WarningAmber
        else -> NavyBlue
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Incoming Call", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.8f))
                    Text(callerNumber, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                Text("😇", fontSize = 32.sp)
            }

            if (warning.isNotBlank()) {
                Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Text("⚠️ $warning", modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }

            if (isScreening) {
                Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Text("Guardian is listening…", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (riskLevel == "SCAM" || isScreening) {
                    Button(onClick = onHangUp, modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                        Text("HANG UP", color = ScamRed, style = MaterialTheme.typography.titleSmall)
                    }
                } else {
                    Button(onClick = onAnswer, modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)) {
                        Icon(Icons.Default.Phone, null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Answer", color = Color.White)
                    }
                    Button(onClick = onDecline, modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ScamRed)) {
                        Icon(Icons.Default.PhoneDisabled, null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Decline", color = Color.White)
                    }
                }
            }

            if (!isScreening && riskLevel != "SCAM") {
                OutlinedButton(onClick = onScreen, modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Text("😇 Screen with Guardian", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
