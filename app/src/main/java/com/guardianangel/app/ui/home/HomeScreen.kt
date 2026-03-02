package com.guardianangel.app.ui.home

import android.app.role.RoleManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardianangel.app.data.local.entity.AlertEntity
import com.guardianangel.app.ui.theme.*
import com.guardianangel.app.util.formatTime

@Composable
fun HomeScreen(
    onNavigateToGuardian: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isCallScreeningActive by remember { mutableStateOf(true) } // assume active until checked

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            isCallScreeningActive = rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            isCallScreeningActive = rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
    }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            HomeTopBar(
                userName = state.userName,
                onSettingsClick = onNavigateToSettings
            )
        },
        bottomBar = {
            HomeBottomBar(
                onCalls = onNavigateToCalls,
                onMessages = onNavigateToMessages,
                onSettings = onNavigateToSettings
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // Big Guardian button
            item {
                GuardianCallButton(
                    allShieldsOn = state.allShieldsOn,
                    onClick = onNavigateToGuardian
                )
            }

            // Call screening warning banner
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isCallScreeningActive) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WarningAmberLight),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("⚠️", fontSize = 24.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Call screening not set up",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = WarningAmber
                                )
                                Text(
                                    "Calls are not being screened for scams",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val rm = context.getSystemService(RoleManager::class.java)
                                    roleRequestLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber)
                            ) {
                                Text("Fix This", style = MaterialTheme.typography.labelLarge, color = WarningAmber)
                            }
                        }
                    }
                }
            }

            // Shield status cards
            item {
                Text(
                    "Protection Shields",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NavyBlue
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShieldCard(
                        modifier = Modifier.weight(1f),
                        label = "Call",
                        emoji = "📞",
                        isOn = state.isCallShieldOn,
                        onToggle = { viewModel.setCallShield(it) }
                    )
                    ShieldCard(
                        modifier = Modifier.weight(1f),
                        label = "SMS",
                        emoji = "💬",
                        isOn = state.isSmsShieldOn,
                        onToggle = { viewModel.setSmsShield(it) }
                    )
                    ShieldCard(
                        modifier = Modifier.weight(1f),
                        label = "Email",
                        emoji = "📧",
                        isOn = state.isEmailShieldOn,
                        onToggle = { viewModel.setEmailShield(it) }
                    )
                }
            }

            // Recent alerts header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Alerts",
                        style = MaterialTheme.typography.headlineSmall,
                        color = NavyBlue
                    )
                    TextButton(onClick = onNavigateToMessages) {
                        Text("See all", style = MaterialTheme.typography.bodyLarge, color = WarmGold)
                    }
                }
            }

            if (state.recentAlerts.isEmpty()) {
                item { AllClearCard() }
            } else {
                items(state.recentAlerts, key = { it.id }) { alert ->
                    AlertCard(
                        alert = alert,
                        onClick = {
                            viewModel.markAlertRead(alert.id)
                            if (alert.type == "SMS") onNavigateToMessages() else onNavigateToCalls()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(userName: String, onSettingsClick: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
        title = {
            Column {
                Text(
                    text = if (userName.isNotBlank()) "Hello, $userName 👋" else "Hello! 👋",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "Guardian Angel is watching over you",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmGoldLight
                )
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    )
}

@Composable
private fun GuardianCallButton(allShieldsOn: Boolean, onClick: () -> Unit) {
    val pulseColor = if (allShieldsOn) PulseGreen else PulseAmber

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pulse ring
        Box(
            modifier = Modifier
                .size((140 * 1.3).dp)
                .scale(scale)
                .clip(CircleShape)
                .background(pulseColor.copy(alpha = 0.2f))
        )

        // Main button
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(NavyBlue)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Talk to Guardian Angel",
                    tint = WarmGold,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Talk to\nGuardian",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ShieldCard(
    modifier: Modifier = Modifier,
    label: String,
    emoji: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) NavyBlue else LightSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(emoji, fontSize = 24.sp)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isOn) Color.White else TextSecondary
            )
            Switch(
                checked = isOn,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = WarmGold,
                    checkedTrackColor = NavyBlueLight,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = LightSurface
                )
            )
        }
    }
}

@Composable
private fun AllClearCard() {
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
            Text("🛡️", fontSize = 36.sp)
            Text(
                text = "All clear! Guardian Angel is watching over you",
                style = MaterialTheme.typography.bodyLarge,
                color = SafeGreen
            )
        }
    }
}

@Composable
fun AlertCard(alert: AlertEntity, onClick: () -> Unit) {
    val (badgeColor, badgeText) = when (alert.riskLevel) {
        "SCAM" -> Pair(ScamRed, "SCAM")
        "WARNING" -> Pair(WarningAmber, "WARNING")
        else -> Pair(SafeGreen, "SAFE")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when (alert.riskLevel) {
                "SCAM" -> ScamRedLight
                "WARNING" -> WarningAmberLight
                else -> SafeGreenLight
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
                    Text(
                        if (alert.type == "SMS") "💬" else "📞",
                        fontSize = 20.sp
                    )
                    Text(
                        alert.sender,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        badgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                alert.content,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                alert.reason,
                style = MaterialTheme.typography.bodySmall,
                color = badgeColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                formatTime(alert.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun HomeBottomBar(
    onCalls: () -> Unit,
    onMessages: () -> Unit,
    onSettings: () -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 4.dp
    ) {
        NavigationBarItem(
            selected = false,
            onClick = onCalls,
            icon = { Icon(Icons.Default.Phone, contentDescription = "Calls") },
            label = { Text("Calls", style = MaterialTheme.typography.labelMedium) }
        )
        NavigationBarItem(
            selected = false,
            onClick = onMessages,
            icon = { Icon(Icons.Default.Message, contentDescription = "Messages") },
            label = { Text("Messages", style = MaterialTheme.typography.labelMedium) }
        )
        NavigationBarItem(
            selected = false,
            onClick = onSettings,
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings", style = MaterialTheme.typography.labelMedium) }
        )
    }
}

