package com.safeharborsecurity.app.ui.privacy

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.local.entity.RemediationKnowledgeEntity
import com.safeharborsecurity.app.data.repository.PrivacyThreat
import com.safeharborsecurity.app.data.repository.ThreatCategory
import com.safeharborsecurity.app.ui.theme.*
import com.safeharborsecurity.app.util.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyMonitorScreen(
    onNavigateBack: () -> Unit,
    viewModel: PrivacyMonitorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showStopListeningSheet by remember { mutableStateOf(false) }

    // Re-scan when the user returns from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasLeftScreen by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasLeftScreen = true
                Lifecycle.Event.ON_RESUME -> {
                    if (hasLeftScreen && state.isShieldEnabled) {
                        viewModel.runManualScan()
                        hasLeftScreen = false
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showStopListeningSheet) {
        StopListeningBottomSheet(
            onDismiss = { showStopListeningSheet = false },
            onScanAfterDone = { viewModel.runManualScan() }
        )
    }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
                title = {
                    Text(
                        "Privacy Monitor",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    Switch(
                        checked = state.isShieldEnabled,
                        onCheckedChange = { viewModel.setShieldEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WarmGold,
                            checkedTrackColor = NavyBlueLight,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = NavyBlueLight.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
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
            // Summary card
            item {
                SummaryCard(
                    isEnabled = state.isShieldEnabled,
                    threatCount = state.lastScanResult?.threats?.size ?: 0,
                    isScanning = state.isScanning,
                    onScanNow = { viewModel.runManualScan() }
                )
            }

            // Last scan time
            val scanTime = state.lastScanResult?.scanTimeMillis
            if (scanTime != null) {
                item {
                    Text(
                        "Last checked: ${formatTime(scanTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Stop Silent Listening button
            if (state.isShieldEnabled) {
                item {
                    StopSilentListeningActionCard(
                        onClick = { showStopListeningSheet = true }
                    )
                }
            }

            // NFC Security Status
            item {
                NfcStatusCard()
            }

            // Threat list — grouped by section
            val threats = state.lastScanResult?.threats ?: emptyList()
            if (threats.isNotEmpty()) {
                // Tester feedback (item 7): a bold "Scan Results" header
                // tells the user the cards below are *findings* from the
                // scan, not generic info cards.
                item(key = "scan_results_header") {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.HorizontalDivider(
                        color = TextSecondary.copy(alpha = 0.25f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Scan Results",
                        style = MaterialTheme.typography.titleLarge,
                        color = NavyBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Apps Safe Companion noticed using sensitive features:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                }

                val sections = listOf(
                    "🎤 Microphone Access",
                    "📷 Camera Access",
                    "🖥️ Screen Access",
                    "📍 Location Access",
                    "📋 Clipboard & Data Access",
                    "🔐 Device Control",
                    "🌐 Network & VPN"
                )

                val grouped = threats.groupBy { "${it.category.sectionEmoji} ${it.category.section}" }

                for (section in sections) {
                    val sectionThreats = grouped[section] ?: continue

                    item(key = "header_$section") {
                        Text(
                            section,
                            style = MaterialTheme.typography.headlineSmall,
                            color = NavyBlue
                        )
                    }

                    items(sectionThreats, key = { it.packageName + it.category }) { threat ->
                        val remediation = viewModel.findRemediationForPackage(threat.packageName)
                        ThreatCardWithRemediation(
                            threat = threat,
                            remediation = remediation,
                            onGoToSettings = { viewModel.openAppSettings(threat) },
                            onOpenRemediationSettings = { r -> viewModel.openRemediationSettings(r) }
                        )
                    }
                }

                // Show "All clear" for sections with no threats
                val clearSections = sections.filter { it !in grouped.keys }
                if (clearSections.isNotEmpty()) {
                    item(key = "all_clear_sections") {
                        Spacer(Modifier.height(4.dp))
                        clearSections.forEach { section ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("✅", fontSize = 16.sp)
                                Text(
                                    "$section — All clear",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SafeGreen
                                )
                            }
                        }
                    }
                }
            } else if (state.isShieldEnabled && !state.isScanning && state.lastScanResult != null) {
                item { AllClearPrivacyCard() }
            }

            // Knowledge base status
            if (state.isShieldEnabled) {
                item {
                    KnowledgeBaseStatusCard(
                        lastSync = state.remediationLastSync,
                        recordCount = state.remediationKnowledge.size,
                        onRefresh = { viewModel.refreshKnowledgeBase() }
                    )
                }
            }

            // Explanation for elderly users
            if (!state.isShieldEnabled) {
                item { ExplanationCard() }
            }
        }
    }
}

@Composable
private fun StopSilentListeningActionCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Icons.Default.ShieldMoon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = WarningAmber
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Stop Silent Listening",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Step-by-step guide to turn off tracking and microphone access",
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

@Composable
private fun SummaryCard(
    isEnabled: Boolean,
    threatCount: Int,
    isScanning: Boolean,
    onScanNow: () -> Unit
) {
    val (bgColor, icon, title, subtitle) = when {
        !isEnabled -> SummaryStyle(
            LightSurface,
            Icons.Default.MicOff,
            "Listening Shield is off",
            "Turn it on to check if any apps are using your microphone"
        )
        isScanning -> SummaryStyle(
            WarmGoldLight.copy(alpha = 0.3f),
            Icons.Default.Search,
            "Checking your phone...",
            "Looking for apps that might be listening"
        )
        threatCount == 0 -> SummaryStyle(
            SafeGreenLight,
            Icons.Default.VerifiedUser,
            "All clear!",
            "No apps are listening to you right now"
        )
        threatCount == 1 -> SummaryStyle(
            WarningAmberLight,
            Icons.Default.Warning,
            "1 app found",
            "One app might be using your microphone or tracking you"
        )
        else -> SummaryStyle(
            WarningAmberLight,
            Icons.Default.Warning,
            "$threatCount apps found",
            "Some apps might be using your microphone or tracking you"
        )
    }

    // Tester feedback (item 7): summary card needs to read as the "alert at
    // a glance" rather than blending into the rest of the section. White
    // background + bright accent border (orange/red/green based on
    // threatCount). Tone-matched to the section's themes underneath.
    val borderColor = when {
        threatCount > 0 -> WarningAmber  // bright orange outline
        isScanning -> WarmGold
        else -> SafeGreen
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = when {
                        !isEnabled -> TextSecondary
                        threatCount > 0 -> WarningAmber
                        else -> SafeGreen
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            if (isEnabled && !isScanning) {
                OutlinedButton(
                    onClick = onScanNow,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Check again now")
                }
            }

            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = WarmGold,
                    trackColor = WarmGoldLight.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ── Threat card with per-app remediation ─────────────────────────────────────

@Composable
private fun ThreatCardWithRemediation(
    threat: PrivacyThreat,
    remediation: RemediationKnowledgeEntity?,
    onGoToSettings: () -> Unit,
    onOpenRemediationSettings: (RemediationKnowledgeEntity) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Confirmation dialog for direct-toggle apps
    if (showConfirmDialog && remediation != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = WarmWhite,
            icon = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = NavyBlue,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Opening settings for ${remediation.appDisplayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = NavyBlue
                )
            },
            text = {
                Text(
                    "We've opened the settings for ${remediation.appDisplayName}. " +
                            "Look for the switches on that screen and turn them OFF.\n\n" +
                            "When you're done, come back here and we'll check if it worked.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    lineHeight = 24.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showConfirmDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Got it", color = Color.White)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WarningAmberLight),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row: emoji + app name + category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val categoryEmoji = threat.category.sectionEmoji
                    Text(categoryEmoji, fontSize = 24.sp)
                    Column {
                        Text(
                            threat.appName,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            categoryLabel(threat.category),
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }

                // Badge showing status
                Surface(
                    color = WarningAmber,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "ACTIVE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Reason text
            Text(
                threat.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // ── Per-app remediation section ──────────────────────────────

            if (remediation != null) {
                if (remediation.canToggleDirectly) {
                    // Direct toggle: switch + "Turn off listening" label
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WarmWhiteLight),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.ToggleOn,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    "Turn off listening",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(
                                checked = true,  // shown because threat is active
                                onCheckedChange = {
                                    // When user flips the switch OFF, open settings + show dialog
                                    onOpenRemediationSettings(remediation)
                                    showConfirmDialog = true
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = WarningAmber,
                                    checkedTrackColor = WarningAmberLight,
                                    uncheckedThumbColor = SafeGreen,
                                    uncheckedTrackColor = SafeGreenLight
                                )
                            )
                        }
                    }
                } else {
                    // Expandable "How to turn this off" instructions
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded },
                        colors = CardDefaults.cardColors(containerColor = WarmWhiteLight),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = NavyBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "How to turn this off",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NavyBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = NavyBlue
                                )
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        start = 12.dp, end = 12.dp, bottom = 12.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        remediation.howToInstructions,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        lineHeight = 24.sp
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // "Open Settings" button
                                        Button(
                                            onClick = { onOpenRemediationSettings(remediation) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Open Settings", color = Color.White, fontSize = 13.sp)
                                        }

                                        // "Learn More" button — opens in Custom Tab
                                        if (remediation.learnMoreUrl != null) {
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        val customTabsIntent = CustomTabsIntent.Builder()
                                                            .setShowTitle(true)
                                                            .build()
                                                        customTabsIntent.launchUrl(
                                                            context,
                                                            Uri.parse(remediation.learnMoreUrl)
                                                        )
                                                    } catch (_: Exception) {
                                                        // Fallback to plain browser
                                                        val intent = Intent(
                                                            Intent.ACTION_VIEW,
                                                            Uri.parse(remediation.learnMoreUrl)
                                                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                                        try {
                                                            context.startActivity(intent)
                                                        } catch (_: Exception) { }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.OpenInNew,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Learn More", fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ── Fallback: app not in knowledge base ──────────────────

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded },
                    colors = CardDefaults.cardColors(containerColor = WarmWhiteLight),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = NavyBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "How to turn this off",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NavyBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = NavyBlue
                            )
                        }

                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    start = 12.dp, end = 12.dp, bottom = 12.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "Step 1: Tap \"Open Settings\" below.\n" +
                                            "Step 2: Look for \"Permissions\" and tap it.\n" +
                                            "Step 3: Find \"Microphone\" and turn it OFF.\n" +
                                            "Step 4: Come back here and we'll check if it worked.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    lineHeight = 24.sp
                                )

                                Button(
                                    onClick = {
                                        // Deep-link to this app's system settings page
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                        ).apply {
                                            data = Uri.parse("package:${threat.packageName}")
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            onGoToSettings()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open Settings for ${threat.appName}", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Knowledge base status card ───────────────────────────────────────────────

@Composable
private fun KnowledgeBaseStatusCard(
    lastSync: Long,
    recordCount: Int,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = NavyBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Knowledge Base",
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyBlue,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                if (lastSync > 0L) {
                    val daysAgo = ((System.currentTimeMillis() - lastSync) / (1000 * 60 * 60 * 24)).toInt()
                    when {
                        daysAgo == 0 -> "Last updated: today ($recordCount known apps)"
                        daysAgo == 1 -> "Last updated: yesterday ($recordCount known apps)"
                        else -> "Last updated: $daysAgo days ago ($recordCount known apps)"
                    }
                } else {
                    "Using built-in knowledge ($recordCount known apps)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Check for updates now")
            }
        }
    }
}

// ── Static helper cards ──────────────────────────────────────────────────────

@Composable
private fun AllClearPrivacyCard() {
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
            Column {
                Text(
                    "You're safe!",
                    style = MaterialTheme.typography.titleMedium,
                    color = SafeGreen
                )
                Text(
                    "No apps are listening to your microphone or tracking you right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ExplanationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "What does Listening Shield do?",
                style = MaterialTheme.typography.titleMedium,
                color = NavyBlue
            )
            Text(
                "Some apps on your phone might use your microphone when you don't expect it, " +
                "or track your activity to show you ads. Listening Shield checks for these apps " +
                "and lets you know so you can stay safe.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                "Turn on the shield above to start checking.",
                style = MaterialTheme.typography.bodyMedium,
                color = WarmGold
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun categoryLabel(category: ThreatCategory): String = category.section

@Composable
private fun NfcStatusCard() {
    val context = LocalContext.current
    val nfcAdapter = remember { android.nfc.NfcAdapter.getDefaultAdapter(context) }
    val isAvailable = nfcAdapter != null
    val isEnabled = nfcAdapter?.isEnabled == true

    val (statusColor, statusIcon, statusText, detail) = when {
        !isAvailable -> NfcCardStyle(Color.Gray, Icons.Default.PhonelinkOff, "NFC Not Available", "This device does not have NFC hardware.")
        !isEnabled -> NfcCardStyle(SafeGreen, Icons.Default.CheckCircle, "NFC is Off", "NFC is turned off. No NFC risks.")
        else -> NfcCardStyle(WarningAmber, Icons.Default.Contactless, "NFC is On", "NFC is active. Safe Companion will check any NFC tag you tap.")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(statusIcon, "NFC", tint = statusColor, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(statusText, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavyBlue)
                Text(detail, fontSize = 14.sp, color = TextSecondary)
            }
            if (isAvailable && isEnabled) {
                IconButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                }) {
                    Icon(Icons.Default.Settings, "NFC Settings", tint = NavyBlue)
                }
            }
        }
    }
}

private data class NfcCardStyle(
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String
)

private data class SummaryStyle(
    val bgColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String
)
