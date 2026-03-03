package com.guardianangel.app.ui.home

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardianangel.app.ui.theme.*
import com.guardianangel.app.util.formatTime
import kotlin.math.sqrt

// ── Shake detector ────────────────────────────────────────────────────────────

private const val SHAKE_THRESHOLD = 14f   // m/s² net over gravity
private const val SHAKE_DEBOUNCE_MS = 2_000L

@Composable
private fun ShakeDetector(onShake: () -> Unit) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onShake)
    var lastShakeTime by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return@DisposableEffect onDispose {}

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                val net = sqrt((x * x + y * y + z * z).toDouble()).toFloat() - SensorManager.GRAVITY_EARTH
                if (net > SHAKE_THRESHOLD) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > SHAKE_DEBOUNCE_MS) {
                        lastShakeTime = now
                        callback()
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }
}

// ── Main screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToGuardian: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val intelState by viewModel.intelState.collectAsStateWithLifecycle()
    val shakeIntroShown by viewModel.shakeIntroShown.collectAsStateWithLifecycle()

    var showStatusDialog by remember { mutableStateOf(false) }
    var showFeaturesSheet by remember { mutableStateOf(false) }
    var showSecurityConfirm by remember { mutableStateOf(false) }
    var showNoContactsDialog by remember { mutableStateOf(false) }

    // Shake → open Guardian chat
    ShakeDetector(onShake = onNavigateToGuardian)

    // Auto-dismiss security confirmation after 2 s
    LaunchedEffect(showSecurityConfirm) {
        if (showSecurityConfirm) {
            kotlinx.coroutines.delay(2_000)
            showSecurityConfirm = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBlue)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── 1. Top bar ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Guardian Angel",
                    color = WarmGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = WarmGold,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // ── 2. Status badge ────────────────────────────────────────────
            StatusBadge(intelState = intelState, onClick = { showStatusDialog = true })

            Spacer(modifier = Modifier.weight(1f))

            // ── 3. Push-to-talk button ─────────────────────────────────────
            PushToTalkButton(onClick = onNavigateToGuardian)

            Spacer(modifier = Modifier.weight(0.6f))

            // ── 4. Two supporting buttons ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SupportingButton(
                    modifier = Modifier.weight(1f),
                    icon = if (uiState.allShieldsOn) Icons.Default.Shield else Icons.Default.Warning,
                    label = if (uiState.allShieldsOn) "Security is ON" else "Turn On Security",
                    badgeDot = !uiState.allShieldsOn,
                    confirmed = showSecurityConfirm,
                    onClick = {
                        if (!uiState.allShieldsOn) {
                            viewModel.enableAllShields()
                            showSecurityConfirm = true
                        }
                    }
                )

                SupportingButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Phone,
                    label = "Call a Friend",
                    badgeDot = false,
                    confirmed = false,
                    dialNumber = uiState.familyContacts.firstOrNull()?.number,
                    onClick = {
                        if (uiState.familyContacts.isEmpty()) {
                            showNoContactsDialog = true
                        } else {
                            viewModel.recordCallFriendTap()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.weight(0.4f))

            // ── 5. "See all features" link ─────────────────────────────────
            TextButton(
                onClick = { showFeaturesSheet = true },
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = "See all features →",
                    color = WarmGold.copy(alpha = 0.85f),
                    fontSize = 18.sp
                )
            }
        }

        // ── Dialogs & sheets ───────────────────────────────────────────────

        if (showStatusDialog) {
            StatusInfoDialog(intelState = intelState, onDismiss = { showStatusDialog = false })
        }

        if (showNoContactsDialog) {
            AlertDialog(
                onDismissRequest = { showNoContactsDialog = false },
                title = { Text("No Contacts Added", fontSize = 20.sp) },
                text = {
                    Text(
                        "Add a trusted family member or friend in Settings so Guardian can call them for you.",
                        fontSize = 17.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showNoContactsDialog = false; onNavigateToSettings() }) {
                        Text("Open Settings", fontSize = 17.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNoContactsDialog = false }) {
                        Text("Not now", fontSize = 17.sp)
                    }
                },
                containerColor = Color(0xFF1E4A72),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.9f)
            )
        }

        if (showFeaturesSheet) {
            FeaturesBottomSheet(
                onDismiss = { showFeaturesSheet = false },
                onSmsShield = { showFeaturesSheet = false; onNavigateToMessages() },
                onCallShield = { showFeaturesSheet = false; onNavigateToCalls() },
                onSettings = { showFeaturesSheet = false; onNavigateToSettings() }
            )
        }

        // One-time shake intro dialog
        if (!shakeIntroShown) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissShakeIntro() },
                icon = {
                    Text("📳", fontSize = 36.sp)
                },
                title = {
                    Text("New! Shake to Activate", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "Shake your phone anytime to instantly reach Guardian Angel, even when the app is closed. You can turn this off in Settings.",
                        fontSize = 17.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissShakeIntro(turnOff = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = WarmGold)
                    ) {
                        Text("Got it", fontSize = 17.sp, color = Color(0xFF1A1A1A))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissShakeIntro(turnOff = true) }) {
                        Text("Turn off", fontSize = 17.sp)
                    }
                },
                containerColor = Color(0xFF1E4A72),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

