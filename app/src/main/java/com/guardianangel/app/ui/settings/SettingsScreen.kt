package com.guardianangel.app.ui.settings

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardianangel.app.data.remote.model.FamilyContact
import com.guardianangel.app.ui.theme.*

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val clearDataResult by viewModel.clearDataResult.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var userName by remember(state.userName) { mutableStateOf(state.userName) }
    var apiKey by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var isCallScreeningActive by remember { mutableStateOf(false) }

    var showAddFamily by remember { mutableStateOf(false) }
    var showAddTrusted by remember { mutableStateOf(false) }
    var porcupineKey by remember(state.porcupineKey) { mutableStateOf(state.porcupineKey) }
    var showPorcupineKey by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

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
            // ── Text Size ────────────────────────────────────────────────
            SettingsSection(title = "Text Size") {
                Text(
                    "Choose how large you'd like the text to appear:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextSizeButton(
                        label = "Normal",
                        previewSp = 16f,
                        isSelected = state.textSizePref == "NORMAL",
                        onClick = { viewModel.setTextSize("NORMAL") },
                        modifier = Modifier.weight(1f)
                    )
                    TextSizeButton(
                        label = "Large",
                        previewSp = 20f,
                        isSelected = state.textSizePref == "LARGE",
                        onClick = { viewModel.setTextSize("LARGE") },
                        modifier = Modifier.weight(1f)
                    )
                    TextSizeButton(
                        label = "Extra\nLarge",
                        previewSp = 26f,
                        isSelected = state.textSizePref == "EXTRA_LARGE",
                        onClick = { viewModel.setTextSize("EXTRA_LARGE") },
                        modifier = Modifier.weight(1f)
                    )
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = InputBackground,
                        unfocusedContainerColor = InputBackground,
                        focusedBorderColor = NavyBlue,
                        unfocusedBorderColor = Color(0xFFBBBBBB),
                        focusedLabelColor = NavyBlue,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = NavyBlue
                    ),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.setUserName(userName) }) {
                            Icon(Icons.Default.Save, contentDescription = "Save name", tint = WarmGold)
                        }
                    }
                )
            }

            // ── API Key ──────────────────────────────────────────────────
            SettingsSection(title = "Claude API Key") {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; viewModel.clearTestResult() },
                    label = { Text("API key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = InputBackground,
                        unfocusedContainerColor = InputBackground,
                        focusedBorderColor = NavyBlue,
                        unfocusedBorderColor = Color(0xFFBBBBBB),
                        focusedLabelColor = NavyBlue,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = NavyBlue
                    ),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                            IconButton(onClick = { viewModel.setApiKey(apiKey) }) {
                                Icon(Icons.Default.Save, contentDescription = "Save key", tint = WarmGold)
                            }
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

            // ── Call Screening Setup ──────────────────────────────────────
            SettingsSection(title = "Call Screening Setup") {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Text(
                        "Call screening requires Android 10 or later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else if (isCallScreeningActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeGreen)
                        Text(
                            "Call screening is active",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SafeGreen
                        )
                    }
                } else {
                    Text(
                        "Guardian Angel needs to be set as your call screening app to review incoming calls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val rm = context.getSystemService(RoleManager::class.java)
                            roleRequestLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Set Up Call Screening", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // ── Battery Optimization ──────────────────────────────────────
            SettingsSection(title = "Battery & Background") {
                Text(
                    "For SMS detection to work on all devices, Guardian Angel should be excluded from battery optimization.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        }.onFailure {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.BatteryFull, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Disable Battery Optimization", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── Shields ──────────────────────────────────────────────────
            SettingsSection(title = "Protection Shields") {
                ShieldToggleRow("💬 SMS Shield", state.isSmsShieldOn, viewModel::setSmsShield)
                ShieldToggleRow("📞 Call Shield", state.isCallShieldOn, viewModel::setCallShield)
                ShieldToggleRow("📧 Email Shield", state.isEmailShieldOn, viewModel::setEmailShield)
            }

            // ── Family Contacts ──────────────────────────────────────────
            SettingsSection(title = "Family Alert Contacts") {
                Text(
                    "These people will be texted if Guardian detects a serious scam.",
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

            // ── Wake Word ────────────────────────────────────────────────
            SettingsSection(title = "Wake Word — 'Hey Guardian'") {
                // Toggle row
                ShieldToggleRow(
                    label = "Enable wake word listening",
                    isOn = state.isWakeWordEnabled,
                    onToggle = { viewModel.setWakeWordEnabled(it) }
                )

                Spacer(Modifier.height(4.dp))

                // Status row
                val (statusDot, statusText, statusColor) = when {
                    !state.isWakeWordEnabled -> Triple("○", "Not running", TextSecondary)
                    state.porcupineKey.isBlank() -> Triple("⚠", "No access key set", WarningAmber)
                    else -> Triple("●", "Active — listening", SafeGreen)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(statusDot, style = MaterialTheme.typography.bodyLarge, color = statusColor)
                    Text(statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                }

                Spacer(Modifier.height(12.dp))

                // Access key field
                OutlinedTextField(
                    value = porcupineKey,
                    onValueChange = { porcupineKey = it },
                    label = { Text("Picovoice Access Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPorcupineKey) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = InputBackground,
                        unfocusedContainerColor = InputBackground,
                        focusedBorderColor = NavyBlue,
                        unfocusedBorderColor = Color(0xFFBBBBBB),
                        focusedLabelColor = NavyBlue,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = NavyBlue
                    ),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showPorcupineKey = !showPorcupineKey }) {
                                Icon(
                                    if (showPorcupineKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                            IconButton(onClick = { viewModel.setPorcupineKey(porcupineKey) }) {
                                Icon(Icons.Default.Save, contentDescription = "Save key", tint = WarmGold)
                            }
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "Get a free access key at console.picovoice.ai. " +
                    "To use 'Hey Guardian', download the keyword file and place it in the app's assets folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // ── Privacy & Security ───────────────────────────────────────
            SettingsSection(title = "Privacy & Security") {
                // AI Processing mode
                Text(
                    "AI Processing",
                    style = MaterialTheme.typography.labelLarge,
                    color = NavyBlue
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Choose where Guardian analyzes your messages:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))

                listOf(
                    Triple("AUTO", "Auto", "Try on-device first, use cloud if needed"),
                    Triple("ON", "On-Device Only", "Maximum privacy — no data leaves your phone"),
                    Triple("OFF", "Cloud Only", "Always use Claude AI in the cloud")
                ).forEach { (mode, label, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setPrivacyMode(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = state.privacyMode == mode,
                            onClick = { viewModel.setPrivacyMode(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = NavyBlue)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Conversation history
                ShieldToggleRow(
                    label = "Save conversation history",
                    isOn = state.saveHistory,
                    onToggle = { viewModel.setSaveHistory(it) }
                )
                Text(
                    "When OFF (default): chat stays in memory only and is deleted when you close the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Cloud message counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cloud messages today", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text(
                        "${state.cloudMessagesToday}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (state.cloudMessagesToday > 0) WarningAmber else SafeGreen
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Clear all data button
                if (showClearConfirm) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ScamRedLight),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "This will permanently delete all alerts, messages, and call logs from this device. Are you sure?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ScamRed
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showClearConfirm = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        viewModel.clearAllData()
                                        showClearConfirm = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = ScamRed)
                                ) {
                                    Text("Delete All", color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ScamRed)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ScamRed)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear All Local Data", style = MaterialTheme.typography.labelLarge, color = ScamRed)
                    }
                }

                if (clearDataResult == "cleared") {
                    Text(
                        "✅ All local data deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SafeGreen
                    )
                    LaunchedEffect(clearDataResult) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.dismissClearDataResult()
                    }
                }
            }

            // ── About ────────────────────────────────────────────────────
            SettingsSection(title = "About Guardian Angel") {
                Text(
                    "Guardian Angel v1.0\n\nDesigned with love to protect seniors from phone and text scams using AI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = SafeGreenLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "🔒 Our Privacy Promise",
                            style = MaterialTheme.typography.titleSmall,
                            color = SafeGreen
                        )
                        Text(
                            "• Your SMS and call content is NEVER stored on this device or any server.\n" +
                            "• We store only the AI's risk verdict (SAFE / WARNING / SCAM) — never the original message.\n" +
                            "• In On-Device mode, nothing leaves your phone.\n" +
                            "• In Cloud mode, messages are sent to Anthropic's Claude API for analysis. Anthropic does not train on API data.\n" +
                            "• Your API key is encrypted on-device using hardware-backed Android Keystore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeGreen
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = NavyBlue
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun TextSizeButton(
    label: String,
    previewSp: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) NavyBlue else Color.White
    val contentColor = if (isSelected) Color.White else NavyBlue
    Surface(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 0.dp else 1.5.dp,
            color = NavyBlue
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Aa",
                fontSize = previewSp.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                lineHeight = (previewSp * 1.2f).sp
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
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
                val dialogFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = InputBackground,
                    unfocusedContainerColor = InputBackground,
                    focusedBorderColor = NavyBlue,
                    unfocusedBorderColor = Color(0xFFBBBBBB),
                    focusedLabelColor = NavyBlue,
                    unfocusedLabelColor = TextSecondary,
                    cursorColor = NavyBlue
                )
                if (hasNickname) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Nickname (e.g. Daughter Sarah)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = dialogFieldColors
                    )
                }
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = dialogFieldColors
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
