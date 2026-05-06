package com.safeharborsecurity.app.ui.security

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdditionalSecurityScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdditionalSecurityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.schedulePeriodicCheck()
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
                        "Additional Security",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Intro text
            Text(
                "Connect additional security services to keep yourself safe online. " +
                "These services can check if your personal information has been leaked.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            // ── HaveIBeenPwned (Tier 1 — prominent) ─────────────────
            val hibpService = state.services.find { it.serviceId == "hibp" }
            HibpCard(
                hibpApiKey = state.hibpApiKey,
                service = hibpService,
                onApiKeyChanged = { viewModel.setHibpApiKey(it) },
                onCheckNow = { viewModel.checkNow() }
            )

            // ── Credit Karma ────────────────────────────────────────
            ServiceCard(
                serviceName = "Credit Karma",
                description = "Free credit monitoring — highly recommended for keeping track of your credit score and spotting unusual activity.",
                isConnected = false,
                actionLabel = "Open Credit Karma",
                onAction = {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.creditkarma.mobile")
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        val playStoreIntent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=com.creditkarma.mobile"))
                        context.startActivity(playStoreIntent)
                    }
                }
            )

            // ── Google Dark Web Report ──────────────────────────────
            ServiceCard(
                serviceName = "Google Dark Web Report",
                description = "Free with your Google account. Check if your personal information appears on the dark web.",
                isConnected = false,
                actionLabel = "Check Now",
                onAction = {
                    val customTabsIntent = CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .build()
                    customTabsIntent.launchUrl(context, Uri.parse("https://myaccount.google.com/security"))
                }
            )

            // ── Experian ────────────────────────────────────────────
            ServiceCard(
                serviceName = "Experian",
                description = "Credit monitoring and dark web alerts. Helps you stay on top of your financial safety.",
                isConnected = false,
                actionLabel = "Learn More",
                onAction = {
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.experian.android"))
                    context.startActivity(intent)
                }
            )

            // ── LifeLock ────────────────────────────────────────────
            ServiceCard(
                serviceName = "LifeLock",
                description = "Identity theft protection. Monitors your personal information and alerts you to potential threats.",
                isConnected = false,
                actionLabel = "Learn More",
                onAction = {
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.symantec.mobile.m360"))
                    context.startActivity(intent)
                }
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun HibpCard(
    hibpApiKey: String,
    service: com.safeharborsecurity.app.data.local.entity.ConnectedServiceEntity?,
    onApiKeyChanged: (String) -> Unit,
    onCheckNow: () -> Unit
) {
    var localApiKey by remember(hibpApiKey) { mutableStateOf(hibpApiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(2.dp, NavyBlue)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "HaveIBeenPwned",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NavyBlue,
                    modifier = Modifier.weight(1f)
                )
                StatusIndicator(isConnected = service?.isConnected == true)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Check if your email appeared in data leaks. This service scans known data breaches to see if your information was exposed.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(12.dp))

            // API key input
            OutlinedTextField(
                value = localApiKey,
                onValueChange = { localApiKey = it },
                label = { Text("HaveIBeenPwned API Key") },
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
                        IconButton(onClick = { onApiKeyChanged(localApiKey) }) {
                            Icon(Icons.Default.Save, contentDescription = "Save key", tint = WarmGold)
                        }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "You can get a free API key from haveibeenpwned.com/API/Key",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(12.dp))

            // Check Now button
            Button(
                onClick = {
                    if (localApiKey != hibpApiKey) {
                        onApiKeyChanged(localApiKey)
                    }
                    onCheckNow()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = localApiKey.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Check Now", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }

            // Last check result
            if (service != null && service.resultSummary.isNotBlank()) {
                Spacer(Modifier.height(12.dp))

                val resultColor = when {
                    service.resultSummary.contains("safe", ignoreCase = true) -> SafeGreen
                    service.resultSummary.contains("leak", ignoreCase = true) ||
                    service.resultSummary.contains("Found", ignoreCase = true) -> ScamRed
                    service.resultSummary.contains("invalid", ignoreCase = true) -> WarningAmber
                    else -> TextSecondary
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = resultColor.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Last Result",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = resultColor
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            service.resultSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        if (service.lastSyncTime > 0) {
                            Spacer(Modifier.height(4.dp))
                            val dateFormat = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
                            Text(
                                "Checked: ${dateFormat.format(Date(service.lastSyncTime))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(
    serviceName: String,
    description: String,
    isConnected: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    lastCheckTime: Long? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    serviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NavyBlue,
                    modifier = Modifier.weight(1f)
                )
                StatusIndicator(isConnected = isConnected)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            if (lastCheckTime != null && lastCheckTime > 0) {
                Spacer(Modifier.height(4.dp))
                val dateFormat = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
                Text(
                    "Last checked: ${dateFormat.format(Date(lastCheckTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StatusIndicator(isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = if (isConnected) SafeGreen else TextSecondary.copy(alpha = 0.4f),
                    shape = CircleShape
                )
        )
        Text(
            if (isConnected) "Connected" else "Not connected",
            style = MaterialTheme.typography.bodySmall,
            color = if (isConnected) SafeGreen else TextSecondary
        )
    }
}
