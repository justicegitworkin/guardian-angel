package com.safeharborsecurity.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.remote.model.FamilyContact
import com.safeharborsecurity.app.ui.theme.*

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacyPromise: () -> Unit = {},
    onNavigateToEmailSetup: () -> Unit = {},
    onNavigateToAdditionalSecurity: () -> Unit = {},
    onNavigateToPermissionWalkthrough: () -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val voiceTestResult by viewModel.voiceTestResult.collectAsStateWithLifecycle()
    val voiceDebugLog by viewModel.voiceDebugLog.collectAsStateWithLifecycle()

    var userName by remember(state.userName) { mutableStateOf(state.userName) }
    var apiKey by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    var showAddFamily by remember { mutableStateOf(false) }
    var showAddTrusted by remember { mutableStateOf(false) }

    if (showAddFamily) {
        AddContactDialog(
            title = "Add Family Member",
            hasNickname = true,
            onConfirm = { nick, num ->
                viewModel.addFamilyContact(nick, num)
                showAddFamily = false
            },
            onDismiss = { showAddFamily = false }
        )
    }

    if (showAddTrusted) {
        AddContactDialog(
            title = "Add Trusted Number",
            hasNickname = false,
            onConfirm = { _, num ->
                viewModel.addTrustedNumber(num)
                showAddTrusted = false
            },
            onDismiss = { showAddTrusted = false }
        )
    }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            SettingsTopBar(onBack = onNavigateBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Setup & Permissions ─────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPermissionWalkthrough() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = WarmGold,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Setup & Permissions",
                            fontSize = 18.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Review what Safe Companion has access to",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = TextSecondary
                    )
                }
            }

            // ── How Safe Companion Helps You ─────────────────────────────
            // Top-most section because it's the most fundamental product
            // decision the user makes. Two modes side-by-side, with Silent
            // Guardian honestly marked Coming Soon since the underlying
            // capabilities (default-SMS-app, gmail.modify scope, full
            // CallScreeningService) are being built incrementally.
            SettingsSection(title = "How Safe Companion Helps You") {
                Text(
                    "Safe Companion can work two different ways. You can change " +
                        "this any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))

                val currentMode = state.operatingMode

                // Watch and Warn (current behaviour)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setOperatingMode("WATCH_AND_WARN") },
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentMode == "WATCH_AND_WARN")
                            SafeGreen.copy(alpha = 0.15f) else LightSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (currentMode == "WATCH_AND_WARN")
                        BorderStroke(2.dp, SafeGreen) else null
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = currentMode == "WATCH_AND_WARN",
                                onClick = { viewModel.setOperatingMode("WATCH_AND_WARN") }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Watch and Warn",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = NavyBlue
                            )
                            Spacer(Modifier.weight(1f))
                            if (currentMode == "WATCH_AND_WARN") {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SafeGreen.copy(alpha = 0.20f)
                                ) {
                                    Text(
                                        "Active",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SafeGreen,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Recommended for most people. Safe Companion watches " +
                                "in the background. When it spots a scam — a text, " +
                                "an email, a phone call — it alerts you and suggests " +
                                "what to do. You stay in control of every decision.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Silent Guardian (coming soon)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setOperatingMode("SILENT_GUARDIAN") },
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentMode == "SILENT_GUARDIAN")
                            NavyBlue.copy(alpha = 0.10f) else LightSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (currentMode == "SILENT_GUARDIAN")
                        BorderStroke(2.dp, NavyBlue) else null
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = currentMode == "SILENT_GUARDIAN",
                                onClick = { viewModel.setOperatingMode("SILENT_GUARDIAN") }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Silent Guardian",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = NavyBlue
                            )
                            Spacer(Modifier.weight(1f))
                            if (currentMode == "SILENT_GUARDIAN") {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = NavyBlue.copy(alpha = 0.20f)
                                ) {
                                    Text(
                                        "Active",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NavyBlue,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Hands-off mode. Safe Companion handles scams quietly " +
                                "for you and sends a weekly report of everything it did. " +
                                "You can review anything that was wrongly flagged from " +
                                "the report.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "What it does today:",
                            style = MaterialTheme.typography.labelLarge,
                            color = NavyBlue,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Text(
                            " ✓ Auto-declines scam phone calls (needs Caller-ID role below)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            " ✓ Auto-quarantines scam Gmail emails to a SafeCompanion " +
                                "label (needs Gmail connected — Settings → Email)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Coming next:",
                            style = MaterialTheme.typography.labelLarge,
                            color = NavyBlue,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Text(
                            " • Auto-handle scam texts (needs Google Play review)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        // For Silent Guardian to actually decline calls, the
                        // user must grant the Call Screening role. This
                        // launches Android's standard role-picker dialog.
                        if (currentMode == "SILENT_GUARDIAN") {
                            Spacer(Modifier.height(12.dp))
                            CallScreeningRoleButton()
                        }
                    }
                }
            }

            // ── Profile ──────────────────────────────────────────────────
            SettingsSection(title = "Your Profile") {
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Your name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.setUserName(userName) }) {
                            Icon(Icons.Default.Save, contentDescription = "Save name", tint = WarmGold)
                        }
                    }
                )
            }

            // ── API Key ──────────────────────────────────────────────────
            SettingsSection(title = "Claude API Key (Optional)") {
                // Item 5 Stage 1: Status banner so the user knows which mode the
                // app is currently in. Green = full AI, amber = on-device-only.
                if (state.apiKey.isBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Running on-device only",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                Text(
                                    "Scam detection works without an API key. " +
                                        "Add one below for higher-accuracy analysis " +
                                        "and natural-language chat with Grace, James, " +
                                        "Sophie, or George.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SafeGreen.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SafeGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Full AI protection enabled",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                Text(
                                    "Safe Companion is using your Claude key for " +
                                        "deeper scam analysis and natural-language chat.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Key-fingerprint chip — shows the last 6 characters of the
                // saved Claude key (just enough to disambiguate "is this the
                // key I think it is?") without leaking entropy. Critical for
                // testers when they rotate keys in local.properties and want
                // to confirm the new build picked up the new key.
                if (state.apiKey.isNotBlank()) {
                    val fingerprint = state.apiKey.takeLast(6)
                    Text(
                        "Active key ends in …$fingerprint",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Safe Companion works out of the box! Scam detection, message scanning, " +
                        "and all safety features run on your device with no setup. " +
                        "Add a Claude API key to unlock the AI voice assistant and deeper analysis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; viewModel.clearTestResult() },
                    label = { Text("API key (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    // Always-masked. The "show key" eye toggle was removed
                    // per security review — keys should never be shoulder-
                    // surfed off the screen, even in beta.
                    visualTransformation = PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.setApiKey(apiKey) }) {
                            Icon(Icons.Default.Save, contentDescription = "Save key", tint = WarmGold)
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.testConnection(apiKey) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = apiKey.isNotBlank() && testResult != "testing"
                ) {
                    if (testResult == "testing") {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Test Connection", style = MaterialTheme.typography.labelLarge)
                }

                if (testResult != null && testResult != "testing") {
                    val (color, msg) = when (testResult) {
                        "success" -> Pair(SafeGreen, "✅ Connected successfully!")
                        "invalid_key" -> Pair(ScamRed, "❌ Invalid key. Please check.")
                        else -> Pair(WarningAmber, "⚠️ Connection failed. Check your internet.")
                    }
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = color)
                }
            }

            // ── Voice Quality ──────────────────────────────────────────────
            SettingsSection(title = "Voice Quality") {
                var elevenLabsKey by remember(state.elevenLabsApiKey) { mutableStateOf(state.elevenLabsApiKey) }
                var showElKey by remember { mutableStateOf(false) }
                val keyValidation = viewModel.validateElevenLabsKey(elevenLabsKey)
                val isTestRunning = voiceTestResult?.startsWith("Connecting") == true ||
                    voiceTestResult?.startsWith("Playing") == true

                val currentTierText = if (state.elevenLabsApiKey.isNotBlank()) {
                    "Currently using: ElevenLabs Natural Voice"
                } else {
                    "Currently using: Device Voice"
                }

                Text(
                    currentTierText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.elevenLabsApiKey.isNotBlank()) SafeGreen else TextSecondary
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = elevenLabsKey,
                    onValueChange = { elevenLabsKey = it; viewModel.clearVoiceTestResult() },
                    label = { Text("ElevenLabs API key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    // Always-masked — same security policy as the Claude key.
                    visualTransformation = PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.saveElevenLabsKey(elevenLabsKey) }) {
                            Icon(Icons.Default.Save, contentDescription = "Save key", tint = WarmGold)
                        }
                    }
                )

                // Key format validation warning
                if (elevenLabsKey.isNotBlank() && keyValidation != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(keyValidation, style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveElevenLabsKey(elevenLabsKey) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                        enabled = elevenLabsKey.isNotBlank()
                    ) {
                        Text("Save Key", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { viewModel.testElevenLabsVoice(elevenLabsKey) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = elevenLabsKey.isNotBlank() && !isTestRunning
                    ) {
                        if (isTestRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (isTestRunning) "Testing..." else "Test Voice",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Detailed test result status
                if (voiceTestResult != null) {
                    Spacer(Modifier.height(4.dp))
                    val (color, msg) = when {
                        voiceTestResult == "el_success" ->
                            Pair(SafeGreen, "ElevenLabs connected — playing natural voice")
                        voiceTestResult?.startsWith("fallback_") == true ->
                            Pair(WarningAmber, "ElevenLabs failed — fell back to ${voiceTestResult?.removePrefix("fallback_")}")
                        voiceTestResult?.startsWith("error:") == true ->
                            Pair(ScamRed, "Failed: ${voiceTestResult?.removePrefix("error:")}")
                        voiceTestResult?.startsWith("Connecting") == true ->
                            Pair(TextSecondary, voiceTestResult ?: "")
                        voiceTestResult?.startsWith("Playing") == true ->
                            Pair(SafeGreen, voiceTestResult ?: "")
                        else -> Pair(TextSecondary, voiceTestResult ?: "")
                    }
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = color)
                }

                if (state.elevenLabsApiKey.isBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Without a key, Safe Companion uses your device's built-in voice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Debug log panel (debug builds only)
                if (voiceDebugLog.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NavyBlueDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = voiceDebugLog,
                            modifier = Modifier.padding(8.dp),
                            color = SafeGreen,
                            fontSize = 9.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 12.sp
                        )
                    }
                }
            }

            // ── Shields ──────────────────────────────────────────────────
            SettingsSection(title = "Protection Shields") {
                ShieldToggleRow("💬 Messages Shield", state.isSmsShieldOn, viewModel::setSmsShield)
                ShieldToggleRow("📞 Call Shield", state.isCallShieldOn, viewModel::setCallShield)
                ShieldToggleRow("📧 Email Shield", state.isEmailShieldOn, viewModel::setEmailShield)
            }

            // ── Alert Level ──────────────────────────────────────────────
            SettingsSection(title = "Alert Level") {
                Text(
                    "Choose how Safe Companion alerts you about threats",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                val alertOptions = listOf(
                    Triple("off", "Off", "I'll check the app myself"),
                    Triple("subtle", "Quiet alerts", "Standard notification in the status bar"),
                    Triple("attention", "Grab my attention", "Full-screen alert that wakes the phone")
                )
                alertOptions.forEach { (value, label, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setAlertLevel(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = state.alertLevel == value,
                            onClick = { viewModel.setAlertLevel(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            // ── Caller Announcements ─────────────────────────────────────
            SettingsSection(title = "Caller Announcements") {
                Text(
                    "Announce the name of saved contacts when they call",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                ShieldToggleRow("Announce callers by name", state.callerAnnouncementsEnabled, viewModel::setCallerAnnouncements)
            }

            // ── Email Accounts ───────────────────────────────────────────
            SettingsSection(title = "Email Accounts") {
                Text(
                    "Connect your email accounts so Safe Companion can scan your inbox for scam emails.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNavigateToEmailSetup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage Email Accounts", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── Text Size ────────────────────────────────────────────────
            SettingsSection(title = "Text Size") {
                val sizes = listOf("NORMAL" to "Normal", "LARGE" to "Large", "EXTRA_LARGE" to "Extra Large")
                sizes.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setTextSize(value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = state.textSizePref == value,
                            onClick = { viewModel.setTextSize(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = NavyBlue)
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                }
            }

            // ── Family Contacts ──────────────────────────────────────────
            SettingsSection(title = "Family Alert Contacts") {
                Text(
                    "These people will be texted if Safe Companion detects a serious scam.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))

                state.familyContacts.forEach { contact ->
                    ContactChip(
                        label = "${contact.nickname} (${contact.number})",
                        onRemove = { viewModel.removeFamilyContact(contact) }
                    )
                }

                OutlinedButton(
                    onClick = { showAddFamily = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Family Member", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── Family Safety Alerts ────────────────────────────────────
            FamilySafetyAlertsCard(
                isEnabled = state.isFamilyAlertsEnabled,
                isConsented = state.isFamilyAlertsConsented,
                alertLevel = state.familyAlertLevel,
                hasContacts = state.familyContacts.isNotEmpty(),
                onToggle = { enabled ->
                    if (enabled && !state.isFamilyAlertsConsented) {
                        // Will be handled by consent dialog
                    } else {
                        viewModel.setFamilyAlertsEnabled(enabled)
                    }
                },
                onConsent = {
                    viewModel.setFamilyAlertsConsented()
                    viewModel.setFamilyAlertsEnabled(true)
                },
                onAlertLevelChange = { viewModel.setFamilyAlertLevel(it) }
            )

            // ── Trusted Numbers ──────────────────────────────────────────
            SettingsSection(title = "Trusted Contacts") {
                Text(
                    "Texts and calls from these numbers are never flagged as scams.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))

                state.trustedNumbers.forEach { number ->
                    ContactChip(label = number, onRemove = { viewModel.removeTrustedNumber(number) })
                }

                OutlinedButton(
                    onClick = { showAddTrusted = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Trusted Number", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── Bank Phone Number ──────────────────────────────────────
            SettingsSection(title = "Emergency: My Bank") {
                var bankNumber by remember(state.bankPhoneNumber) { mutableStateOf(state.bankPhoneNumber) }
                Text(
                    "Save your bank's phone number so you can call them with one tap if you get scammed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = bankNumber,
                    onValueChange = { bankNumber = it },
                    label = { Text("Bank phone number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.setBankPhoneNumber(bankNumber) }) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = WarmGold)
                        }
                    }
                )
            }

            // ── Daily Check-In ────────────────────────────────────────
            SettingsSection(title = "Daily Check-In") {
                Text(
                    "Safe Companion can remind you to check in each morning. If you don't, it will let your family know.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                ShieldToggleRow("Notify family if missed", state.checkInNotifyFamily, viewModel::setCheckInNotifyFamily)
            }

            // ── Knowledge Base ──────────────────────────────────────────
            SettingsSection(title = "Listening Shield Knowledge Base") {
                Text(
                    "Safe Companion keeps a list of apps known to track or listen. " +
                    "You can update this list using your Claude API key.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))

                val syncText = if (state.remediationLastSync > 0L) {
                    val daysAgo = ((System.currentTimeMillis() - state.remediationLastSync) / (1000 * 60 * 60 * 24)).toInt()
                    when {
                        daysAgo == 0 -> "Last updated: today"
                        daysAgo == 1 -> "Last updated: yesterday"
                        else -> "Last updated: $daysAgo days ago"
                    }
                } else {
                    "Never updated (using built-in list)"
                }
                Text(
                    syncText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.refreshKnowledgeBase() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = state.apiKey.isNotBlank()
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Check for updates now", style = MaterialTheme.typography.labelLarge)
                }

                if (state.apiKey.isBlank()) {
                    Text(
                        "Add your API key above to enable knowledge base updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                }
            }

            // ── Additional Security ──────────────────────────────────────
            SettingsSection(title = "Additional Security") {
                Text(
                    "Connect third-party services to check for data leaks, monitor your credit, and protect your identity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onNavigateToAdditionalSecurity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect Additional Security", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── Help & Diagnostics (beta) ─────────────────────────────────
            SettingsSection(title = "Help & Diagnostics") {
                var showLogDialog by remember { mutableStateOf(false) }
                val ctx = LocalContext.current
                if (showLogDialog) {
                    AlertDialog(
                        onDismissRequest = { showLogDialog = false },
                        title = { Text("Send diagnostic logs") },
                        text = {
                            Text(
                                "Safe Companion will collect a small file of " +
                                    "technical details from your phone — what " +
                                    "the app has been doing, errors, app version, " +
                                    "and your phone model. It does not include " +
                                    "messages, contact names, phone numbers, or " +
                                    "any image content. Your email app will open " +
                                    "with the file attached so you can review it " +
                                    "before sending."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showLogDialog = false
                                com.safeharborsecurity.app.util.LogCollector.shareLogs(ctx)
                            }) { Text("Continue") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLogDialog = false }) { Text("Cancel") }
                        }
                    )
                }
                Text(
                    "If something isn't working right, you can send the team " +
                        "a small file of technical details that helps us find " +
                        "and fix bugs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showLogDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = NavyBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Send Diagnostic Logs", color = NavyBlue)
                }
            }

            // ── Security ────────────────────────────────────────────────
            SettingsSection(title = "App Security") {
                val context = LocalContext.current
                var showChangePinDialog by remember { mutableStateOf(false) }
                var showDeleteDataDialog by remember { mutableStateOf(false) }

                if (state.hasPinSet) {
                    Text(
                        "PIN is set. Your app is protected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SafeGreen
                    )
                    Spacer(Modifier.height(8.dp))

                    // Tester feedback (item 4): when the user turns on
                    // fingerprint unlock, prompt them to authenticate with
                    // their fingerprint right then to confirm it works.
                    // Otherwise they can enable a feature that may fail on
                    // first lock and leave them unable to get back in.
                    val activity = context as? androidx.fragment.app.FragmentActivity
                    ShieldToggleRow("Use fingerprint to unlock", state.biometricEnabled) { newValue ->
                        if (newValue && activity != null) {
                            // Confirm fingerprint works BEFORE persisting the toggle.
                            val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                            val prompt = androidx.biometric.BiometricPrompt(
                                activity,
                                executor,
                                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(
                                        result: androidx.biometric.BiometricPrompt.AuthenticationResult
                                    ) {
                                        viewModel.setBiometricEnabled(true)
                                        android.widget.Toast.makeText(
                                            activity,
                                            "Fingerprint unlock turned on.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        // User cancelled or error — leave the toggle off.
                                        if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                                            errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                            android.widget.Toast.makeText(
                                                activity,
                                                "Couldn't enable fingerprint: $errString",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            )
                            val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Confirm fingerprint")
                                .setSubtitle("Touch the fingerprint sensor to turn this on.")
                                .setNegativeButtonText("Cancel")
                                .build()
                            prompt.authenticate(info)
                        } else {
                            viewModel.setBiometricEnabled(newValue)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text("Auto-lock after:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    val timeouts = listOf(1 to "1 minute", 5 to "5 minutes", 15 to "15 minutes", 0 to "Never")
                    timeouts.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAutoLockTimeout(value) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = state.autoLockTimeoutMinutes == value,
                                onClick = { viewModel.setAutoLockTimeout(value) },
                                colors = RadioButtonDefaults.colors(selectedColor = NavyBlue)
                            )
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showChangePinDialog = true },
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Text("Change PIN", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = { viewModel.lockAppNow() },
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Lock Now", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    Text(
                        "No PIN set. Anyone who picks up your phone can open Safe Companion.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarningAmber
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showChangePinDialog = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                    ) {
                        Text("Set Up PIN", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (showChangePinDialog) {
                    PinChangeDialog(
                        onConfirm = { newPin ->
                            viewModel.changePin(newPin)
                            showChangePinDialog = false
                        },
                        onDismiss = { showChangePinDialog = false }
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = LightSurface)
                Spacer(Modifier.height(16.dp))

                // Privacy Promise link
                OutlinedButton(
                    onClick = onNavigateToPrivacyPromise,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Our Privacy Promise", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(8.dp))

                // Delete All Data
                OutlinedButton(
                    onClick = { showDeleteDataDialog = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ScamRed)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ScamRed)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete All My Data", style = MaterialTheme.typography.labelLarge, color = ScamRed)
                }

                if (showDeleteDataDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDataDialog = false },
                        containerColor = WarmWhite,
                        title = { Text("Delete All Your Data?", color = ScamRed) },
                        text = {
                            Text(
                                "This will remove all your Safe Companion data from this phone including your settings, alert history, and contacts. Safe Companion will restart as if newly installed. Are you sure?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteAllData()
                                    showDeleteDataDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ScamRed)
                            ) {
                                Text("Yes, delete everything", color = Color.White)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showDeleteDataDialog = false }) {
                                Text("No, keep my data")
                            }
                        }
                    )
                }
            }

            // ── Privacy & Legal ──────────────────────────────────────────
            SettingsSection(title = "Privacy & Legal") {
                OutlinedButton(
                    onClick = onNavigateToPrivacyPolicy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("View Privacy Policy", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNavigateToPrivacyPromise,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Our Privacy Promise", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── About ────────────────────────────────────────────────────
            SettingsSection(title = "About Safe Companion") {
                Text(
                    "Safe Companion v1.0\n\nDesigned to protect seniors from phone and text scams. " +
                    "Most scam detection runs on your device. If you choose to add a Claude API key, " +
                    "ambiguous messages are also sent to Anthropic for a second opinion. " +
                    "See the Privacy Policy above for full details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * Silent Guardian — Call Screening role grant.
 *
 * For Safe Companion to actually intercept incoming calls and silently
 * reject scams, the user has to grant the Caller ID & spam-app role on
 * their phone. Android exposes this via RoleManager. This button:
 *   - Shows "Grant call-screening role" if not yet granted
 *   - Shows "Active — auto-declining scam calls" once granted
 *   - Tapping launches Android's standard role-grant dialog
 *
 * Role grants persist across reboots, so this is a one-time setup step.
 */
@Composable
private fun CallScreeningRoleButton() {
    val context = LocalContext.current
    var isHolder by remember { mutableStateOf(checkCallScreeningRoleHeld(context)) }
    var showFallback by remember { mutableStateOf(false) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        // Whether the user accepted or declined, re-check the role state.
        isHolder = checkCallScreeningRoleHeld(context)
        // If the dialog returned without granting, the user might have hit
        // an OEM that silently rejected — show the manual-Settings fallback
        // so they're not stuck wondering what happened.
        if (!isHolder) showFallback = true
    }

    if (showFallback) {
        AlertDialog(
            onDismissRequest = { showFallback = false },
            containerColor = LightSurface,
            title = { Text("Set Safe Companion as your call-screening app", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Your phone didn't open the call-screening permission " +
                            "directly. You can set it from your phone's Settings:",
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("1. Tap Open Settings below.", color = TextPrimary)
                    Text(
                        "2. Look for \"Default apps\" (sometimes inside \"Apps\").",
                        color = TextPrimary
                    )
                    Text(
                        "3. Find \"Caller ID & spam app\" and pick Safe Companion.",
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "If you don't see that option, your phone or carrier " +
                            "may not allow third-party call screening — Silent " +
                            "Guardian will still auto-quarantine emails and " +
                            "show full-screen scam alerts, just without " +
                            "auto-declining calls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFallback = false
                        // Try the cleanest action first — Default Apps — then
                        // fall back step by step if the OEM doesn't expose it.
                        val candidates = listOf(
                            android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
                            "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
                            android.provider.Settings.ACTION_SETTINGS
                        )
                        var launched = false
                        for (action in candidates) {
                            try {
                                context.startActivity(
                                    android.content.Intent(action)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                                launched = true
                                break
                            } catch (_: Exception) {}
                        }
                        if (!launched) {
                            android.widget.Toast.makeText(
                                context,
                                "Couldn't open Settings. Open it manually and look for Default apps.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                ) {
                    Text("Open Settings", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFallback = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isHolder) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SafeGreen.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, SafeGreen)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeGreen)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Active — auto-declining scam calls",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeGreen,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            }
        }
    } else {
        OutlinedButton(
            onClick = {
                // Path 1: Android 10+ role manager. Most common path.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val rm = context.getSystemService(android.app.role.RoleManager::class.java)
                    val available = runCatching {
                        rm?.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING) == true
                    }.getOrDefault(false)
                    val intent = if (available) {
                        runCatching { rm?.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING) }
                            .getOrNull()
                    } else null

                    if (intent != null) {
                        try {
                            launcher.launch(intent)
                            return@OutlinedButton
                        } catch (_: Exception) { /* fall through to fallback */ }
                    }
                }
                // Path 2: any failure (pre-Q, OEM that doesn't expose the
                // role, intent threw) → show manual-Settings dialog so the
                // button never feels broken.
                showFallback = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, NavyBlue)
        ) {
            Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = NavyBlue)
            Spacer(Modifier.width(8.dp))
            Text(
                "Grant call-screening permission",
                color = NavyBlue,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }
    }
}

private fun checkCallScreeningRoleHeld(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return false
    val rm = context.getSystemService(android.app.role.RoleManager::class.java) ?: return false
    return runCatching { rm.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) }.getOrDefault(false)
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = WarmGold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ShieldToggleRow(label: String, isOn: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        Switch(
            checked = isOn,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = WarmGold, checkedTrackColor = NavyBlue)
        )
    }
}

@Composable
private fun ContactChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = ScamRed)
        }
    }
}

@Composable
private fun AddContactDialog(
    title: String,
    hasNickname: Boolean,
    onConfirm: (nickname: String, number: String) -> Unit,
    onDismiss: () -> Unit
) {
    var nickname by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WarmWhite,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = NavyBlue) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasNickname) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Nickname (e.g. Daughter Sarah)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nickname, number) },
                enabled = number.isNotBlank() && (!hasNickname || nickname.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                modifier = Modifier.height(52.dp)
            ) {
                Text("Add", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.height(52.dp)) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
private fun PinChangeDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WarmWhite,
        title = { Text(if (isConfirming) "Confirm your PIN" else "Enter a 4-digit PIN", color = NavyBlue) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                val currentValue = if (isConfirming) confirmPin else pin
                OutlinedTextField(
                    value = currentValue,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }.take(4)
                        error = null
                        if (isConfirming) {
                            confirmPin = filtered
                            if (filtered.length == 4) {
                                if (filtered == pin) {
                                    onConfirm(pin)
                                } else {
                                    error = "PINs don't match. Try again."
                                    confirmPin = ""
                                    isConfirming = false
                                    pin = ""
                                }
                            }
                        } else {
                            pin = filtered
                            if (filtered.length == 4) {
                                isConfirming = true
                            }
                        }
                    },
                    label = { Text(if (isConfirming) "Confirm PIN" else "New PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = ScamRed)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FamilySafetyAlertsCard(
    isEnabled: Boolean,
    isConsented: Boolean,
    alertLevel: String,
    hasContacts: Boolean,
    onToggle: (Boolean) -> Unit,
    onConsent: () -> Unit,
    onAlertLevelChange: (String) -> Unit
) {
    var showConsentDialog by remember { mutableStateOf(false) }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            containerColor = WarmWhite,
            title = { Text("Family Safety Alerts", color = NavyBlue) },
            text = {
                Text(
                    "When Safe Companion detects something dangerous, it will send a short text message to your family contact.\n\n" +
                    "Your family will NOT see your private messages or calls. They will only receive a brief alert like:\n\n" +
                    "\"Steve may be on a suspicious call. Please check in.\"\n\n" +
                    "You can turn this off at any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConsent()
                        showConsentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                ) {
                    Text("I understand, turn it on", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConsentDialog = false }) {
                    Text("Not now")
                }
            }
        )
    }

    val purpleDark = WarmGold
    val purpleLight = NavyBlueLight

    SettingsSection(title = "Family Safety Alerts") {
        Text(
            "Automatically text a family member when Safe Companion detects something dangerous.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isEnabled) "Alerts are ON" else "Alerts are OFF",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isEnabled) purpleDark else TextSecondary
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = { newValue ->
                    if (newValue && !isConsented) {
                        showConsentDialog = true
                    } else {
                        onToggle(newValue)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = purpleDark,
                    checkedTrackColor = purpleLight
                )
            )
        }

        if (isEnabled) {
            if (!hasContacts) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningAmberLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Add a family contact above to start receiving alerts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarningAmber,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Alert sensitivity",
                style = MaterialTheme.typography.labelLarge,
                color = NavyBlue
            )
            Spacer(Modifier.height(4.dp))

            val levels = listOf("ALL" to "All alerts", "HIGH_ONLY" to "High risk only", "CRITICAL" to "Critical only")
            levels.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlertLevelChange(value) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = alertLevel == value,
                        onClick = { onAlertLevelChange(value) },
                        colors = RadioButtonDefaults.colors(selectedColor = purpleDark)
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        },
        title = {
            Text("⚙️ Settings", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
    )
}
