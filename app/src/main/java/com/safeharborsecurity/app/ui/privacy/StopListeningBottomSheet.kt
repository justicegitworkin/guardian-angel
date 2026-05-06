@file:Suppress("DEPRECATION")

package com.safeharborsecurity.app.ui.privacy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeharborsecurity.app.ui.theme.*

data class RemediationStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val minSdk: Int = 26,
    val intentAction: () -> Intent?,
    val stateChecker: ((Context) -> Boolean?)? = null  // null = unknown, true = active/risky, false = off/safe
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopListeningBottomSheet(
    onDismiss: () -> Unit,
    onScanAfterDone: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(-1) }  // -1 = overview
    var turningOffAll by remember { mutableStateOf(false) }
    var turnOffProgress by remember { mutableIntStateOf(0) }

    val steps = remember { buildStepsList(context) }
    val applicableSteps = remember(steps) {
        steps.filter { Build.VERSION.SDK_INT >= it.minSdk }
    }
    val totalSteps = applicableSteps.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WarmWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Stop Silent Listening on Your Device",
                style = MaterialTheme.typography.headlineSmall,
                color = NavyBlue,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "We'll walk you through turning off tracking and listening on your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            if (currentStep == -1) {
                // Overview: show all steps with status badges
                applicableSteps.forEachIndexed { index, step ->
                    val state = step.stateChecker?.invoke(context)
                    val statusEmoji = when (state) {
                        true -> "🔴"
                        false -> "🟢"
                        null -> "🟡"
                    }
                    val statusText = when (state) {
                        true -> "Active"
                        false -> "Off"
                        null -> "Unknown"
                    }

                    Card(
                        onClick = { currentStep = index },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(step.icon, null, modifier = Modifier.size(28.dp), tint = NavyBlue)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(step.title, style = MaterialTheme.typography.titleSmall, color = NavyBlue, fontWeight = FontWeight.Bold)
                            }
                            Text(statusEmoji, fontSize = 16.sp)
                            Text(statusText, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Icon(Icons.Default.ChevronRight, null, tint = WarmGold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // "Turn Off Everything" master button
                Button(
                    onClick = {
                        turningOffAll = true
                        turnOffProgress = 0
                        currentStep = 0
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ScamRed),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.SecurityUpdateGood, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Turn Off Everything — Step by Step", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            } else if (currentStep < totalSteps) {
                // Individual step view
                val step = applicableSteps[currentStep]

                if (turningOffAll) {
                    LinearProgressIndicator(
                        progress = { (currentStep + 1).toFloat() / totalSteps },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = WarmGold,
                        trackColor = LightSurface,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Turning off ${currentStep + 1} of $totalSteps...",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { (currentStep + 1).toFloat() / totalSteps },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = WarmGold,
                        trackColor = LightSurface,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Step ${currentStep + 1} of $totalSteps", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                }

                // State badge
                val state = step.stateChecker?.invoke(context)
                if (state != null) {
                    Surface(
                        color = if (state) ScamRedLight else SafeGreenLight,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (state) "🔴 Currently Active" else "🟢 Already Off",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state) ScamRed else SafeGreen
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(step.icon, null, modifier = Modifier.size(32.dp), tint = NavyBlue)
                            Text(step.title, style = MaterialTheme.typography.titleMedium, color = NavyBlue, fontWeight = FontWeight.Bold)
                        }

                        Text(step.description, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, lineHeight = 26.sp)

                        val intent = step.intentAction()
                        if (intent != null) {
                            Button(
                                onClick = {
                                    try {
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Open Settings", color = Color.White)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (currentStep > 0) currentStep-- else { currentStep = -1; turningOffAll = false }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (currentStep > 0) "Back" else "Overview")
                    }

                    Button(
                        onClick = {
                            if (currentStep < totalSteps - 1) {
                                currentStep++
                            } else {
                                if (turningOffAll) {
                                    Toast.makeText(context, "All done! Safe Companion will check your settings.", Toast.LENGTH_LONG).show()
                                }
                                onScanAfterDone()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = WarmGold),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (currentStep < totalSteps - 1) "Next step" else "All done!",
                            color = TextOnGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun buildStepsList(context: Context): List<RemediationStep> {
    return listOf(
        RemediationStep(
            stepNumber = 1,
            title = "Turn Off Ad Services",
            description = "Android has built-in ad tracking called 'Ad Services'. " +
                    "This lets companies learn what you like and show you targeted ads.\n\n" +
                    "Tap the button below to open the settings, then turn OFF all the switches you see " +
                    "(Ad Topics, App-suggested ads, and Ad measurement).",
            icon = Icons.Default.AdsClick,
            minSdk = 33,
            intentAction = {
                try { Intent("android.adservices.ui.SETTINGS") }
                catch (_: Exception) { Intent(Settings.ACTION_PRIVACY_SETTINGS) }
            },
            stateChecker = { ctx ->
                try {
                    val pm = ctx.packageManager
                    val state = pm.getApplicationEnabledSetting("com.google.android.adservices.api")
                    state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                } catch (_: Exception) { null }
            }
        ),
        RemediationStep(
            stepNumber = 2,
            title = "Turn Off Personalised Ads",
            description = "Google keeps track of your interests to show personalised ads. " +
                    "You can stop this.\n\n" +
                    "Tap the button below, then look for 'Opt out of Ads Personalisation' and turn it ON.",
            icon = Icons.Default.VisibilityOff,
            intentAction = {
                try { Intent("com.google.android.gms.settings.ADS_PRIVACY_SETTINGS") }
                catch (_: Exception) {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:com.google.android.gms")
                    }
                }
            }
        ),
        RemediationStep(
            stepNumber = 3,
            title = "Check Microphone Access",
            description = "Your phone lets you turn off the microphone for ALL apps at once. " +
                    "This is a powerful way to stop any app from listening.\n\n" +
                    "Tap the button below to open Privacy settings. Look for 'Microphone access' " +
                    "and turn it OFF if you want no apps to use your microphone.",
            icon = Icons.Default.MicOff,
            minSdk = 31,
            intentAction = { Intent(Settings.ACTION_PRIVACY_SETTINGS) }
        ),
        RemediationStep(
            stepNumber = 4,
            title = "Review Accessibility Services",
            description = "Some apps ask for 'Accessibility' access, which lets them see everything " +
                    "on your screen. Only trusted apps like TalkBack should have this.\n\n" +
                    "Tap the button below to see which apps have accessibility access. " +
                    "Turn OFF any app you don't recognise.",
            icon = Icons.Default.Accessibility,
            intentAction = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        ),
        RemediationStep(
            stepNumber = 5,
            title = "Review Notification Access",
            description = "Apps with notification access can read all your notifications, " +
                    "including messages and codes.\n\n" +
                    "Tap below to see which apps have this. Turn OFF any you don't recognise.",
            icon = Icons.Default.Notifications,
            intentAction = { Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") }
        )
    )
}
