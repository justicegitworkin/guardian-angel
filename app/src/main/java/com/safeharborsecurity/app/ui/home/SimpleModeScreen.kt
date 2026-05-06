package com.safeharborsecurity.app.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.ui.components.Verdict
import com.safeharborsecurity.app.ui.components.VerdictIcon
import com.safeharborsecurity.app.ui.components.ViewModePill
import com.safeharborsecurity.app.ui.components.toVerdict
import com.safeharborsecurity.app.ui.theme.*

@Composable
fun SimpleModeScreen(
    userName: String,
    hasCheckedInToday: Boolean,
    recentAlerts: List<AlertEntity>,
    onNavigateToChat: () -> Unit,
    onNavigateToSafetyChecker: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToMessageDetail: (Long) -> Unit = {},
    onClearAllAlerts: () -> Unit = {},
    onCheckIn: () -> Unit,
    onSwitchToFullMode: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all alerts?") },
            text = {
                Text(
                    "This removes every alert from the Recent Alerts list. " +
                        "Your safety settings stay the same. New alerts will " +
                        "appear here as Safe Companion finds them."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearAllAlerts()
                    showClearConfirm = false
                }) { Text("Clear All", color = ScamRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            Surface(color = NavyBlue) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (userName.isNotBlank()) "Hello, $userName" else "Hello!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ViewModePill(
                        isSimpleMode = true,
                        onToggle = onSwitchToFullMode
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToSafetyChecker,
                containerColor = WarmGold,
                contentColor = TextOnGold,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Is This Safe?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large Chat button
            SimpleChatButton(onClick = onNavigateToChat)

            // Check-In card
            SimpleCheckInCard(
                hasCheckedIn = hasCheckedInToday,
                onCheckIn = onCheckIn
            )

            // Recent alerts (max 5)
            if (recentAlerts.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Recent Alerts",
                        style = MaterialTheme.typography.titleLarge,
                        color = NavyBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showClearConfirm = true }) {
                            Text(
                                "Clear All",
                                style = MaterialTheme.typography.bodyLarge,
                                color = ScamRed
                            )
                        }
                        TextButton(onClick = onNavigateToMessages) {
                            Text(
                                "See all",
                                style = MaterialTheme.typography.bodyLarge,
                                color = WarmGold
                            )
                        }
                    }
                }
                recentAlerts.take(5).forEach { alert ->
                    SimpleAlertRow(
                        alert = alert,
                        onClick = {
                            // Match the full home-screen routing — every alert
                            // type except CALL goes to the MessageDetail screen
                            // (the same place the notification opens).
                            when (alert.type) {
                                "SMS", "SCREEN_SMS", "EMAIL", "SOCIAL" ->
                                    onNavigateToMessageDetail(alert.id)
                                "CALL" -> onNavigateToCalls()
                                else -> onNavigateToMessageDetail(alert.id)
                            }
                        }
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SafeGreenLight),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("✅", fontSize = 36.sp)
                        Text(
                            "All clear! You are safe.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SafeGreen,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SimpleChatButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "simple_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "simple_pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pulse ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(PulseGreen.copy(alpha = 0.2f))
        )

        // Main button
        Box(
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape)
                .background(NavyBlue)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💬", fontSize = 56.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Chat with\nSafe Companion",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SimpleCheckInCard(hasCheckedIn: Boolean, onCheckIn: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasCheckedIn) SafeGreenLight else Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasCheckedIn) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (hasCheckedIn) {
                Text("✅", fontSize = 36.sp)
                Text(
                    "Checked in today",
                    style = MaterialTheme.typography.titleMedium,
                    color = SafeGreen,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text("✋", fontSize = 36.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Daily Check-In",
                        style = MaterialTheme.typography.titleMedium,
                        color = NavyBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Let your family know you're OK",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Button(
                    onClick = onCheckIn,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("I'm OK", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun SimpleAlertRow(alert: AlertEntity, onClick: () -> Unit) {
    val verdict = alert.riskLevel.toVerdict()
    val bgColor = when (verdict) {
        Verdict.DANGEROUS -> ScamRedLight
        Verdict.SUSPICIOUS -> WarningAmberLight
        Verdict.SAFE -> SafeGreenLight
        else -> SafeGreenLight
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VerdictIcon(verdict = verdict, size = 32.dp, showLabel = false)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    alert.sender,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    alert.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
