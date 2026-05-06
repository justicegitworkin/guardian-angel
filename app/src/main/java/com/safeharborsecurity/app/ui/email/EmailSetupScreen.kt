package com.safeharborsecurity.app.ui.email

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.BuildConfig
import com.safeharborsecurity.app.data.local.entity.EmailAccountEntity
import com.safeharborsecurity.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailSetupScreen(
    onNavigateBack: () -> Unit,
    viewModel: EmailSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Gmail Sign-In launcher
    val gmailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleGmailSignInResult(result.data)
        } else {
            viewModel.handleGmailSignInResult(result.data)
        }
    }

    // Show toast messages and close sheet on success
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            if (message.contains("added")) {
                showAddSheet = false
            }
            viewModel.clearToast()
        }
    }

    // Close sheet when Gmail connects successfully
    LaunchedEffect(state.gmailState) {
        if (state.gmailState is EmailAccountState.Connected ||
            state.gmailState is EmailAccountState.Scanning) {
            showAddSheet = false
        }
    }

    if (showAddSheet) {
        AddEmailBottomSheet(
            onDismiss = { showAddSheet = false },
            onConnectGmail = {
                val intent = viewModel.getGmailSignInIntent()
                gmailLauncher.launch(intent)
            },
            onAddManual = { email, displayName, provider ->
                viewModel.addManualAccount(email, displayName, provider)
            },
            hasGmailAccount = state.accounts.any { it.provider == "GMAIL" },
            isGmailOAuthAvailable = state.isGmailOAuthAvailable
        )
    }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title = {
                    Text(
                        "Email Accounts",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = NavyBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Account", style = MaterialTheme.typography.labelLarge)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Gmail scan progress/result card
            val gmailState = state.gmailState
            if (gmailState !is EmailAccountState.Idle) {
                item {
                    GmailStatusCard(
                        state = gmailState,
                        onDismiss = viewModel::dismissGmailState
                    )
                }
            }

            if (state.accounts.isEmpty() && gmailState is EmailAccountState.Idle) {
                item {
                    EmptyState(onAddAccount = { showAddSheet = true })
                }
            } else if (state.accounts.isNotEmpty()) {
                item {
                    Text(
                        "Connected Accounts",
                        style = MaterialTheme.typography.titleMedium,
                        color = NavyBlue,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(state.accounts, key = { it.emailAddress }) { account ->
                    EmailAccountCard(
                        account = account,
                        onRemove = { viewModel.removeAccount(account.emailAddress) }
                    )
                }
            }

            // DEBUG: Gmail config diagnostics
            if (BuildConfig.DEBUG) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.checkGmailConfig() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Check Gmail Config", style = MaterialTheme.typography.labelLarge)
                        }

                        val diag = state.gmailConfigDiagnostics
                        if (diag != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = diag,
                                    modifier = Modifier.padding(12.dp),
                                    color = Color(0xFF00FF00),
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun GmailStatusCard(
    state: EmailAccountState,
    onDismiss: () -> Unit
) {
    val (icon, title, message, color, showDismiss) = when (state) {
        is EmailAccountState.Connecting -> StatusCardData(
            Icons.Default.Sync, "Connecting to Gmail...", "Please wait while we connect to your Google account.", NavyBlue, false
        )
        is EmailAccountState.Scanning -> StatusCardData(
            Icons.Default.Search, "Scanning your emails...", state.message, NavyBlue, false
        )
        is EmailAccountState.Connected -> {
            val hasWarning = state.summary.contains("dangerous", ignoreCase = true) ||
                    state.summary.contains("suspicious", ignoreCase = true)
            StatusCardData(
                if (hasWarning) Icons.Default.Warning else Icons.Default.CheckCircle,
                if (hasWarning) "Review needed" else "All done!",
                state.summary,
                if (hasWarning) WarningAmber else SafeGreen,
                true
            )
        }
        is EmailAccountState.Error -> StatusCardData(
            Icons.Default.ErrorOutline, "Something went wrong", state.message, ScamRed, true
        )
        else -> return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state is EmailAccountState.Connecting || state is EmailAccountState.Scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = NavyBlue,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (showDismiss) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
        }
    }
}

private data class StatusCardData(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val message: String,
    val color: Color,
    val showDismiss: Boolean
)

@Composable
private fun EmptyState(onAddAccount: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp)
    ) {
        Icon(
            Icons.Default.Email,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = NavyBlue.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "No email accounts connected",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Connect your email so Safe Companion can scan incoming messages for scams and keep you safe.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAddAccount,
            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Connect an Email Account", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

@Composable
private fun EmailAccountCard(
    account: EmailAccountEntity,
    onRemove: () -> Unit
) {
    var showConfirmRemove by remember { mutableStateOf(false) }

    if (showConfirmRemove) {
        AlertDialog(
            onDismissRequest = { showConfirmRemove = false },
            containerColor = WarmWhite,
            title = {
                Text("Remove this account?", style = MaterialTheme.typography.headlineSmall, color = NavyBlue)
            },
            text = {
                Text(
                    "Safe Companion will stop scanning emails from ${account.emailAddress}. You can add it back later.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = { onRemove(); showConfirmRemove = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ScamRed),
                    modifier = Modifier.height(52.dp)
                ) { Text("Remove", color = Color.White, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmRemove = false }, modifier = Modifier.height(52.dp)) {
                    Text("Keep", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (account.provider) {
                        "GMAIL" -> Icons.Default.Email
                        "OUTLOOK" -> Icons.Default.Mail
                        "YAHOO" -> Icons.Default.Mail
                        else -> Icons.Default.AlternateEmail
                    },
                    contentDescription = account.provider,
                    tint = NavyBlue,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(account.emailAddress, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Connected",
                    tint = if (account.isActive) SafeGreen else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = LightSurface)
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Emails scanned", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("${account.totalScanned}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                Column {
                    Text("Threats found", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(
                        "${account.threatsFound}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (account.threatsFound > 0) WarningAmber else SafeGreen
                    )
                }
                Column {
                    Text("Last scan", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(
                        if (account.lastSyncTime > 0) {
                            SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(account.lastSyncTime))
                        } else "Never",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showConfirmRemove = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ScamRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = ScamRed)
                Spacer(Modifier.width(8.dp))
                Text("Remove Account", style = MaterialTheme.typography.labelLarge, color = ScamRed)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEmailBottomSheet(
    onDismiss: () -> Unit,
    onConnectGmail: () -> Unit,
    onAddManual: (email: String, displayName: String, provider: String) -> Unit,
    hasGmailAccount: Boolean,
    isGmailOAuthAvailable: Boolean
) {
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showGmailSetupDialog by remember { mutableStateOf(false) }

    if (showGmailSetupDialog) {
        AlertDialog(
            onDismissRequest = { showGmailSetupDialog = false },
            containerColor = WarmWhite,
            title = {
                Text(
                    "Gmail Setup Needed",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NavyBlue
                )
            },
            text = {
                Text(
                    "Gmail connection requires a one-time setup step.\n\n" +
                    "If you are the app developer, please check the SETUP_REQUIRED " +
                    "instructions in the app folder and add a google-services.json file.\n\n" +
                    "If someone set this app up for you, please ask them to complete " +
                    "the Gmail configuration.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showGmailSetupDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WarmWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Connect an Email Account", style = MaterialTheme.typography.headlineSmall, color = NavyBlue)

            if (selectedProvider == null) {
                Text(
                    "Choose your email provider so Safe Companion can scan your inbox for scam emails.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )

                // Gmail
                if (hasGmailAccount) {
                    EmailProviderCard(
                        label = "Gmail (Google)",
                        description = "Already connected",
                        icon = Icons.Default.Email,
                        enabled = false,
                        buttonText = "Connected",
                        onClick = {}
                    )
                } else if (isGmailOAuthAvailable) {
                    // Full OAuth — google-services.json is present
                    EmailProviderCard(
                        label = "Gmail (Google)",
                        description = "Sign in with your Google account",
                        icon = Icons.Default.Email,
                        enabled = true,
                        buttonText = "Connect",
                        onClick = onConnectGmail
                    )
                } else {
                    // No google-services.json — show setup guidance, NOT manual form
                    EmailProviderCard(
                        label = "Gmail (Google)",
                        description = "Tap below to learn how to connect Gmail",
                        icon = Icons.Default.Email,
                        enabled = true,
                        buttonText = "Setup",
                        onClick = { showGmailSetupDialog = true }
                    )
                }

                // Outlook — coming soon placeholder
                // Outlook OAuth requires Azure app registration (portal.azure.com):
                // 1. Register app in Azure Active Directory
                // 2. Add Mail.Read API permission
                // 3. Set redirect URI: msauth://com.safeharborsecurity.app
                // 4. Copy client ID into msal_config.json
                EmailProviderCard(
                    label = "Outlook (Microsoft)",
                    description = "Coming soon — in setup",
                    icon = Icons.Default.Mail,
                    enabled = false,
                    buttonText = "Soon",
                    onClick = {}
                )

                // Yahoo — manual entry
                EmailProviderCard(
                    label = "Yahoo Mail",
                    description = "Add your email address for notification scanning",
                    icon = Icons.Default.Mail,
                    enabled = true,
                    buttonText = "Add",
                    onClick = { selectedProvider = "YAHOO" }
                )

                // Other — manual entry
                EmailProviderCard(
                    label = "Other Email",
                    description = "Add any email address for notification scanning",
                    icon = Icons.Default.AlternateEmail,
                    enabled = true,
                    buttonText = "Add",
                    onClick = { selectedProvider = "IMAP" }
                )
            } else {
                val providerName = when (selectedProvider) {
                    "GMAIL" -> "Gmail"
                    "YAHOO" -> "Yahoo"
                    else -> "Email"
                }

                Text("Enter your $providerName details", style = MaterialTheme.typography.titleMedium, color = NavyBlue)

                Text(
                    "Safe Companion will watch for new email notifications from this account and scan them for scams.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Your name") },
                    placeholder = { Text("e.g. Margaret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address") },
                    placeholder = {
                        Text(
                            when (selectedProvider) {
                                "GMAIL" -> "e.g. margaret@gmail.com"
                                "YAHOO" -> "e.g. margaret@yahoo.com"
                                else -> "e.g. margaret@example.com"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { selectedProvider = null; email = ""; displayName = "" },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Back", style = MaterialTheme.typography.labelLarge) }

                    Button(
                        onClick = { onAddManual(email, displayName, selectedProvider!!) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        enabled = email.isNotBlank() && displayName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Add Account", style = MaterialTheme.typography.labelLarge, color = Color.White) }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EmailProviderCard(
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color.White else LightSurface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 1.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) NavyBlue else TextSecondary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) TextPrimary else TextSecondary
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyBlue,
                    disabledContainerColor = LightSurface
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    buttonText,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 13.sp
                )
            }
        }
    }
}
