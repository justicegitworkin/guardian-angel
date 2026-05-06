package com.safeharborsecurity.app.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.safeharborsecurity.app.ui.theme.*
import com.safeharborsecurity.app.util.VoiceInputManager
import com.safeharborsecurity.app.util.VoiceState

@Composable
fun VoiceListeningOverlay(
    voiceManager: VoiceInputManager,
    onDismiss: () -> Unit,
    onTextRecognized: (String) -> Unit
) {
    val context = LocalContext.current
    val voiceResult by voiceManager.voiceResult.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceManager.startListening()
        } else {
            onDismiss()
        }
    }

    // Start listening when overlay opens
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            voiceManager.startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // When result is ready, pass it up
    LaunchedEffect(voiceResult.state) {
        if (voiceResult.state == VoiceState.RESULT && voiceResult.spokenText.isNotBlank()) {
            onTextRecognized(voiceResult.spokenText)
        }
    }

    Dialog(
        onDismissRequest = {
            voiceManager.stopListening()
            voiceManager.reset()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (voiceResult.state) {
                    VoiceState.LISTENING -> {
                        // Animated pulsing microphone
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = EaseInOut),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "micScale"
                        )

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .scale(scale)
                                .background(WarmGold.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Listening",
                                modifier = Modifier.size(48.dp),
                                tint = WarmGold
                            )
                        }

                        Text(
                            "Safe Companion is listening...",
                            style = MaterialTheme.typography.titleMedium,
                            color = NavyBlue,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        if (voiceResult.spokenText.isNotBlank()) {
                            Text(
                                "\"${voiceResult.spokenText}\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                "Speak clearly and I'll help you.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    VoiceState.PROCESSING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = NavyBlue
                        )
                        Text(
                            "Let me think about that...",
                            style = MaterialTheme.typography.titleMedium,
                            color = NavyBlue,
                            textAlign = TextAlign.Center
                        )
                    }

                    VoiceState.RESULT -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Got it",
                            modifier = Modifier.size(48.dp),
                            tint = SafeGreen
                        )
                        Text(
                            "I heard:",
                            style = MaterialTheme.typography.titleMedium,
                            color = NavyBlue,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "\"${voiceResult.spokenText}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    VoiceState.ERROR -> {
                        Icon(
                            Icons.Default.MicOff,
                            contentDescription = "Error",
                            modifier = Modifier.size(48.dp),
                            tint = WarningAmber
                        )
                        Text(
                            voiceResult.error ?: "Something went wrong.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { voiceManager.startListening() },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Try again", color = Color.White)
                        }
                    }

                    VoiceState.IDLE -> {
                        // Should not normally show
                    }
                }

                // Cancel button (always visible)
                TextButton(
                    onClick = {
                        voiceManager.stopListening()
                        voiceManager.reset()
                        onDismiss()
                    }
                ) {
                    Text("Cancel", color = TextSecondary, fontSize = 16.sp)
                }
            }
        }
    }
}
