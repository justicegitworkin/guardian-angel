package com.safeharborsecurity.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidedPermissionScreen(
    onComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: GuidedPermissionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check permissions when returning from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-advance when permission is granted
    val currentStep = state.steps.getOrNull(state.currentStep)
    val isCurrentGranted = currentStep?.let { state.grantedMap[it.id] } ?: false
    LaunchedEffect(isCurrentGranted, state.currentStep) {
        if (isCurrentGranted && !state.isComplete) {
            delay(600) // Show green checkmark briefly
            viewModel.advanceToNext()
        }
    }

    // Runtime permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        currentStep?.let { viewModel.refreshStep(it.id) }
    }

    if (state.isComplete) {
        CompletionScreen(
            grantedCount = viewModel.countGranted(),
            totalCount = state.steps.size,
            onDone = onComplete
        )
        return
    }

    if (state.steps.isEmpty() || currentStep == null) return

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress bar
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (state.currentStep + 1).toFloat() / state.steps.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = NavyBlue,
                trackColor = Color(0xFFE0E0E0)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Step ${state.currentStep + 1} of ${state.steps.size}",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(Modifier.height(32.dp))

            // Large icon in colored circle
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(currentStep.iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentGranted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Already granted",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(64.dp)
                    )
                } else {
                    Text(
                        currentStep.icon,
                        fontSize = 56.sp
                    )
                }
            }

            // Green badge if already granted
            if (isCurrentGranted) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFE8F5E9)
                ) {
                    Text(
                        "Already set up \u2713",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Title
            Text(
                currentStep.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = NavyBlue,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Explanation
            Text(
                currentStep.explanation,
                fontSize = 18.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )

            Spacer(Modifier.height(20.dp))

            // Reassurance card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF558B2F),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        currentStep.reassurance,
                        fontSize = 15.sp,
                        color = Color(0xFF33691E),
                        lineHeight = 22.sp
                    )
                }
            }

            // Notification listener special guidance
            if (currentStep.id == "notification_listener" && !isCurrentGranted) {
                Spacer(Modifier.height(16.dp))
                NotificationListenerGuidanceCard()
            }

            // Usage Access special guidance (replaced the prior accessibility step)
            if (currentStep.id == "usage_access" && !isCurrentGranted) {
                Spacer(Modifier.height(16.dp))
                UsageAccessGuidanceCard()
            }

            Spacer(Modifier.height(32.dp))

            // Action button
            if (!isCurrentGranted) {
                Button(
                    onClick = {
                        when (currentStep.actionType) {
                            PermissionActionType.RUNTIME -> {
                                currentStep.manifestPermission?.let {
                                    permissionLauncher.launch(it)
                                } ?: viewModel.advanceToNext()
                            }
                            PermissionActionType.SETTINGS_DEEP_LINK -> {
                                val intent = Intent(currentStep.settingsAction)
                                context.startActivity(intent)
                            }
                            PermissionActionType.BATTERY_OPTIMIZATION -> {
                                val intent = Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = currentStep.iconColor)
                ) {
                    Text(
                        currentStep.buttonText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = { viewModel.advanceToNext() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                ) {
                    Text(
                        "Continue \u2192",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Skip link
            if (!isCurrentGranted) {
                TextButton(onClick = { viewModel.advanceToNext() }) {
                    Text(
                        "Skip for now",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun NotificationListenerGuidanceCard() {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val extraTip = when {
        manufacturer.contains("samsung") ->
            "On Samsung phones, look for \"Device care\" or \"Advanced\" before finding Notification access."
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
            "On Xiaomi phones, also check \"Special app access\" in your phone settings."
        manufacturer.contains("huawei") ->
            "On Huawei phones, also check \"Apps\" then \"Special access\" in your phone settings."
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "What to look for in Settings:",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "1. Find \"Safe Harbor Security\" in the list\n" +
                "2. Tap on it\n" +
                "3. Turn the switch ON",
                fontSize = 16.sp,
                color = Color(0xFF795548),
                lineHeight = 26.sp
            )
            if (extraTip != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    extraTip,
                    fontSize = 14.sp,
                    color = Color(0xFF795548),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun UsageAccessGuidanceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "What to look for in Settings:",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6A1B9A)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "1. Scroll down to find \"Safe Companion\"\n" +
                "2. Tap on it\n" +
                "3. Turn \"Permit usage access\" ON\n" +
                "4. Press Back to return here",
                fontSize = 16.sp,
                color = Color(0xFF4A148C),
                lineHeight = 26.sp
            )
        }
    }
}

@Composable
private fun CompletionScreen(
    grantedCount: Int,
    totalCount: Int,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmWhite)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\uD83D\uDEE1\uFE0F", fontSize = 80.sp) // 🛡️

        Spacer(Modifier.height(24.dp))

        Text(
            "Safe Harbor is ready!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = NavyBlue,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "You are now protected.",
            fontSize = 20.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFE8F5E9)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "$grantedCount of $totalCount",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    "protections active",
                    fontSize = 18.sp,
                    color = Color(0xFF388E3C)
                )
            }
        }

        if (grantedCount < totalCount) {
            Spacer(Modifier.height(12.dp))
            Text(
                "You can set up the rest any time\nfrom Settings.",
                fontSize = 15.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
        ) {
            Text(
                "Start Using Safe Harbor",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
