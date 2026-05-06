package com.safeharborsecurity.app.ui.safety

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.model.VoicemailStage
import com.safeharborsecurity.app.ui.components.VerdictIcon
import com.safeharborsecurity.app.ui.components.toVerdict
import com.safeharborsecurity.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicemailScannerScreen(
    onNavigateBack: () -> Unit,
    viewModel: VoicemailScannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voicemail Scanner", fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBlue,
                    titleContentColor = TextOnDark,
                    navigationIconContentColor = TextOnDark
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state.stage) {
                VoicemailStage.METHOD_SELECT -> MethodSelectStage(
                    onListenLive = { viewModel.selectListenLive() },
                    onManualText = { viewModel.selectManualText() }
                )
                VoicemailStage.LISTEN_LIVE -> ListenLiveStage(
                    transcript = state.transcript,
                    partialTranscript = state.partialTranscript,
                    isListening = state.isListening,
                    error = state.error,
                    onStartListening = { viewModel.startListening() },
                    onStopAndAnalyse = { viewModel.stopListeningAndAnalyse() },
                    onBack = { viewModel.reset() }
                )
                VoicemailStage.MANUAL_TEXT -> ManualTextStage(
                    text = state.manualText,
                    error = state.error,
                    onTextChange = { viewModel.updateManualText(it) },
                    onAnalyse = { viewModel.analyseManualText() },
                    onBack = { viewModel.reset() }
                )
                VoicemailStage.ANALYSING -> AnalysingStage()
                VoicemailStage.RESULT -> ResultStage(
                    result = state.result,
                    error = state.error,
                    onShare = { viewModel.shareResult(context) },
                    onScanAnother = { viewModel.reset() }
                )
            }
        }
    }
}

@Composable
private fun MethodSelectStage(
    onListenLive: () -> Unit,
    onManualText: () -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "How would you like to check this voicemail?",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = TextPrimary
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Safe Companion will listen to the voicemail or read what you type, then tell you if it is a scam.",
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        color = TextSecondary
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Listen Live card
    Card(
        onClick = onListenLive,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = NavyBlue
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Listen Live",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Play the voicemail on speaker and Safe Companion will listen",
                    fontSize = 15.sp,
                    color = TextSecondary
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Manual text card
    Card(
        onClick = onManualText,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = WarningAmber
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Type What You Heard",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Type or paste what the voicemail said",
                    fontSize = 15.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ListenLiveStage(
    transcript: String,
    partialTranscript: String,
    isListening: Boolean,
    error: String?,
    onStartListening: () -> Unit,
    onStopAndAnalyse: () -> Unit,
    onBack: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = if (isListening) "Listening..." else "Ready to listen",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = if (isListening) SafeGreen else TextPrimary
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = if (isListening)
            "Play the voicemail on speaker now. Safe Companion is listening."
        else
            "Tap Start Listening, then play the voicemail on speaker.",
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        color = TextSecondary
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Transcript display
    if (transcript.isNotBlank() || partialTranscript.isNotBlank()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "What Safe Companion heard:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (transcript.isNotBlank()) {
                    Text(transcript, fontSize = 16.sp, color = TextPrimary)
                }
                if (partialTranscript.isNotBlank()) {
                    Text(
                        partialTranscript,
                        fontSize = 16.sp,
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Light
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    error?.let {
        Text(it, fontSize = 15.sp, color = ScamRed, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (!isListening) {
        Button(
            onClick = onStartListening,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Listening", fontSize = 18.sp)
        }
    } else {
        Button(
            onClick = onStopAndAnalyse,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ScamRed)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop & Analyse", fontSize = 18.sp)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    TextButton(onClick = onBack) {
        Text("Go back", fontSize = 16.sp, color = TextSecondary)
    }
}

@Composable
private fun ManualTextStage(
    text: String,
    error: String?,
    onTextChange: (String) -> Unit,
    onAnalyse: () -> Unit,
    onBack: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Type what the voicemail said",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Type or paste as much as you can remember. It does not need to be word for word.",
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        color = TextSecondary
    )

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        placeholder = {
            Text(
                "For example: \"This is the IRS calling about your tax return. You must call us back immediately at...\"",
                fontSize = 15.sp,
                color = TextSecondary.copy(alpha = 0.5f)
            )
        },
        shape = RoundedCornerShape(12.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
    )

    error?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(it, fontSize = 15.sp, color = ScamRed)
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onAnalyse,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
        enabled = text.isNotBlank()
    ) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Analyse This Voicemail", fontSize = 18.sp)
    }

    Spacer(modifier = Modifier.height(12.dp))

    TextButton(onClick = onBack) {
        Text("Go back", fontSize = 16.sp, color = TextSecondary)
    }
}

@Composable
private fun AnalysingStage() {
    Spacer(modifier = Modifier.height(80.dp))

    CircularProgressIndicator(
        modifier = Modifier.size(64.dp),
        color = NavyBlue,
        strokeWidth = 4.dp
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Analysing the voicemail...",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Safe Companion is checking this voicemail for scam patterns.",
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        color = TextSecondary
    )
}

@Composable
private fun ResultStage(
    result: com.safeharborsecurity.app.data.model.VoicemailScanResult?,
    error: String?,
    onShare: () -> Unit,
    onScanAnother: () -> Unit
) {
    if (error != null) {
        Spacer(modifier = Modifier.height(40.dp))

        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = WarningAmber
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = error,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onScanAnother,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
        ) {
            Text("Try Again", fontSize = 18.sp)
        }
        return
    }

    val r = result ?: return

    Spacer(modifier = Modifier.height(16.dp))

    // Verdict
    val verdictColor = when (r.verdict.uppercase()) {
        "SAFE" -> SafeGreen
        "SUSPICIOUS" -> WarningAmber
        "DANGEROUS" -> ScamRed
        else -> WarningAmber
    }
    val verdictEmoji = when (r.verdict.uppercase()) {
        "SAFE" -> "✅"
        "SUSPICIOUS" -> "⚠️"
        "DANGEROUS" -> "🔴"
        else -> "⚠️"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (r.verdict.uppercase()) {
                "SAFE" -> SafeGreenLight
                "SUSPICIOUS" -> WarningAmberLight
                "DANGEROUS" -> ScamRedLight
                else -> WarningAmberLight
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$verdictEmoji ${r.verdict.uppercase()}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = verdictColor
            )

            if (r.confidence > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${r.confidence}% confidence",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = r.summary,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = TextPrimary
            )
        }
    }

    // Explanation
    if (r.explanation.isNotBlank()) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Why?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(r.explanation, fontSize = 15.sp, color = TextSecondary)
            }
        }
    }

    // Red flags
    if (r.redFlags.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ScamRedLight)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Warning Signs",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScamRed
                )
                Spacer(modifier = Modifier.height(8.dp))
                r.redFlags.forEach { flag ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("• ", color = ScamRed, fontSize = 15.sp)
                        Text(flag, fontSize = 15.sp, color = TextPrimary)
                    }
                }
            }
        }
    }

    // Scam type
    if (r.scamType.isNotBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Scam type: ${r.scamType}",
            fontSize = 14.sp,
            color = TextSecondary
        )
    }

    // Recommended action
    if (r.recommendedAction.isNotBlank()) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "What to do",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyBlue
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(r.recommendedAction, fontSize = 15.sp, color = TextPrimary)
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Action buttons
    Button(
        onClick = onShare,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
    ) {
        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Share with Family", fontSize = 16.sp)
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = onScanAnother,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("Check Another Voicemail", fontSize = 16.sp)
    }

    Spacer(modifier = Modifier.height(24.dp))
}
