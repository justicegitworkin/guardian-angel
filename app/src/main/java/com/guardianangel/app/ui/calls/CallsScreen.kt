package com.guardianangel.app.ui.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardianangel.app.data.local.entity.CallLogEntity
import com.guardianangel.app.util.formatTime
import com.guardianangel.app.ui.theme.*

@Composable
fun CallsScreen(
    onNavigateBack: () -> Unit,
    onOpenGuardian: (String) -> Unit,
    viewModel: CallsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCall by remember { mutableStateOf<CallLogEntity?>(null) }

    if (selectedCall != null) {
        CallDetailDialog(
            call = selectedCall!!,
            onDismiss = { selectedCall = null },
            onBlock = {
                viewModel.blockNumber(selectedCall!!.callerNumber)
                selectedCall = null
            },
            onAskGuardian = {
                val context = "I received a call from ${selectedCall!!.callerNumber}. " +
                    "Guardian's summary: ${selectedCall!!.summary}. What should I know?"
                selectedCall = null
                onOpenGuardian(context)
            }
        )
    }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            CallsTopBar(onBack = onNavigateBack)
        }
    ) { padding ->
        if (state.callLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📞", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No screened calls yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Guardian will show screened calls here",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.callLogs, key = { it.id }) { call ->
                    CallLogCard(
                        call = call,
                        onClick = { selectedCall = call },
                        onBlock = { viewModel.blockNumber(call.callerNumber) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallsTopBar(onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        },
        title = {
            Text("📞 Call History", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
    )
}

@Composable
private fun CallLogCard(
    call: CallLogEntity,
    onClick: () -> Unit,
    onBlock: () -> Unit
) {
    val (badgeColor, badgeText) = when (call.riskLevel) {
        "SCAM" -> Pair(ScamRed, "SCAM")
        "SUSPICIOUS" -> Pair(WarningAmber, "SUSPICIOUS")
        "SAFE" -> Pair(SafeGreen, "SAFE")
        else -> Pair(TextSecondary, "UNKNOWN")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when (call.riskLevel) {
                "SCAM" -> ScamRedLight
                "SUSPICIOUS" -> WarningAmberLight
                "SAFE" -> SafeGreenLight
                else -> LightSurface
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (call.isBlocked) Icons.Default.Block else Icons.Default.Phone,
                        contentDescription = null,
                        tint = if (call.isBlocked) ScamRed else NavyBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        call.callerNumber,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                Surface(color = badgeColor, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        badgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (call.summary.isNotBlank()) {
                Text(
                    call.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDuration(call.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    formatTime(call.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            if (!call.isBlocked) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ScamRed)
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Block this number", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CallDetailDialog(
    call: CallLogEntity,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
    onAskGuardian: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WarmWhite,
        title = {
            Text(
                "Call from ${call.callerNumber}",
                style = MaterialTheme.typography.headlineSmall,
                color = NavyBlue
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (call.summary.isNotBlank()) {
                    Text("Guardian's Summary:", style = MaterialTheme.typography.titleSmall, color = NavyBlue)
                    Text(call.summary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
                if (call.transcript.isNotBlank()) {
                    HorizontalDivider()
                    Text("Transcript:", style = MaterialTheme.typography.titleSmall, color = NavyBlue)
                    Text(call.transcript, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Text(
                    "Duration: ${formatDuration(call.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAskGuardian,
                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                modifier = Modifier.height(52.dp)
            ) {
                Text("Ask Guardian 😇", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onBlock,
                modifier = Modifier.height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ScamRed)
            ) {
                Text("Block Number", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

private fun formatDuration(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val m = seconds / 60
    val s = seconds % 60
    return "${m}m ${s}s"
}