// ── Status badge ──────────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(intelState: IntelState, onClick: () -> Unit) {
    val hasSynced = intelState.lastSyncMs > 0L
    val threatCount = intelState.threats.size

    val label = when {
        !hasSynced -> "🛡️ Protected — local rules active"
        threatCount > 0 -> "🛡️ Protected — $threatCount active alerts"
        else -> {
            val agoHours = ((System.currentTimeMillis() - intelState.lastSyncMs) / 3_600_000).toInt()
            val agoText = when {
                agoHours < 1 -> "just now"
                agoHours == 1 -> "1 hour ago"
                else -> "$agoHours hours ago"
            }
            "🛡️ Protected — database updated $agoText"
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color(0xFF0F2540),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

// ── Push-to-talk button ───────────────────────────────────────────────────────

@Composable
private fun PushToTalkButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Outer pulse ring
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(WarmGold.copy(alpha = pulseAlpha))
            )
            // Main button
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = WarmGold,
                shadowElevation = 12.dp,
                modifier = Modifier.size(164.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Talk to Guardian Angel",
                        tint = Color.White,
                        modifier = Modifier.size(68.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Tap to talk to Guardian Angel",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "or shake your phone",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ── Supporting button ─────────────────────────────────────────────────────────

@Composable
private fun SupportingButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    badgeDot: Boolean,
    confirmed: Boolean,
    dialNumber: String? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        onClick = {
            if (dialNumber != null) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNumber")))
                onClick()
            } else {
                onClick()
            }
        },
        shape = RoundedCornerShape(20.dp),
        color = NavyBlueLight,
        border = BorderStroke(
            width = if (confirmed) 2.dp else 1.5.dp,
            color = if (confirmed) SafeGreen else WarmGold.copy(alpha = 0.6f)
        ),
        modifier = modifier.height(100.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (badgeDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(WarningAmber)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (confirmed) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (confirmed) SafeGreen else WarmGold,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (confirmed) "Security is ON ✓" else label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

// ── Status info dialog ────────────────────────────────────────────────────────

@Composable
private fun StatusInfoDialog(intelState: IntelState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Shield, null, tint = WarmGold, modifier = Modifier.size(36.dp))
        },
        title = { Text("About Your Protection", fontSize = 21.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Guardian Angel checks every text message and phone call for scam patterns before they reach you.",
                    fontSize = 17.sp
                )
                if (intelState.lastSyncMs > 0L) {
                    Text(
                        "Your scam database was last updated ${formatTime(intelState.lastSyncMs)} and contains ${intelState.threats.size} active threat alerts from FBI, FTC, and CISA.",
                        fontSize = 17.sp
                    )
                } else {
                    Text(
                        "Your device uses built-in scam rules. Add a server URL in Settings to receive live updates.",
                        fontSize = 17.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it", fontSize = 17.sp) }
        },
        containerColor = Color(0xFF1E4A72),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.9f),
        iconContentColor = WarmGold
    )
}

// ── Features bottom sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeaturesBottomSheet(
    onDismiss: () -> Unit,
    onSmsShield: () -> Unit,
    onCallShield: () -> Unit,
    onSettings: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F2540),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "All Features",
                color = WarmGold, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FeatureRow(Icons.Default.Message, "SMS Shield",
                "Guardian reads your texts and warns you about scams", onSmsShield)
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            FeatureRow(Icons.Default.Phone, "Call Shield",
                "Guardian screens suspicious calls before they reach you", onCallShield)
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            FeatureRow(Icons.Default.Notifications, "Scam Alerts",
                "View all recent warnings and blocked threats", onSmsShield)
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            FeatureRow(Icons.Default.Settings, "Settings",
                "Family contacts, privacy, wake word, and more", onSettings)
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector, title: String, description: String, onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, tint = WarmGold, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f))
        }
    }
}
