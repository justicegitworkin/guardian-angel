package com.guardianangel.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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

            // ── Shields ──────────────────────────────────────────────────
            SettingsSection(title = "Protection Shields") {
                ShieldToggleRow("💬 SMS Shield", state.isSmsShieldOn, viewModel::setSmsShield)
                ShieldToggleRow("📞 Call Shield", state.isCallShieldOn, viewModel::setCallShield)
                ShieldToggleRow("📧 Email Shield", state.isEmailShieldOn, viewModel::setEmailShield)
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

            // ── About ────────────────────────────────────────────────────
            SettingsSection(title = "About Guardian Angel") {
                Text(
                    "Guardian Angel v1.0\n\nDesigned to protect seniors from phone and text scams using AI. " +
                    "Your privacy is our priority — messages are analyzed by Claude AI and never stored by Anthropic beyond your session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
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
