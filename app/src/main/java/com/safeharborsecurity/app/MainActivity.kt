package com.safeharborsecurity.app

import android.content.Intent
import com.safeharborsecurity.app.BuildConfig
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.ui.lock.LockScreen
import com.safeharborsecurity.app.ui.navigation.SafeHarborNavHost
import com.safeharborsecurity.app.ui.navigation.Screen
import com.safeharborsecurity.app.ui.onboarding.OnboardingViewModel
import com.safeharborsecurity.app.ui.theme.SafeHarborTheme
import com.safeharborsecurity.app.ui.theme.TextSizePreference
import com.safeharborsecurity.app.util.KeystoreManager
import com.safeharborsecurity.app.util.NfcSecurityManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var prefs: UserPreferences
    @Inject lateinit var keystoreManager: KeystoreManager
    @Inject lateinit var nfcSecurityManager: NfcSecurityManager

    private companion object {
        // Part E2: hard allow-list of safeharbor:// deep-link hosts. Anything
        // else is rejected so an external app can't navigate us into surprising
        // screens or trigger code paths with hostile parameters.
        val ALLOWED_DEEP_LINK_HOSTS: Set<String> =
            setOf("message_detail", "chat", "wifi_detail", "app_detail")

        // Numeric IDs only — strip everything else (including unicode digits).
        fun sanitizeId(raw: String): String = raw.take(32).filter { it.isDigit() || it == '-' }

        // Android package names: ASCII letters, digits, dots, underscores; cap length.
        fun sanitizePackage(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.take(128)
                .filter { it.isLetterOrDigit() || it == '.' || it == '_' }
            return cleaned.takeIf { it.isNotBlank() && '.' in it }
        }

        // Free-form contextual text: cap length and strip control chars.
        fun sanitizeFreeText(raw: String): String =
            raw.take(2000).replace(Regex("[\\u0000-\\u001F]"), " ")
    }

    // Stores the last NFC analysis for the UI to pick up
    var lastNfcAnalysis: com.safeharborsecurity.app.data.model.NfcTagAnalysis? = null
        private set

    /** Tracks whether we're past the lock screen. Read from onResume / onPause. */
    @Volatile private var isUnlocked: Boolean = false

    override fun onResume() {
        super.onResume()
        // Refresh lastActiveTime whenever the app is foregrounded AND already
        // unlocked. We deliberately don't stamp while locked, otherwise
        // Settings → Lock Now (which sets lastActive=0L) would be defeated
        // by the user briefly switching apps and coming back.
        if (isUnlocked) {
            runBlocking {
                prefs.setLastActiveTime(System.currentTimeMillis())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Part C5: Block screenshots and recent-apps preview to keep API keys,
        // chat content, and message detail off the screenshot history.
        // Beta builds (SHOW_DEBUG_INFO=true) explicitly clear FLAG_SECURE so
        // testers can use screen mirroring (Phone Link / scrcpy) for support.
        if (BuildConfig.SHOW_DEBUG_INFO) {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else if (!BuildConfig.DEBUG) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        setContent {
            val onboardingVm: OnboardingViewModel = hiltViewModel()
            val isOnboardingDone by onboardingVm.isOnboardingDone.collectAsStateWithLifecycle()
            val textSizePref by onboardingVm.textSizePref.collectAsStateWithLifecycle()

            val pinHash by prefs.pinHash.collectAsState(initial = "")
            val biometricEnabled by prefs.isBiometricEnabled.collectAsState(initial = false)
            val autoLockTimeout by prefs.autoLockTimeoutMinutes.collectAsState(initial = 5)
            val lastActive by prefs.lastActiveTime.collectAsState(initial = 0L)

            var isLocked by remember { mutableStateOf(true) }
            var lockError by remember { mutableStateOf<String?>(null) }

            // Determine if we need the lock screen
            val hasPinSet = pinHash.isNotBlank()
            val shouldLock = hasPinSet && isOnboardingDone == true && autoLockTimeout > 0

            // Check if auto-lock timeout has expired. lastActive is included
            // in the keys so that Settings → Lock Now (which sets lastActive
            // to 0) immediately re-locks the app instead of waiting for the
            // next activity recreation.
            LaunchedEffect(isOnboardingDone, hasPinSet, lastActive, autoLockTimeout) {
                when {
                    !hasPinSet -> isLocked = false
                    !shouldLock -> isLocked = false
                    // Lock Now sentinel — sets lastActive to 0L. Any time we
                    // see 0 with shouldLock true and a PIN set, force lock.
                    lastActive == 0L -> isLocked = true
                    else -> {
                        val elapsed = System.currentTimeMillis() - lastActive
                        val timeoutMs = autoLockTimeout * 60_000L
                        isLocked = elapsed > timeoutMs
                    }
                }
            }

            val navController = rememberNavController()

            val textSizeEnum = when (textSizePref) {
                "LARGE" -> TextSizePreference.LARGE
                "EXTRA_LARGE" -> TextSizePreference.EXTRA_LARGE
                else -> TextSizePreference.NORMAL
            }

            val biometricAvailable = remember {
                val bioManager = BiometricManager.from(this@MainActivity)
                bioManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                    BiometricManager.BIOMETRIC_SUCCESS
            }

            SafeHarborTheme(textSizePreference = textSizeEnum) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isOnboardingDone != null) {
                        // Mirror the locked/unlocked state out to the
                        // Activity-level flag so onResume's stamp logic
                        // knows whether to refresh lastActiveTime.
                        LaunchedEffect(shouldLock, isLocked) {
                            isUnlocked = !(shouldLock && isLocked)
                        }
                        // While unlocked, refresh the lastActive stamp every
                        // 30 seconds. Combined with onResume's stamp this is
                        // what makes "5 minutes of inactivity" mean what the
                        // user expects, instead of "5 minutes since you last
                        // unlocked the app."
                        LaunchedEffect(shouldLock, isLocked) {
                            if (!shouldLock || !isLocked) {
                                while (true) {
                                    prefs.setLastActiveTime(System.currentTimeMillis())
                                    kotlinx.coroutines.delay(30_000L)
                                }
                            }
                        }
                        if (shouldLock && isLocked) {
                            LockScreen(
                                onPinVerified = {
                                    isLocked = false
                                    lockError = null
                                    // Stamp lastActive so reopens via notification
                                    // don't immediately re-prompt for PIN. Without
                                    // this, every notification tap throws the user
                                    // back to the lock screen (tester report).
                                    runBlocking {
                                        prefs.setLastActiveTime(System.currentTimeMillis())
                                    }
                                },
                                onBiometricRequest = {
                                    showBiometricPrompt(
                                        onSuccess = {
                                            isLocked = false
                                            runBlocking {
                                                prefs.setLastActiveTime(System.currentTimeMillis())
                                            }
                                        },
                                        onError = { lockError = it }
                                    )
                                },
                                biometricAvailable = biometricAvailable && biometricEnabled,
                                errorMessage = lockError,
                                onPinSubmit = { pin ->
                                    keystoreManager.verifyPin(pin, pinHash)
                                }
                            )

                            // Auto-trigger biometric on first show
                            LaunchedEffect(Unit) {
                                if (biometricAvailable && biometricEnabled) {
                                    showBiometricPrompt(
                                        onSuccess = {
                                            isLocked = false
                                            runBlocking {
                                                prefs.setLastActiveTime(System.currentTimeMillis())
                                            }
                                        },
                                        onError = { lockError = it }
                                    )
                                }
                            }
                        } else {
                            val start = if (isOnboardingDone == true) Screen.Home.route
                                        else Screen.Onboarding.route
                            SafeHarborNavHost(
                                navController = navController,
                                startDestination = start
                            )

                            // Handle deep links and share intent (Part E2: validated)
                            LaunchedEffect(Unit) {
                                val uri = intent?.data
                                if (uri != null && uri.scheme == "safeharbor" && isOnboardingDone == true) {
                                    val host = uri.host
                                    if (host !in ALLOWED_DEEP_LINK_HOSTS) {
                                        Log.w("MainActivity", "Rejected deep link with unknown host: $host")
                                        return@LaunchedEffect
                                    }
                                    when (host) {
                                        "message_detail" -> {
                                            val alertId = uri.getQueryParameter("alertId")
                                                ?.let(::sanitizeId)
                                                ?.toLongOrNull()
                                            if (alertId != null) {
                                                navController.navigate(Screen.MessageDetail.createRoute(alertId))
                                            }
                                        }
                                        "chat" -> {
                                            val ctx = sanitizeFreeText(uri.getQueryParameter("context") ?: "")
                                            navController.navigate(Screen.SafeHarbor.withContext(ctx))
                                        }
                                        "wifi_detail" -> {
                                            navController.navigate(Screen.WifiDetail.route)
                                        }
                                        "app_detail" -> {
                                            val pkg = sanitizePackage(uri.getQueryParameter("packageName"))
                                            if (pkg != null) {
                                                navController.navigate(Screen.AppDetail.createRoute(pkg))
                                            }
                                        }
                                    }
                                } else if (intent?.action == Intent.ACTION_SEND && isOnboardingDone == true) {
                                    val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
                                    if (!sharedText.isNullOrBlank()) {
                                        // Route shared text/URLs to Safety Checker for auto-analysis
                                        navController.navigate(Screen.SafetyChecker.route)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Safe Companion")
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(info)
    }
}
