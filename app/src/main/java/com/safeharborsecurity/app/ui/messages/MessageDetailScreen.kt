package com.safeharborsecurity.app.ui.messages

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.ui.components.VerdictIcon
import com.safeharborsecurity.app.ui.components.toVerdict
import com.safeharborsecurity.app.ui.theme.*
import com.safeharborsecurity.app.util.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    alertId: Long,
    onNavigateBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: MessageDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Navigate back after block/delete/markSafe
    LaunchedEffect(state.isBlocked) {
        if (state.isBlocked) {
            Toast.makeText(context, "Sender blocked", Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
    }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) {
            Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
    }
    LaunchedEffect(state.isMarkedSafe) {
        if (state.isMarkedSafe) {
            Toast.makeText(context, "Marked as safe", Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
    }

    var showBlockDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
                title = { Text("Message Details", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = WarmGold)
            }
            return@Scaffold
        }

        val alert = state.alert
        if (alert == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Message not found.", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Verdict header
            VerdictHeader(alert)

            // Message content
            MessageContentCard(alert)

            // Reason / analysis
            if (alert.reason.isNotBlank()) {
                AnalysisCard(title = "Why this looks suspicious", content = alert.reason)
            }

            // Recommended action
            if (alert.action.isNotBlank()) {
                AnalysisCard(title = "What you should do", content = alert.action)
            }

            // Action buttons
            Spacer(Modifier.height(8.dp))

            // Honest about capabilities — Android doesn't let third-party
            // apps add numbers to the system block list or delete messages
            // from the user's Messaging app. So instead of buttons that
            // pretend to do those things, we open a how-to dialog with the
            // exact steps for the user's own apps. See showBlockDialog and
            // showDeleteDialog further down.
            Button(
                onClick = { showBlockDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ScamRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Block, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("How to Block This Sender", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = ScamRed)
                Spacer(Modifier.width(8.dp))
                Text("How to Delete This Message", color = ScamRed)
            }

            // Tell my family
            Button(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT,
                            "Safe Companion Alert: I received a suspicious ${alert.type.lowercase()} from ${alert.sender}.\n\n" +
                            "Reason: ${alert.reason}\n\nAdvice: ${alert.action}")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Tell My Family"))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Tell My Family", color = Color.White)
            }

            // Ask Safe Companion
            OutlinedButton(
                onClick = {
                    onOpenChat("I got a suspicious ${alert.type.lowercase()} from ${alert.sender}: \"${alert.content.take(200)}\". ${alert.reason}. What should I do?")
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Ask Safe Companion About This", color = WarmGold)
            }

            // Mark as safe
            if (alert.riskLevel != "SAFE") {
                TextButton(
                    onClick = { viewModel.markAsSafe() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mark as Safe", color = TextSecondary)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Block guide dialog. Android doesn't let third-party apps add numbers
    // to the system-wide block list, so instead we tell the user exactly
    // how to do it themselves and offer to copy the number to the clipboard
    // so they don't have to type it.
    if (showBlockDialog) {
        val sender = state.alert?.sender.orEmpty()
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            containerColor = LightSurface,
            title = { Text("How to block this sender", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Safe Companion can't block numbers for you — Android only " +
                            "allows the Phone or Messages app to do that. Here's how " +
                            "to do it yourself in just a few taps:",
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("For text messages:", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Open Messages → press and hold this conversation → " +
                            "tap Block & report spam.",
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("For phone calls:", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Open Phone → Recents → tap the number → tap Block / report spam.",
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Sender: $sender",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Copy to clipboard so the user can paste into the
                        // block form in their Messages/Phone app.
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Sender", sender))
                        android.widget.Toast.makeText(
                            context, "Sender copied — paste it in your Messages or Phone app.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        // Also remember locally so Safe Companion's own
                        // call-screening service can decline future calls
                        // from this number if it's enabled.
                        viewModel.blockSender()
                        showBlockDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                ) { Text("Copy sender to clipboard", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBlockDialog = false }) { Text("Close") }
            }
        )
    }

    // Delete guide dialog — same shape. We can't delete the original from
    // the user's Messages or Email app, but we explain how and offer to
    // remove it from Safe Companion's own history.
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = LightSurface,
            title = { Text("How to delete this message", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Safe Companion can't delete messages from your Messages " +
                            "or Email app — Android only allows those apps themselves " +
                            "to do that. To remove the original message, open the " +
                            "app it came from, find this conversation, and delete " +
                            "it the way you normally would.",
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "If you want to clear the alert from Safe Companion's own " +
                            "history (just here, not the original), tap Remove from " +
                            "Safe Companion below.",
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; viewModel.deleteMessage() },
                    colors = ButtonDefaults.buttonColors(containerColor = ScamRed)
                ) { Text("Remove from Safe Companion", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun VerdictHeader(alert: AlertEntity) {
    val verdict = alert.riskLevel.toVerdict()
    val (bgColor, textColor, label) = when (alert.riskLevel) {
        "SCAM", "DANGEROUS" -> Triple(ScamRedLight, ScamRed, "Likely Scam")
        "WARNING", "SUSPICIOUS" -> Triple(WarningAmberLight, WarningAmber, "Be Careful")
        "SAFE" -> Triple(SafeGreenLight, SafeGreen, "Looks Safe")
        else -> Triple(LightSurface, TextSecondary, alert.riskLevel)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VerdictIcon(verdict = verdict, size = 48.dp, showLabel = false)
            Column {
                Text(label, style = MaterialTheme.typography.headlineSmall, color = textColor, fontWeight = FontWeight.Bold)
                Text(
                    "${alert.type} from ${alert.sender}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun MessageContentCard(alert: AlertEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("From: ${alert.sender}", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(formatTime(alert.timestamp), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }

            HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))

            Text(
                alert.content,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun AnalysisCard(title: String, content: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = WarmGold, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, lineHeight = 22.sp)
        }
    }
}
