package com.guardianangel.app.ui.onboarding

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.*
import com.guardianangel.app.ui.theme.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var page by remember { mutableIntStateOf(0) }
    var userName by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    val testResult by viewModel.testResult.collectAsStateWithLifecycle()

    val smsPermission = rememberPermissionState(android.Manifest.permission.RECEIVE_SMS)
    val phonePermission = rememberPermissionState(android.Manifest.permission.READ_PHONE_STATE)
    val micPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    val contactsPermission = rememberPermissionState(android.Manifest.permission.READ_CONTACTS)
    val notifPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    } else null

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WarmWhite
    ) {
        AnimatedContent(targetState = page, label = "onboarding_page") { currentPage ->
            when (currentPage) {
                0 -> WelcomePage(onNext = { page = 1 })
                1 -> PrivacyPage(onNext = { page = 2 })
                2 -> PermissionsPage(
                    smsPermission = smsPermission,
                    phonePermission = phonePermission,
                    micPermission = micPermission,
                    contactsPermission = contactsPermission,
                    notifPermission = notifPermission,
                    onNext = { page = 3 }
                )
                3 -> SetupPage(
                    userName = userName,
                    onUserNameChange = { userName = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it; viewModel.clearTestResult() },
                    testResult = testResult,
                    onTestConnection = { viewModel.testApiConnection(apiKey) },
                    onComplete = {
                        viewModel.clearTestResult()
                        viewModel.saveNameAndKey(userName, apiKey)
                        viewModel.completeOnboarding()
                        onComplete()
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Angel avatar
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(WarmGold),
            contentAlignment = Alignment.Center
        ) {
            Text("😇", fontSize = 60.sp)
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Welcome to\nGuardian Angel",
            style = MaterialTheme.typography.displayMedium,
            color = NavyBlue,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FeatureRow("🛡️", "Protects you from phone and text scams automatically")
                FeatureRow("💬", "Chat with your Guardian Angel any time you're unsure about something")
                FeatureRow("📞", "Screens suspicious calls before they reach you")
                FeatureRow("👨‍👩‍👧", "Alerts your family when something looks dangerous")
            }
        }

        Spacer(Modifier.height(40.dp))

        GuardianButton(text = "Let's Get Started", onClick = onNext)
    }
}

@Composable
private fun FeatureRow(emoji: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(emoji, fontSize = 24.sp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionsPage(
    smsPermission: PermissionState,
    phonePermission: PermissionState,
    micPermission: PermissionState,
    contactsPermission: PermissionState,
    notifPermission: PermissionState?,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        Text(
            text = "A few permissions needed",
            style = MaterialTheme.typography.headlineLarge,
            color = NavyBlue,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "These help Guardian Angel protect you. We never sell your data.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        PermissionCard(
            icon = "💬",
            title = "Text Messages (SMS)",
            description = "So Guardian can check if incoming texts might be scams",
            isGranted = smsPermission.status.isGranted,
            onRequest = { smsPermission.launchPermissionRequest() }
        )

        PermissionCard(
            icon = "📞",
            title = "Phone",
            description = "So Guardian can screen suspicious phone calls for you",
            isGranted = phonePermission.status.isGranted,
            onRequest = { phonePermission.launchPermissionRequest() }
        )

        PermissionCard(
            icon = "🎤",
            title = "Microphone",
            description = "So you can talk to Guardian Angel by voice instead of typing",
            isGranted = micPermission.status.isGranted,
            onRequest = { micPermission.launchPermissionRequest() }
        )

        PermissionCard(
            icon = "👥",
            title = "Contacts",
            description = "So Guardian knows your trusted contacts and doesn't flag them",
            isGranted = contactsPermission.status.isGranted,
            onRequest = { contactsPermission.launchPermissionRequest() }
        )

        if (notifPermission != null) {
            PermissionCard(
                icon = "🔔",
                title = "Notifications",
                description = "So Guardian can immediately warn you about scam attempts",
                isGranted = notifPermission.status.isGranted,
                onRequest = { notifPermission.launchPermissionRequest() }
            )
        }

        Spacer(Modifier.height(32.dp))

        GuardianButton(text = "Continue →", onClick = onNext)

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onNext) {
            Text(
                "Skip for now",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: String,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) SafeGreenLight else LightSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(icon, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = NavyBlue)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (isGranted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = SafeGreen)
            } else {
                OutlinedButton(
                    onClick = onRequest,
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Allow", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SetupPage(
    userName: String,
    onUserNameChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    testResult: String?,
    onTestConnection: () -> Unit,
    onComplete: () -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        Text(
            text = "Almost done! 🌟",
            style = MaterialTheme.typography.headlineLarge,
            color = NavyBlue
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Tell Guardian Angel a little about you",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = userName,
            onValueChange = onUserNameChange,
            label = { Text("Your first name", style = MaterialTheme.typography.bodyLarge) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Text("👤", fontSize = 20.sp) },
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
            )
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Claude API Key",
            style = MaterialTheme.typography.titleMedium,
            color = NavyBlue,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Guardian Angel uses Claude AI. Get your free API key at console.anthropic.com",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Paste your API key here", style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showApiKey = !showApiKey }) {
                    Icon(
                        if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showApiKey) "Hide key" else "Show key"
                    )
                }
            },
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
            )
        )

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onTestConnection,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = apiKey.isNotBlank() && testResult != "testing"
        ) {
            if (testResult == "testing") {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Testing connection…", style = MaterialTheme.typography.labelLarge)
            } else {
                Text("Test Connection", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (testResult != null && testResult != "testing") {
            Spacer(Modifier.height(8.dp))
            val (color, message) = when (testResult) {
                "success" -> Pair(SafeGreen, "✅ Connection successful! Guardian Angel is ready.")
                "invalid_key" -> Pair(ScamRed, "❌ Invalid API key. Please check and try again.")
                else -> Pair(WarningAmber, "⚠️ Could not connect. Please check your internet.")
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = color)
        }

        Spacer(Modifier.height(40.dp))

        GuardianButton(
            text = "Meet Your Guardian Angel 😇",
            onClick = onComplete,
            enabled = userName.isNotBlank()
        )

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onComplete) {
            Text(
                "Set up later",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun GuardianButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NavyBlue,
            contentColor = Color.White,
            disabledContainerColor = NavyBlue.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
