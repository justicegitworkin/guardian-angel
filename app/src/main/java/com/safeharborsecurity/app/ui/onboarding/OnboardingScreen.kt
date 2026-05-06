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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import com.safeharborsecurity.app.BuildConfig
import com.safeharborsecurity.app.ui.lock.PinSetupScreen
import com.safeharborsecurity.app.ui.theme.*
import kotlinx.coroutines.delay

// Total onboarding pages: 0=Welcome, 1=WhoFor, 2=Name, 3=ApiKey,
// 4=Permissions walkthrough (8 sub-steps), 5=FamilyContacts, 6=PinSetup, 7=TestDrive
private const val TOTAL_PAGES = 8

// Permission step definitions
private enum class PermStepType { RUNTIME, SETTINGS_SPECIAL, SETTINGS_BATTERY }

private data class PermStep(
    val id: String,
    val icon: ImageVector,
    val iconColor: Color,
    val emoji: String,
    val title: String,
    val explanation: String,
    val reassurance: String,
    val buttonText: String,
    val permType: PermStepType,
    val manifestPermission: String? = null,
    val settingsAction: String? = null
)

private val PERM_STEPS = listOf(
    PermStep(
        id = "microphone",
        icon = Icons.Default.Mic,
        iconColor = Color(0xFF00897B),
        emoji = "🎤",
        title = "Talk to Safe Companion",
        explanation = "Safe Companion needs your microphone so you can talk to it using your voice instead of typing.",
        reassurance = "The microphone is only used when you are actively using Safe Companion.",
        buttonText = "Allow Microphone",
        permType = PermStepType.RUNTIME,
        manifestPermission = android.Manifest.permission.RECORD_AUDIO
    ),
    PermStep(
        id = "notifications",
        icon = Icons.Default.Notifications,
        iconColor = Color(0xFFE65100),
        emoji = "🔔",
        title = "Get Safety Alerts",
        explanation = "Safe Companion needs to send you notifications so it can warn you about scams immediately.",
        reassurance = "We only send important alerts — we won't spam you.",
        buttonText = "Allow Notifications",
        permType = PermStepType.RUNTIME,
        manifestPermission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.POST_NOTIFICATIONS else null
    ),
    // Item 2: Single combined opt-in that replaces the two intimidating
    // restricted-setting flows (notification listener + usage access). The
    // MediaProjection consent dialog Android shows is friendly: "Safe Companion
    // will start capturing what's displayed on your screen." That's it.
    // ScreenScanService picks it up from there.
    PermStep(
        id = "screen_monitor",
        icon = Icons.Default.Visibility,
        iconColor = Color(0xFF1565C0),
        emoji = "👀",
        title = "Watch My Screen for Scams",
        explanation = "Safe Companion can quickly look at your screen when a text or payment app is open, " +
            "to warn you about scams. The pictures of your screen never leave this phone.",
        reassurance = "Nothing is sent over the internet. Safe Companion looks at the words on your " +
            "screen using its own brain, then throws the picture away. You can turn this off any time.",
        buttonText = "Allow Screen Monitor",
        permType = PermStepType.SETTINGS_SPECIAL,
        settingsAction = "com.safeharborsecurity.app.action.SCREEN_MONITOR_CONSENT"
    ),
    PermStep(
        id = "camera",
        icon = Icons.Default.CameraAlt,
        iconColor = Color(0xFF00695C),
        emoji = "📷",
        title = "Check Suspicious Photos",
        explanation = "Safe Companion can use your camera to scan QR codes and check suspicious images for scams.",
        reassurance = "The camera is only used when you ask Safe Companion to check something.",
        buttonText = "Allow Camera",
        permType = PermStepType.RUNTIME,
        manifestPermission = android.Manifest.permission.CAMERA
    ),
    PermStep(
        id = "contacts",
        icon = Icons.Default.Contacts,
        iconColor = Color(0xFF2E7D32),
        emoji = "📇",
        title = "Recognise Your Contacts",
        explanation = "Safe Companion can check if a caller or texter is in your address book, so it knows who is safe.",
        reassurance = "Your contacts stay on your phone. We never upload them anywhere.",
        buttonText = "Allow Contacts",
        permType = PermStepType.RUNTIME,
        manifestPermission = android.Manifest.permission.READ_CONTACTS
    ),
    PermStep(
        id = "phone",
        icon = Icons.Default.Phone,
        iconColor = Color(0xFF1565C0),
        emoji = "📞",
        title = "Screen Incoming Calls",
        explanation = "Safe Companion needs access to your calls so it can warn you about scam callers.",
        reassurance = "Safe Companion never records your calls. It only checks for warning signs.",
        buttonText = "Allow Phone",
        permType = PermStepType.RUNTIME,
        manifestPermission = android.Manifest.permission.READ_PHONE_STATE
    ),
    PermStep(
        id = "battery",
        icon = Icons.Default.BatteryChargingFull,
        iconColor = Color(0xFFF57C00),
        emoji = "🔋",
        title = "Stay Alert in the Background",
        explanation = "Safe Companion needs to stay running in the background so it can protect you even when you're not using the app.",
        reassurance = "This uses very little battery. Safe Companion is designed to be efficient.",
        buttonText = "Allow Background Activity",
        permType = PermStepType.SETTINGS_BATTERY
    )
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var page by remember { mutableIntStateOf(0) }
    var userName by remember { mutableStateOf("") }
    // If the build embeds an Anthropic key (set in local.properties as
    // safe.companion.anthropic.api.key=...), pre-fill it so the onboarding
    // flow can skip the "Connect Safe Companion's brain" page entirely.
    val bakedKey = BuildConfig.DEFAULT_ANTHROPIC_API_KEY
    val hasBakedKey = bakedKey.isNotBlank()
    var apiKey by remember { mutableStateOf(bakedKey) }
    var isForFamily by remember { mutableStateOf(false) }
    // Permission walkthrough sub-step index (0..7)
    var permStep by remember { mutableIntStateOf(0) }

    val testResult by viewModel.testResult.collectAsStateWithLifecycle()

    // Calculate progress: pages 0-3 are 1-4, page 4 (perms) shows sub-steps as 5-12, pages 5-7 are 13-15
    val totalSteps = 3 + PERM_STEPS.size + 3  // welcome(skip) + 3 + 8 perms + 3
    val currentStep = when {
        page <= 3 -> page
        page == 4 -> 4 + permStep
        else -> 4 + PERM_STEPS.size + (page - 5)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WarmWhite
    ) {
        // Tester feedback (item 1): on edge-to-edge devices the camera notch
        // was overlapping the title text and the bottom button area was
        // catching the system gesture-navigation strip. windowInsetsPadding
        // with WindowInsets.safeDrawing reserves space for the status bar at
        // top, navigation bar at bottom, and any cutouts on the sides.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (page > 0) {
                ProgressBar(
                    current = currentStep,
                    total = totalSteps,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            AnimatedContent(targetState = page, label = "onboarding") { currentPage ->
                when (currentPage) {
                    0 -> WelcomePage(onNext = { page = 1 })
                    1 -> WhoIsThisForPage(
                        onSelf = { isForFamily = false; page = 2 },
                        onFamily = { isForFamily = true; viewModel.setSetupForFamily(true); page = 2 }
                    )
                    2 -> NamePage(
                        userName = userName,
                        onUserNameChange = { userName = it },
                        isForFamily = isForFamily,
                        onNext = {
                            if (hasBakedKey) {
                                // Build embeds an API key — save name + baked key
                                // and skip the "Connect Safe Companion's brain" page.
                                viewModel.saveNameAndKey(userName, bakedKey)
                                permStep = 0
                                page = 4
                            } else {
                                page = 3
                            }
                        },
                        onBack = { page = 1 }
                    )
                    3 -> ApiKeyPage(
                        apiKey = apiKey,
                        onApiKeyChange = { apiKey = it; viewModel.clearTestResult() },
                        testResult = testResult,
                        onTestConnection = { viewModel.testApiConnection(apiKey) },
                        onNext = {
                            viewModel.saveNameAndKey(userName, apiKey)
                            permStep = 0
                            page = 4
                        },
                        onBack = { page = 2 }
                    )
                    4 -> PermissionWalkthroughPage(
                        stepIndex = permStep,
                        totalSteps = PERM_STEPS.size,
                        step = PERM_STEPS[permStep],
                        onNext = {
                            if (permStep < PERM_STEPS.size - 1) {
                                permStep++
                            } else {
                                page = 5
                            }
                        },
                        onBack = {
                            // If the build had a baked key, page 3 (ApiKey) was
                            // skipped — back goes all the way to NamePage.
                            if (permStep > 0) permStep-- else page = if (hasBakedKey) 2 else 3
                        },
                        onSkip = {
                            if (permStep < PERM_STEPS.size - 1) {
                                permStep++
                            } else {
                                page = 5
                            }
                        }
                    )
                    5 -> FamilyContactPage(
                        viewModel = viewModel,
                        onNext = { page = 6 },
                        onBack = { permStep = PERM_STEPS.size - 1; page = 4 }
                    )
                    6 -> PinSetupScreen(
                        onPinSet = { pin ->
                            viewModel.setupPin(pin)
                            page = 7
                        },
                        onSkip = { page = 7 }
                    )
                    7 -> TestDrivePage(
                        userName = userName,
                        onComplete = {
                            viewModel.completeOnboarding()
                            onComplete()
                        }
                    )
                }
            }
        }
    }
}

