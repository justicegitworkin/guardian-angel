package com.safeharborsecurity.app.ui.onboarding

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class WalkthroughState(
    val currentStep: Int = 0,
    val steps: List<PermissionStep> = emptyList(),
    val grantedMap: Map<String, Boolean> = emptyMap(),
    val isComplete: Boolean = false
)

@HiltViewModel
class GuidedPermissionViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(WalkthroughState())
    val state: StateFlow<WalkthroughState> = _state.asStateFlow()

    private val allSteps = buildSteps()

    init {
        refreshAll()
    }

    private fun buildSteps(): List<PermissionStep> = listOf(
        PermissionStep(
            id = "microphone",
            icon = "\uD83C\uDFA4", // 🎤
            iconColor = Color(0xFF1E88E5),
            title = "Talk to Safe Harbor",
            explanation = "Safe Harbor needs your microphone so you can talk to it using your voice instead of typing.",
            reassurance = "The microphone is only used when you are actively speaking to Safe Harbor. It never listens in the background.",
            buttonText = "Allow Microphone",
            actionType = PermissionActionType.RUNTIME,
            manifestPermission = Manifest.permission.RECORD_AUDIO
        ),
        PermissionStep(
            id = "notifications",
            icon = "\uD83D\uDD14", // 🔔
            iconColor = Color(0xFFFF9800),
            title = "Get Safety Alerts",
            explanation = "Safe Harbor needs to send you notifications so it can warn you about scams immediately.",
            reassurance = "We only send important safety alerts. We will not spam you with unnecessary messages.",
            buttonText = "Allow Notifications",
            actionType = PermissionActionType.RUNTIME,
            manifestPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS else null
        ),
        PermissionStep(
            id = "screen_monitor",
            icon = "\uD83D\uDCF1", // 📱
            iconColor = Color(0xFF43A047),
            title = "Watch My Screen for Scams",
            explanation = "Safe Companion can quickly look at your screen when a text or payment app is open, to warn you about scams. The pictures of your screen never leave this phone.",
            reassurance = "Nothing is sent over the internet. Safe Companion looks at the words on your screen using its own brain, then throws the picture away.",
            buttonText = "Allow Screen Monitor",
            actionType = PermissionActionType.SETTINGS_DEEP_LINK,
            settingsAction = "com.safeharborsecurity.app.action.SCREEN_MONITOR_CONSENT"
        ),
        // Item 2: usage_access removed — payment-app detection now happens via
        // ScreenScanService (same MediaProjection consent as SMS scanning).
        PermissionStep(
            id = "usage_access",
            icon = "\uD83D\uDEE1\uFE0F", // 🛡️
            iconColor = Color(0xFF7B1FA2),
            title = "(legacy — not shown)",
            explanation = "(legacy — usage access step replaced by screen_monitor in Item 2)",
            reassurance = "(legacy)",
            buttonText = "Skip",
            actionType = PermissionActionType.SETTINGS_DEEP_LINK,
            settingsAction = Settings.ACTION_USAGE_ACCESS_SETTINGS
        ),
        PermissionStep(
            id = "camera",
            icon = "\uD83D\uDCF7", // 📷
            iconColor = Color(0xFFE53935),
            title = "Check Suspicious Photos",
            explanation = "Safe Harbor needs your camera to take photos of suspicious letters, QR codes, or anything you want checked.",
            reassurance = "The camera is only used when you choose to take a photo. Safe Harbor never uses it on its own.",
            buttonText = "Allow Camera",
            actionType = PermissionActionType.RUNTIME,
            manifestPermission = Manifest.permission.CAMERA
        ),
        PermissionStep(
            id = "contacts",
            icon = "\uD83D\uDC65", // 👥
            iconColor = Color(0xFF00ACC1),
            title = "Recognise Your Contacts",
            explanation = "Safe Harbor can check incoming calls and messages against your contacts to tell you if a caller is someone you know.",
            reassurance = "Your contacts stay on your phone. We never upload or share them.",
            buttonText = "Allow Contacts",
            actionType = PermissionActionType.RUNTIME,
            manifestPermission = Manifest.permission.READ_CONTACTS
        ),
        PermissionStep(
            id = "phone",
            icon = "\uD83D\uDCDE", // 📞
            iconColor = Color(0xFF5E35B1),
            title = "Screen Incoming Calls",
            explanation = "Safe Harbor needs access to your calls so it can warn you about scam callers before you answer.",
            reassurance = "Safe Harbor never records your calls. It only checks who is calling.",
            buttonText = "Allow Phone",
            actionType = PermissionActionType.RUNTIME,
            manifestPermission = Manifest.permission.READ_PHONE_STATE
        ),
        PermissionStep(
            id = "battery",
            icon = "\uD83D\uDD0B", // 🔋
            iconColor = Color(0xFF558B2F),
            title = "Stay Alert in the Background",
            explanation = "Safe Harbor needs to keep running in the background so it can protect you even when you are not looking at it.",
            reassurance = "Safe Harbor uses very little battery. This just stops Android from putting it to sleep.",
            buttonText = "Allow Background",
            actionType = PermissionActionType.BATTERY_OPTIMIZATION,
            settingsAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        )
    )

    // Item 2: hide the legacy usage_access step from the visible list. The
    // step body is left in `allSteps` to avoid a larger refactor, but it never
    // surfaces in the UI because everything iterates over `visibleSteps`.
    private val visibleSteps: List<PermissionStep> get() = allSteps.filterNot {
        it.id == "usage_access" || it.id == "notification_listener"
    }

    fun refreshAll() {
        val granted = mutableMapOf<String, Boolean>()
        for (step in visibleSteps) {
            granted[step.id] = isStepGranted(step)
        }
        _state.value = _state.value.copy(
            steps = visibleSteps,
            grantedMap = granted
        )
    }

    fun refreshStep(stepId: String) {
        val step = allSteps.find { it.id == stepId } ?: return
        val newMap = _state.value.grantedMap.toMutableMap()
        newMap[stepId] = isStepGranted(step)
        _state.value = _state.value.copy(grantedMap = newMap)
    }

    fun advanceToNext() {
        val current = _state.value.currentStep
        if (current < allSteps.size - 1) {
            // Skip steps that are already granted
            var next = current + 1
            while (next < allSteps.size && _state.value.grantedMap[allSteps[next].id] == true) {
                next++
            }
            if (next >= allSteps.size) {
                _state.value = _state.value.copy(isComplete = true)
            } else {
                _state.value = _state.value.copy(currentStep = next)
            }
        } else {
            _state.value = _state.value.copy(isComplete = true)
        }
    }

    fun goToStep(index: Int) {
        if (index in allSteps.indices) {
            _state.value = _state.value.copy(currentStep = index)
        }
    }

    fun markComplete() {
        _state.value = _state.value.copy(isComplete = true)
    }

    fun countGranted(): Int = _state.value.grantedMap.count { it.value }

    private fun isStepGranted(step: PermissionStep): Boolean = when (step.id) {
        "microphone" -> ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        "notifications" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.areNotificationsEnabled()
            }
        }

        "notification_listener" -> isNotificationListenerEnabled()

        "usage_access" -> isUsageAccessGranted()

        "camera" -> ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        "contacts" -> ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        "phone" -> ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        "battery" -> {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        else -> false
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val componentName = ComponentName(context, "com.safeharborsecurity.app.service.SafeHarborNotificationListener")
        return flat.contains(componentName.flattenToString()) ||
                flat.contains(context.packageName)
    }

    private fun isUsageAccessGranted(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
}
