package com.safeharborsecurity.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.data.local.entity.NewsArticleEntity
import com.safeharborsecurity.app.ui.components.Verdict
import com.safeharborsecurity.app.ui.components.VerdictIcon
import com.safeharborsecurity.app.ui.components.ViewModePill
import com.safeharborsecurity.app.ui.components.toVerdict
import com.safeharborsecurity.app.data.repository.WifiStatus
import com.safeharborsecurity.app.ui.theme.*
import com.safeharborsecurity.app.util.formatTime

@Composable
fun HomeScreen(
    onNavigateToSafeHarbor: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToSafetyChecker: () -> Unit = {},
    onNavigateToPanic: () -> Unit = {},
    onNavigateToWifiDetail: () -> Unit = {},
    onNavigateToNews: () -> Unit = {},
    onNavigateToPoints: () -> Unit = {},
    onNavigateToAppChecker: () -> Unit = {},
    onNavigateToWeeklyDigest: () -> Unit = {},
    onNavigateToMessageDetail: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearAllConfirm by remember { mutableStateOf(false) }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
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
                    viewModel.clearAllAlerts()
                    showClearAllConfirm = false
                }) {
                    Text("Clear All", color = ScamRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") }
            }
        )
    }
    val wifiStatus by viewModel.wifiStatus.collectAsStateWithLifecycle()
    val newsArticles by viewModel.newsArticles.collectAsStateWithLifecycle()

    // Screen-monitor re-enable launcher. The original consent token from
    // onboarding is single-use and dies with its foreground service when the
    // user closes the app from Recents, so we keep this here so they can
    // re-grant in one tap from the home banner without going back through
    // onboarding.
    val homeContext = LocalContext.current
    val screenMonitorReEnableLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            homeContext.getSharedPreferences("safeharbor_runtime", 0)
                .edit().putBoolean("screen_monitor_active", true).apply()
            com.safeharborsecurity.app.service.ScreenScanService.startWithProjection(
                homeContext, result.resultCode, result.data!!
            )
        }
    }

    if (state.isSimpleMode) {
        SimpleModeScreen(
            userName = state.userName,
            hasCheckedInToday = state.hasCheckedInToday,
            recentAlerts = state.recentAlerts,
            onNavigateToChat = onNavigateToSafeHarbor,
            onNavigateToSafetyChecker = onNavigateToSafetyChecker,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToMessages = onNavigateToMessages,
            onNavigateToCalls = onNavigateToCalls,
            onNavigateToMessageDetail = onNavigateToMessageDetail,
            onClearAllAlerts = { viewModel.clearAllAlerts() },
            onCheckIn = { viewModel.checkIn() },
            onSwitchToFullMode = { viewModel.toggleSimpleMode() }
        )
        return
    }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            HomeTopBar(
                userName = state.userName,
                onSettingsClick = onNavigateToSettings,
                onSimpleModeToggle = { viewModel.toggleSimpleMode() }
            )
        },
        bottomBar = {
            HomeBottomBar(
                onCalls = onNavigateToCalls,
                onMessages = onNavigateToMessages,
                onSafetyChecker = onNavigateToSafetyChecker
            )
        }
        // FAB removed — Is This Safe? lives in the bottom nav now (per
        // tester feedback item 3). Settings remains in the top bar gear.
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // Big Safe Companion button
            item {
                SafeHarborCallButton(
                    allShieldsOn = state.allShieldsOn,
                    onClick = onNavigateToSafeHarbor
                )
            }

            // Quick Call Family button
            item {
                QuickCallFamilyButton(
                    familyContactName = state.primaryFamilyContactName,
                    familyContactPhone = state.primaryFamilyContactPhone,
                    onNoContactSetup = onNavigateToSettings
                )
            }

            // Safety Points card
            item {
                com.safeharborsecurity.app.ui.points.PointsCard(
                    onNavigateToPoints = onNavigateToPoints
                )
            }

            // Daily Safety Tip
            if (state.dailyTip.isNotBlank()) {
                item {
                    DailyTipCard(
                        tipText = state.dailyTip,
                        onDismiss = { viewModel.dismissDailyTip() },
                        onHelpful = { viewModel.rateTip(it) }
                    )
                }
            }

            // Screen Monitor health banner. Only renders when the user has
            // opted in (their original consent + service start succeeded at
            // some point) but the live status flow says the service isn't
            // running right now — typically because they closed the app from
            // Recents and the MediaProjection token died with the process.
            // One tap re-grants consent and resumes scanning.
            if (state.screenMonitorNeedsReactivation) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val mpm = homeContext.getSystemService(
                                        Context.MEDIA_PROJECTION_SERVICE
                                    ) as android.media.projection.MediaProjectionManager
                                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                        val cfg = android.media.projection.MediaProjectionConfig
                                            .createConfigForDefaultDisplay()
                                        mpm.createScreenCaptureIntent(cfg)
                                    } else mpm.createScreenCaptureIntent()
                                    screenMonitorReEnableLauncher.launch(intent)
                                } catch (_: Exception) {}
                            },
                        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.18f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, WarningAmber)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = WarningAmber
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Screen monitor stopped",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    "Safe Companion isn't currently watching for scams. " +
                                        "Tap to turn it back on. (Closing the app from " +
                                        "your recent apps will stop the monitor.)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Re-enable",
                                tint = WarningAmber
                            )
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
                        label = "Messages",
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

            // Notification listener banner removed — Item 2 replaced
            // NotificationListener-based SMS scanning with the screen-monitor
            // pipeline (MediaProjection + on-device OCR), so this banner was
            // pointing users at a permission they no longer need. The
            // home-screen scam-detection status is now reflected by the
            // shield cards directly.

            item {
                ListeningShieldCard(
                    isOn = state.isListeningShieldOn,
                    onToggle = { viewModel.setListeningShield(it) },
                    onTap = onNavigateToPrivacy
                )
            }

            // WiFi Security status
            item {
                WifiStatusCard(
                    wifiStatus = wifiStatus,
                    onClick = onNavigateToWifiDetail
                )
            }

            // Weekly Digest card (Item 4 option a). Appears Monday morning
            // when the worker has produced a fresh digest the user hasn't
            // dismissed yet.
            if (state.hasFreshWeeklyDigest) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToWeeklyDigest() },
                        colors = CardDefaults.cardColors(containerColor = NavyBlue.copy(alpha = 0.10f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, NavyBlue.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📊", fontSize = 32.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Your weekly safety report is ready",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NavyBlue
                                )
                                Text(
                                    "See what Safe Companion did this week. Tap to read.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = NavyBlue
                            )
                        }
                    }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.recentAlerts.isNotEmpty()) {
                            TextButton(onClick = { showClearAllConfirm = true }) {
                                Text("Clear All", style = MaterialTheme.typography.bodyLarge, color = ScamRed)
                            }
                        }
                        TextButton(onClick = onNavigateToMessages) {
                            Text("See all", style = MaterialTheme.typography.bodyLarge, color = WarmGold)
                        }
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
                            // Route to the same MessageDetail screen the
                            // notifications open (so the user sees the full
                            // alert with action buttons), instead of the
                            // category-list screens. Calls still go to the
                            // Calls list since there's no equivalent detail
                            // screen for them.
                            when (alert.type) {
                                "SMS", "SCREEN_SMS", "EMAIL", "SOCIAL" ->
                                    onNavigateToMessageDetail(alert.id)
                                "CALL" -> onNavigateToCalls()
                                else -> onNavigateToMessageDetail(alert.id)
                            }
                        }
                    )
                }
            }

            // Daily Check-In
            item {
                CheckInCard(
                    hasCheckedIn = state.hasCheckedInToday,
                    onCheckIn = { viewModel.checkIn() }
                )
            }

            // Security News
            item {
                val context = LocalContext.current
                com.safeharborsecurity.app.ui.news.NewsSection(
                    articles = newsArticles,
                    onArticleClick = { article ->
                        viewModel.markNewsRead(article.id)
                        try {
                            CustomTabsIntent.Builder().setShowTitle(true).build()
                                .launchUrl(context, Uri.parse(article.link))
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
                            } catch (_: Exception) { }
                        }
                    },
                    onSeeAll = onNavigateToNews,
                    onClearAll = { viewModel.clearAllNews() },
                    getSourceColor = { viewModel.getNewsSourceColor(it) }
                )
            }

            // Need Help (Panic) button
            item {
                TextButton(
                    onClick = onNavigateToPanic,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Need Help?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = WarningAmber
                    )
                }
            }

            // Beta feedback — opens a Google Form (configured at build time
            // via local.properties) in a Chrome Custom Tab, with app/device
            // info prefilled in the URL. Hidden when no form URL is baked,
            // so non-beta builds don't show a half-broken button.
            if (com.safeharborsecurity.app.BuildConfig.FEEDBACK_FORM_URL.isNotBlank()) {
                item {
                    val ctx = LocalContext.current
                    // Tester feedback (item 8): the old TextButton looked
                    // like plain text. Switched to OutlinedButton with a
                    // 1.5dp navy border so it visibly reads as a button.
                    OutlinedButton(
                        onClick = {
                            val url = com.safeharborsecurity.app.util.FeedbackFormUrlBuilder
                                .build(com.safeharborsecurity.app.BuildConfig.FEEDBACK_FORM_URL)
                            try {
                                CustomTabsIntent.Builder()
                                    .setShowTitle(true)
                                    .build()
                                    .launchUrl(ctx, Uri.parse(url))
                            } catch (_: Exception) {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(
                                        ctx,
                                        "Couldn't open the feedback form. Check your internet connection.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, NavyBlue)
                    ) {
                        Icon(
                            Icons.Default.RateReview,
                            contentDescription = null,
                            tint = NavyBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Give feedback about Safe Companion",
                            style = MaterialTheme.typography.bodyLarge,
                            color = NavyBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Beta diagnostics — small "Help us improve" entry. Tapping shows a
            // brief "what's in the logs" dialog, then opens an email with the
            // redacted log file attached. AARP testers don't have to know what
            // logcat is to send useful information.
            item {
                var showLogConfirm by remember { mutableStateOf(false) }
                if (showLogConfirm) {
                    val ctx = LocalContext.current
                    AlertDialog(
                        onDismissRequest = { showLogConfirm = false },
                        title = { Text("Help us fix bugs") },
                        text = {
                            Text(
                                "Safe Companion will gather a small file of " +
                                    "technical information from your phone " +
                                    "(no messages, contacts, or phone numbers) " +
                                    "and open your email so you can send it to us. " +
                                    "You'll see exactly what's being sent before " +
                                    "you tap Send."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showLogConfirm = false
                                com.safeharborsecurity.app.util.LogCollector.shareLogs(ctx)
                            }) { Text("Continue") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLogConfirm = false }) { Text("Cancel") }
                        }
                    )
                }
                TextButton(
                    onClick = { showLogConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Send diagnostic logs to the Safe Companion team",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

@Composable
private fun CheckInCard(hasCheckedIn: Boolean, onCheckIn: () -> Unit) {
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
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasCheckedIn) {
                Text("✅", fontSize = 32.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Checked in today",
                        style = MaterialTheme.typography.titleSmall,
                        color = SafeGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Your family knows you're safe",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            } else {
                Text("✋", fontSize = 32.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Daily Check-In",
                        style = MaterialTheme.typography.titleSmall,
                        color = NavyBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Let your family know you're OK today",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Button(
                    onClick = onCheckIn,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("I'm OK", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    userName: String,
    onSettingsClick: () -> Unit,
    onSimpleModeToggle: () -> Unit = {}
) {
    Surface(color = NavyBlue) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Greeting — completely inert, no click modifiers
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (userName.isNotBlank()) "Hello, $userName 👋" else "Hello! 👋",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "Safe Companion is watching over you",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmGoldLight
                )
            }
            ViewModePill(
                isSimpleMode = false,
                onToggle = onSimpleModeToggle
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }
}

@Composable
private fun SafeHarborCallButton(allShieldsOn: Boolean, onClick: () -> Unit) {
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
                Text("💬", fontSize = 40.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Chat with\nSafe Companion",
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
private fun ListeningShieldCard(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    onTap: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) NavyBlue else LightSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🎧", fontSize = 24.sp)
                Column {
                    Text(
                        "Listening Shield",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isOn) Color.White else TextSecondary
                    )
                    Text(
                        if (isOn) "Monitoring for listening apps" else "Tap to learn more",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOn) WarmGoldLight else TextSecondary.copy(alpha = 0.7f)
                    )
                }
            }
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
                text = "All clear! Safe Companion is watching over you",
                style = MaterialTheme.typography.bodyLarge,
                color = SafeGreen
            )
        }
    }
}

@Composable
fun AlertCard(alert: AlertEntity, onClick: () -> Unit) {
    val verdict = alert.riskLevel.toVerdict()
    val bgColor = when (verdict) {
        Verdict.DANGEROUS -> ScamRedLight
        Verdict.SUSPICIOUS -> WarningAmberLight
        Verdict.SAFE -> SafeGreenLight
        else -> SafeGreenLight
    }
    val reasonColor = when (verdict) {
        Verdict.DANGEROUS -> ScamRed
        Verdict.SUSPICIOUS -> WarningAmber
        Verdict.SAFE -> SafeGreen
        else -> TextSecondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
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
                        when (alert.type) {
                            "SMS", "SCREEN_SMS", "SOCIAL" -> "💬"
                            "EMAIL" -> "📧"
                            "CALL" -> "📞"
                            "APP" -> "📱"
                            else -> "⚠️"  // unknown type — neutral warning, not phone
                        },
                        fontSize = 20.sp
                    )
                    Text(
                        alert.sender,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                VerdictIcon(verdict = verdict, size = 28.dp, showLabel = false)
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
                color = reasonColor,
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
    onSafetyChecker: () -> Unit
) {
    // Tester feedback (item 3): replaced Settings with Is This Safe so the
    // primary safety action is in the bottom bar instead of a floating
    // button that overlapped page content. Settings remains accessible from
    // the gear icon in the top bar.
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
            selected = true,
            onClick = onSafetyChecker,
            icon = {
                // Custom styled icon — match the gold-on-black look the
                // Focused mode FAB has, so AARP testers see consistent
                // visual language across both home views.
                //   Yellow circle background, 2dp gold border, black shield.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(WarmGold)
                        .border(2.dp, WarmGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = "Is This Safe",
                        tint = TextOnGold,  // dark text-on-gold from theme
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            label = {
                Text(
                    "Is This Safe?",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextOnGold,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = NavigationBarItemDefaults.colors(
                // Indicator colour matches the icon background so when the
                // user taps, the highlight ring blends in cleanly.
                indicatorColor = WarmGold.copy(alpha = 0.20f),
                selectedTextColor = TextOnGold,
                unselectedTextColor = TextOnGold
            )
        )
    }
}

@Composable
private fun WhatsThisAppCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("\uD83D\uDCF1", fontSize = 32.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "What's This App?",
                    style = MaterialTheme.typography.titleSmall,
                    color = WarmGold,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "See an unfamiliar app? Tap here to check if it's safe",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = WarmGold
            )
        }
    }
}

@Composable
private fun NotificationAccessBanner() {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        colors = CardDefaults.cardColors(containerColor = WarningAmberLight),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = WarningAmber
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Message scanning not active",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tap here to allow Safe Companion to read your notifications so it can check messages for scams.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = WarningAmber
            )
        }
    }
}