// ─── Permission Walkthrough Page ─────────────────────────────────────────────

@Composable
private fun PermissionWalkthroughPage(
    stepIndex: Int,
    totalSteps: Int,
    step: PermStep,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    // Track whether this specific permission is granted
    var isGranted by remember(step.id) { mutableStateOf(checkPermission(context, step)) }
    // Track whether user has navigated to Settings for this step
    var hasNavigatedToSettings by remember(step.id) { mutableStateOf(false) }
    // Track whether we showed the green checkmark (for auto-advance delay)
    var showingCheckmark by remember(step.id) { mutableStateOf(false) }

    // Item 2 (Play-policy posture): show the in-app disclosure dialog before
    // we ever fire the system MediaProjection consent dialog. Google explicitly
    // rewards "prominent in-app disclosure" preceding sensitive permission
    // requests, and this is the friendliest place for it for older users.
    var showScreenMonitorDisclosure by remember(step.id) { mutableStateOf(false) }

    // Runtime permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
        if (granted) {
            showingCheckmark = true
        }
    }

    // Item 2: Screen-monitor MediaProjection consent launcher. Returns OK if
    // the user accepts Android's "Safe Companion will start capturing what's
    // displayed" dialog. On OK we mirror the state into SharedPreferences (so
    // checkPermission() can see it) and kick off ScreenScanService.
    val screenMonitorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            context.getSharedPreferences("safeharbor_runtime", 0)
                .edit().putBoolean("screen_monitor_active", true).apply()
            com.safeharborsecurity.app.service.ScreenScanService.startWithProjection(
                context, result.resultCode, result.data!!
            )
            isGranted = true
            showingCheckmark = true
        } else {
            // User said "Cancel" on the system dialog — leave step as un-granted
            // but unblocked. The user can move on with the Skip button.
            isGranted = false
        }
    }

    // Disclosure overlay: rendered above the standard step content when the
    // user taps "Allow Screen Monitor". Acts as our explicit, written, opt-in
    // gate before the system dialog ever appears.
    if (showScreenMonitorDisclosure) {
        ScreenMonitorDisclosureDialog(
            onCancel = { showScreenMonitorDisclosure = false },
            onContinue = {
                showScreenMonitorDisclosure = false
                try {
                    val mpm = context.getSystemService(
                        android.content.Context.MEDIA_PROJECTION_SERVICE
                    ) as android.media.projection.MediaProjectionManager

                    // Android 14 (API 34) added the "A single app" option to
                    // the MediaProjection consent dialog, and Google
                    // unhelpfully made it the default. For Safe Companion's
                    // scam-detection use case, single-app capture is useless —
                    // we don't know which app the scam will appear in. So we
                    // explicitly request full-display capture via
                    // MediaProjectionConfig.createConfigForDefaultDisplay().
                    // The system dialog then only offers "Entire screen" with
                    // no single-app option to mis-tap.
                    //
                    // Pre-Android-14 devices don't have the "single app" mode
                    // at all — they always captured the entire display — so
                    // the older API path needs no special handling.
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val config = android.media.projection.MediaProjectionConfig
                            .createConfigForDefaultDisplay()
                        mpm.createScreenCaptureIntent(config)
                    } else {
                        mpm.createScreenCaptureIntent()
                    }
                    screenMonitorLauncher.launch(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        "Couldn't open the screen-monitor dialog. Tap Skip and try " +
                            "again from Settings later.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    // Fix 27: Lifecycle observer to recheck permission on resume (returning from Settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, step.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = checkPermission(context, step)
                isGranted = nowGranted
                if (hasNavigatedToSettings && nowGranted) {
                    showingCheckmark = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-advance after showing green checkmark for 800ms
    LaunchedEffect(showingCheckmark) {
        if (showingCheckmark) {
            delay(800)
            onNext()
        }
    }

    // Auto-skip notification permission step on Android < 33
    LaunchedEffect(step.id) {
        if (step.id == "notifications" && Build.VERSION.SDK_INT < 33) {
            onNext()
        }
    }

    // Auto-advance if already granted on first composition
    LaunchedEffect(step.id) {
        if (isGranted && !showingCheckmark) {
            showingCheckmark = true
        }
    }

    // Choose the right content based on step type
    when (step.id) {
        "notification_listener" -> NotificationListenerStep(
            step = step,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            isGranted = isGranted,
            hasNavigatedToSettings = hasNavigatedToSettings,
            showingCheckmark = showingCheckmark,
            onOpenSettings = {
                hasNavigatedToSettings = true
                try {
                    context.startActivity(Intent(step.settingsAction))
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            onNext = onNext,
            onBack = onBack,
            onSkip = onSkip
        )
        else -> StandardPermissionStep(
            step = step,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            isGranted = isGranted,
            hasNavigatedToSettings = hasNavigatedToSettings,
            showingCheckmark = showingCheckmark,
            onRequestPermission = {
                when (step.permType) {
                    PermStepType.RUNTIME -> {
                        val perm = step.manifestPermission
                        if (perm != null) {
                            permissionLauncher.launch(perm)
                        } else {
                            onNext()
                        }
                    }
                    PermStepType.SETTINGS_SPECIAL -> {
                        if (step.id == "screen_monitor") {
                            // Item 2 — Show our in-app disclosure dialog FIRST.
                            // The user has to read the privacy story and tick
                            // "I understand" before we ever touch the system
                            // MediaProjection dialog. This is the
                            // "prominent in-app disclosure" Google's Sensitive
                            // Permissions policy explicitly rewards.
                            showScreenMonitorDisclosure = true
                        } else {
                            hasNavigatedToSettings = true
                            try {
                                context.startActivity(Intent(step.settingsAction))
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        }
                    }
                    PermStepType.SETTINGS_BATTERY -> {
                        hasNavigatedToSettings = true
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                }
            },
            onNext = onNext,
            onBack = onBack,
            onSkip = onSkip
        )
    }
}

// ─── Standard Permission Step ────────────────────────────────────────────────

@Composable
private fun StandardPermissionStep(
    step: PermStep,
    stepIndex: Int,
    totalSteps: Int,
    isGranted: Boolean,
    hasNavigatedToSettings: Boolean,
    showingCheckmark: Boolean,
    onRequestPermission: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 48.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            "Permission ${stepIndex + 1} of $totalSteps",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))

        // Large icon in colored circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(step.iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (showingCheckmark && isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    modifier = Modifier.size(60.dp),
                    tint = SafeGreen
                )
            } else {
                Text(step.emoji, fontSize = 52.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Green badge if already granted
        if (isGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SafeGreenLight),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(18.dp))
                    Text("Already set up", style = MaterialTheme.typography.labelLarge, color = SafeGreen)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            step.title,
            style = MaterialTheme.typography.headlineMedium,
            color = NavyBlue,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            step.explanation,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        // Reassurance card
        Card(
            colors = CardDefaults.cardColors(containerColor = SafeGreenLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(20.dp))
                Text(step.reassurance, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }

        // Instructions for Settings-based steps
        if (step.permType == PermStepType.SETTINGS_SPECIAL) {
            Spacer(Modifier.height(16.dp))
            SettingsInstructionCard(step)
        }

        Spacer(Modifier.height(24.dp))

        // Fix 27: Button logic — three states
        PermissionButtons(
            isGranted = isGranted,
            hasNavigatedToSettings = hasNavigatedToSettings,
            onRequestPermission = onRequestPermission,
            buttonText = step.buttonText,
            onNext = onNext,
            onSkip = onSkip,
            onBack = onBack
        )

        Spacer(Modifier.height(16.dp))
    }
}

// ─── Notification Listener Step (Fix 29) ─────────────────────────────────────

@Composable
private fun NotificationListenerStep(
    step: PermStep,
    stepIndex: Int,
    totalSteps: Int,
    isGranted: Boolean,
    hasNavigatedToSettings: Boolean,
    showingCheckmark: Boolean,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 48.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            "Permission ${stepIndex + 1} of $totalSteps",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(12.dp))

        // Icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(step.iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (showingCheckmark && isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    modifier = Modifier.size(52.dp),
                    tint = SafeGreen
                )
            } else {
                Text(step.emoji, fontSize = 44.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (isGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SafeGreenLight),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(18.dp))
                    Text("Already set up", style = MaterialTheme.typography.labelLarge, color = SafeGreen)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            step.title,
            style = MaterialTheme.typography.headlineSmall,
            color = NavyBlue,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            step.explanation,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        // Privacy reassurance card (prominent — Fix 29)
        Card(
            colors = CardDefaults.cardColors(containerColor = SafeGreenLight),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(24.dp))
                Text(
                    step.reassurance,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Important note
        Card(
            colors = CardDefaults.cardColors(containerColor = WarningAmberLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚠️", fontSize = 18.sp)
                Text(
                    "This is a special permission. Android will take you to a Settings screen — Safe Companion will be listed there. You need to turn it ON.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Numbered sub-steps with visual hints (Fix 29)
        Card(
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Step by step:",
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyBlue,
                    fontWeight = FontWeight.Bold
                )
                StepText("1", "Tap \"Open Notification Access\" below")
                StepWithHint("2", "Find \"Safe Companion\" in the list",
                    "Look for 🛡️ Safe Companion in the list of apps")
                StepWithHint("3", "Tap Safe Companion and turn the toggle ON",
                    "The toggle switch should change to blue/green")
                StepWithHint("4", "Tap \"Allow\" on the confirmation dialog",
                    "Android will ask if you're sure — tap Allow")
                StepText("5", "Press Back to return to Safe Companion")
            }
        }

        // Manufacturer-specific tips (Fix 29)
        val manufacturerNote = getManufacturerNote()
        if (manufacturerNote != null) {
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📱", fontSize = 16.sp)
                    Text(
                        manufacturerNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Buttons
        PermissionButtons(
            isGranted = isGranted,
            hasNavigatedToSettings = hasNavigatedToSettings,
            onRequestPermission = onOpenSettings,
            buttonText = step.buttonText,
            onNext = onNext,
            onSkip = onSkip,
            onBack = onBack
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "You can also forward suspicious messages to Safe Companion using your phone's Share button.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))
    }
}

// ─── Shared Button Row (Fix 27) ─────────────────────────────────────────────

@Composable
private fun PermissionButtons(
    isGranted: Boolean,
    hasNavigatedToSettings: Boolean,
    onRequestPermission: () -> Unit,
    buttonText: String,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    if (isGranted) {
        // Permission granted — show green Continue
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SafeGreen,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Permission Granted — Continue", style = MaterialTheme.typography.titleMedium)
        }
    } else if (hasNavigatedToSettings) {
        // Returned from Settings but not granted. We used to show both
        // "Continue Anyway" and "Try Again" here, but that's two decisions in
        // a row when the user has already made the meaningful choice (they
        // came back without granting). Single primary action now, with a
        // small hint that the permission can be re-granted from Settings
        // any time.
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00897B),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.ArrowForward, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Continue", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(6.dp))

        Text(
            "You can turn this on later in your phone's Settings if you want.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        // Initial state — show request button
        SafeHarborButton(text = buttonText, onClick = onRequestPermission)
    }

    Spacer(Modifier.height(8.dp))

    if (!isGranted && !hasNavigatedToSettings) {
        TextButton(onClick = onSkip) {
            Text("Skip for now", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
        }
    }

    TextButton(onClick = onBack) {
        Text("Back", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

// ─── Settings Instruction Card ───────────────────────────────────────────────

@Composable
private fun SettingsInstructionCard(step: PermStep) {
    val instructions = when (step.id) {
        "usage_access" -> listOf(
            "1. Tap the button below to open Usage Access",
            "2. Find \"Safe Companion\" in the list",
            "3. Turn the toggle ON",
            "4. Press Back to return here"
        )
        else -> listOf(
            "1. Tap the button below to open Settings",
            "2. Find and enable the permission",
            "3. Press Back to return here"
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            instructions.forEach { instruction ->
                val parts = instruction.split(". ", limit = 2)
                if (parts.size == 2) {
                    StepText(parts[0].replace(".", ""), parts[1])
                } else {
                    Text(instruction, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ─── Item 2: Screen-monitor disclosure dialog ───────────────────────────────
//
// Full-screen, written-in-plain-English explanation of what MediaProjection
// will do, with an "I understand" checkbox the user must tick before the
// Continue button enables. This sits between the onboarding card and the
// Android system consent dialog.
//
// The wording is deliberately concrete — Google policy reviewers (and AARP
// users) both prefer "what / when / where it goes / how to stop" over generic
// "we respect your privacy" language.

@Composable
private fun ScreenMonitorDisclosureDialog(
    onCancel: () -> Unit,
    onContinue: () -> Unit
) {
    var understood by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp),
            color = WarmWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))

                Text("👀", fontSize = 60.sp)
                Spacer(Modifier.height(16.dp))

                Text(
                    "Before we turn on Screen Monitor",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NavyBlue,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(20.dp))

                // Concrete bullet list of what's going to happen. AARP users
                // told us "tell me exactly what it does" beats reassurance.
                // Item 2: condensed disclosure — removed the "What we check for"
                // bullet (duplicates the page title) and "What you'll see next"
                // (the Android dialog itself explains that). Four bullets fit
                // above the checkbox + Continue without scrolling on most
                // phones, so AARP testers don't have to scroll just to approve.
                DisclosureBullet(
                    icon = Icons.Default.Visibility,
                    title = "What we look at",
                    body = "Every few seconds, Safe Companion takes a quick picture of " +
                        "your screen so it can read the words on it."
                )
                DisclosureBullet(
                    icon = Icons.Default.Memory,
                    title = "Where the picture goes",
                    body = "Nowhere. It's checked on this phone, then thrown away " +
                        "within a second. Never sent over the internet."
                )
                DisclosureBullet(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "How to turn it off",
                    body = "There is always a 'Turn Off' button in the notification " +
                        "Safe Companion shows while it's watching."
                )

                Spacer(Modifier.height(20.dp))

                // The "I understand" checkbox. Tap target is the whole row, not
                // just the checkbox, so it works for shaky fingers.
                Card(
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { understood = !understood }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = understood,
                            onCheckedChange = { understood = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "I understand. Pictures of my screen stay on this phone " +
                                "and I can turn this off any time.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                SafeHarborButton(
                    text = "Continue",
                    onClick = onContinue,
                    enabled = understood
                )

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel — don't turn this on", style = MaterialTheme.typography.titleSmall)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DisclosureBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NavyBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = NavyBlue,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = NavyBlue
            )
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

// ─── Helper Functions ────────────────────────────────────────────────────────

private fun checkPermission(context: android.content.Context, step: PermStep): Boolean {
    return when (step.id) {
        "screen_monitor" -> {
            // The MediaProjection consent token is single-use and lives in
            // ScreenScanService — there's no synchronous Android API to ask
            // "is the user currently letting us screen-capture?". Instead we
            // mirror the consent state into SharedPreferences when consent is
            // granted (in MainActivity's ActivityResultLauncher) and read it
            // here. SharedPreferences is fast/synchronous and fine for a
            // boolean flag.
            context.getSharedPreferences("safeharbor_runtime", 0)
                .getBoolean("screen_monitor_active", false)
        }
        "notification_listener" -> {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            flat.contains(context.packageName)
        }
        "usage_access" -> {
            val ops = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            val mode = ops?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.unsafeCheckOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(), context.packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.checkOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(), context.packageName
                    )
                }
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        }
        "battery" -> {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        "notifications" -> {
            if (Build.VERSION.SDK_INT >= 33) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true // Auto-granted on older Android
            }
        }
        else -> {
            val perm = step.manifestPermission ?: return true
            context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}

private fun getManufacturerNote(): String? {
    return when (Build.MANUFACTURER.lowercase()) {
        "samsung" -> "Samsung tip: Look under Apps \u2192 Special app access \u2192 Notification access"
        "huawei", "honor" -> "Huawei tip: Look under Settings \u2192 Apps \u2192 Special app access"
        "xiaomi", "redmi", "poco" -> "Xiaomi tip: You may also need to enable this in the Security app"
        "oppo", "oneplus", "realme" -> "OnePlus/OPPO tip: Look under Apps \u2192 Special app access"
        else -> null
    }
}

// ─── Step text composables ───────────────────────────────────────────────────

@Composable
private fun StepText(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(NavyBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StepWithHint(number: String, text: String, hint: String) {
    Column {
        StepText(number, text)
        Text(
            "     \u2192 $hint",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(start = 40.dp, top = 2.dp)
        )
    }
}

// ─── Progress Bar ────────────────────────────────────────────────────────────

@Composable
private fun ProgressBar(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            "Step $current of $total",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
    Spacer(Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { current.toFloat() / total },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(6.dp),
        color = WarmGold,
        trackColor = LightSurface,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
    )
}

// ─── Existing Pages (preserved from original) ───────────────────────────────

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 48.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(60.dp))

        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = NavyBlue
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Welcome to\nSafe Companion",
            style = MaterialTheme.typography.displaySmall,
            color = NavyBlue,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Safe Companion watches over your phone and helps protect you from scams, fraud, and privacy risks.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Setting this up takes about 3 minutes.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FeatureRow("🛡️", "Protects you from phone and text scams automatically")
                FeatureRow("💬", "Chat with Safe Companion any time you're unsure about something")
                FeatureRow("📞", "Screens suspicious calls before they reach you")
                FeatureRow("👨‍👩‍👧", "Alerts your family when something looks dangerous")
            }
        }

        Spacer(Modifier.height(40.dp))

        SafeHarborButton(text = "Let's get started", onClick = onNext)

        Spacer(Modifier.height(12.dp))

        Text(
            "Setting this up for a family member? That's perfect.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WhoIsThisForPage(onSelf: () -> Unit, onFamily: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 48.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            "Who will be using\nSafe Companion?",
            style = MaterialTheme.typography.headlineLarge,
            color = NavyBlue,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(40.dp))

        Card(
            onClick = onSelf,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 80.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("🧑", fontSize = 36.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Setting up for myself", style = MaterialTheme.typography.titleMedium, color = NavyBlue, fontWeight = FontWeight.Bold)
                    Text("I want to protect my own phone and messages", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            onClick = onFamily,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 80.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("👨‍👩‍👧", fontSize = 36.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Setting up for a family member", style = MaterialTheme.typography.titleMedium, color = NavyBlue, fontWeight = FontWeight.Bold)
                    Text("I'm helping protect someone I love", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun NamePage(
    userName: String,
    onUserNameChange: (String) -> Unit,
    isForFamily: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 48.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(40.dp))

        Text("👤", fontSize = 60.sp)

        Spacer(Modifier.height(16.dp))

        Text(
            if (isForFamily) "What's their name?" else "What's your name?",
            style = MaterialTheme.typography.headlineLarge,
            color = NavyBlue,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Safe Companion uses ${if (isForFamily) "their" else "your"} name to make alerts feel personal",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = userName,
            onValueChange = onUserNameChange,
            label = { Text("First name", style = MaterialTheme.typography.bodyLarge) },
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(40.dp))

        SafeHarborButton(
            text = "Continue",
            onClick = onNext,
            enabled = userName.isNotBlank()
        )

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onBack) {
            Text("Back", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ApiKeyPage(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    testResult: String?,
    onTestConnection: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 48.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(24.dp))

        Text("🧠", fontSize = 60.sp)

        Spacer(Modifier.height(16.dp))

        Text(
            "Connect Safe Companion's brain",
            style = MaterialTheme.typography.headlineMedium,
            color = NavyBlue,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Safe Companion already protects you on this phone, no internet needed. " +
                "Adding a connection key makes the protection even smarter for tricky " +
                "scams. You can skip this and add a key later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StepText("1", "Tap the button below to open the Anthropic website")
                StepText("2", "Sign in or create an account")
                StepText("3", "Copy your API key")
                StepText("4", "Come back here and paste it in the box below")
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/account/keys"))
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open Anthropic Website", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Paste your API key here") },
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
            shape = RoundedCornerShape(12.dp)
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
                Text("Testing connection...")
            } else {
                Text("Test Connection")
            }
        }

        if (testResult != null && testResult != "testing") {
            Spacer(Modifier.height(8.dp))
            val (color, message) = when (testResult) {
                "success" -> Pair(SafeGreen, "✅ Connected! Safe Companion's brain is working.")
                "invalid_key" -> Pair(ScamRed, "❌ Invalid API key. Please check and try again.")
                else -> Pair(WarningAmber, "⚠️ Could not connect. Please check your internet.")
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = color)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Your key is stored securely on this phone only. We never see it.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Item 5 Stage 1: Continue is always enabled. If the user pasted a key
        // we use it; if not, the app runs in on-device-only mode and the user
        // can add a key later from Settings → API Key.
        SafeHarborButton(
            text = if (apiKey.isNotBlank()) "Continue" else "Continue without a key",
            onClick = onNext,
            enabled = true
        )

        Spacer(Modifier.height(8.dp))

        // Explicit skip affordance so the screen feels intentional in offline
        // mode rather than like the user "should" enter something.
        TextButton(onClick = onNext) {
            Text(
                "I'll add this later — just use on-device protection",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onBack) {
            Text("Back", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun FamilyContactPage(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var name1 by remember { mutableStateOf("") }
    var phone1 by remember { mutableStateOf("") }
    var name2 by remember { mutableStateOf("") }
    var phone2 by remember { mutableStateOf("") }
    var showSecond by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 48.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(40.dp))

        Text("👨‍👩‍👧", fontSize = 60.sp)

        Spacer(Modifier.height(16.dp))

        Text(
            "Who would you like Safe Companion\nto keep in the loop?",
            style = MaterialTheme.typography.headlineMedium,
            color = NavyBlue,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Optional. Add a family member or close friend so Safe Companion " +
                "can let them know if it spots something risky.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name1,
            onValueChange = { name1 = it },
            label = { Text("Name (e.g. Daughter Sarah)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = phone1,
            onValueChange = { phone1 = it },
            label = { Text("Phone number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(12.dp)
        )

        if (showSecond) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name2,
                onValueChange = { name2 = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = phone2,
                onValueChange = { phone2 = it },
                label = { Text("Phone number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (!showSecond) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showSecond = true }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = NavyBlue)
                Spacer(Modifier.width(4.dp))
                Text("Add another contact", color = NavyBlue)
            }
        }

        // Item 4 (option a) — simplified: just the single "send me a weekly
        // report" opt-in. Share-with-family and email-me used to be sub-
        // checkboxes here, but the report screen itself now always shows a
        // Share button (any app — text, email, anything) and an Email button
        // (opens email app pre-filled). No reason to commit upfront.
        Spacer(Modifier.height(28.dp))

        var weeklyOptIn by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { weeklyOptIn = !weeklyOptIn }
                    .padding(14.dp)
            ) {
                Checkbox(checked = weeklyOptIn, onCheckedChange = { weeklyOptIn = it })
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Send me a weekly safety report",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = NavyBlue
                    )
                    Text(
                        "Every Monday, see what Safe Companion did this week. " +
                            "You can share it with your family or email it to " +
                            "yourself from the report screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        SafeHarborButton(
            text = "Continue",
            onClick = {
                if (name1.isNotBlank() && phone1.isNotBlank()) {
                    viewModel.addFamilyContact(name1, phone1)
                }
                if (showSecond && name2.isNotBlank() && phone2.isNotBlank()) {
                    viewModel.addFamilyContact(name2, phone2)
                }
                viewModel.setWeeklyDigestPrefs(weeklyOptIn)
                onNext()
            }
        )

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onNext) {
            Text("I'll do this later", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
        }

        TextButton(onClick = onBack) {
            Text("Back", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TestDrivePage(userName: String, onComplete: () -> Unit) {
    var showAlert by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1500)
        showAlert = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 48.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(40.dp))

        if (!showSuccess) {
            Text("🎉", fontSize = 60.sp)

            Spacer(Modifier.height(16.dp))

            Text(
                "Let's try it out!",
                style = MaterialTheme.typography.headlineMedium,
                color = NavyBlue,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Here's a pretend scam message. Watch what Safe Companion does.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💬", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Unknown Number",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "URGENT: Your bank account has been suspended. Call 0800-555-FAKE immediately to restore access or your funds will be locked permanently.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(
                visible = showAlert,
                enter = fadeIn() + expandVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ScamRed.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, ScamRed)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🚨", fontSize = 36.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "SCAM DETECTED",
                            style = MaterialTheme.typography.titleLarge,
                            color = ScamRed,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This message is trying to scare you into calling a fake number. Your bank would never text you like this.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (showAlert) {
                Spacer(Modifier.height(24.dp))

                SafeHarborButton(
                    text = "I see how it works!",
                    onClick = { showSuccess = true }
                )
            } else {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(
                    color = NavyBlue,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Safe Companion is scanning...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            val pinContext = LocalContext.current
            Text("🛡️", fontSize = 80.sp)

            Spacer(Modifier.height(24.dp))

            Text(
                "Safe Companion is ready!",
                style = MaterialTheme.typography.headlineMedium,
                color = NavyBlue,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "You are now protected. Safe Companion will watch over ${userName.ifBlank { "you" }} around the clock.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // Pin-to-Home button. Beta testers have reported they can't find
            // Safe Companion after install because the icon only goes to the
            // App Drawer, not the Home Screen. Android disabled silent home-
            // screen pinning in API 26 — the best we can do is fire the
            // system "Add to Home Screen?" dialog via ShortcutManagerCompat.
            // Most launchers (Pixel, Samsung, Nova) support this; the few
            // that don't will simply ignore the call with no harm done.
            OutlinedButton(
                onClick = { requestPinToHomeScreen(pinContext) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = NavyBlue)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Add Safe Companion to my Home screen",
                    color = NavyBlue,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "(Tap above. Your phone will ask if you want to add it.)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            SafeHarborButton(
                text = "Start Using Safe Companion",
                onClick = onComplete
            )
        }
    }
}

/**
 * Trigger Android's "Add Safe Companion to Home screen?" system dialog.
 * Single tap → user picks Add → icon pinned. If the launcher doesn't
 * support pinning shortcuts (rare on modern Android), this call no-ops
 * silently — the user can still launch Safe Companion from the App Drawer.
 */
private fun requestPinToHomeScreen(context: android.content.Context) {
    try {
        val shortcutManager = androidx.core.content.pm.ShortcutManagerCompat::class.java
        if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            android.widget.Toast.makeText(
                context,
                "Your phone's home screen doesn't allow this — but Safe Companion " +
                    "is installed. Swipe up from the bottom to find its icon.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { action = android.content.Intent.ACTION_MAIN }
            ?: android.content.Intent(context, com.safeharborsecurity.app.MainActivity::class.java)
                .setAction(android.content.Intent.ACTION_MAIN)

        val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, "safe_companion_home")
            .setShortLabel("Safe Companion")
            .setLongLabel("Safe Companion — scam protection")
            .setIcon(
                androidx.core.graphics.drawable.IconCompat.createWithResource(
                    context,
                    com.safeharborsecurity.app.R.mipmap.ic_launcher
                )
            )
            .setIntent(launchIntent)
            .build()

        androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    } catch (e: Exception) {
        android.util.Log.w("Onboarding", "requestPinShortcut threw", e)
        android.widget.Toast.makeText(
            context,
            "Couldn't open the Add to Home Screen prompt. You can add Safe " +
                "Companion manually by swiping up to your apps and long-pressing " +
                "its icon.",
            android.widget.Toast.LENGTH_LONG
        ).show()
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

@Composable
fun SafeHarborButton(
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
