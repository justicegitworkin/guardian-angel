package com.safeharborsecurity.app.ui.appchecker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.model.InstalledAppInfo
import com.safeharborsecurity.app.data.model.PermissionRisk
import com.safeharborsecurity.app.data.repository.AppAnalysisResult
import com.safeharborsecurity.app.ui.components.VerdictIcon
import com.safeharborsecurity.app.ui.components.toVerdict
import com.safeharborsecurity.app.ui.theme.*
import com.safeharborsecurity.app.util.CoachingTip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    @Suppress("UNUSED_PARAMETER") packageName: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
    viewModel: AppDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
                title = { Text("App Details", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = WarmGold)
            }
            return@Scaffold
        }

        val app = state.appInfo
        if (app == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("App not found. It may have been uninstalled.", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Identity Card
            AppIdentityCard(app)

            // Permissions Summary
            PermissionsSection(app)

            // AI Analysis Button & Result
            if (state.analysisResult == null && !state.isAnalyzing) {
                Button(
                    onClick = { viewModel.analyzeApp() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Check This App with Safe Companion", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }

            // Analyzing state
            if (state.isAnalyzing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = WarmGold, strokeWidth = 3.dp)
                        Text(
                            "Safe Companion is checking this app for you...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Error
            state.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningAmberLight),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(error, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Button(
                            onClick = { viewModel.analyzeApp() },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Try Again", color = Color.White)
                        }
                    }
                }
            }

            // Analysis Result
            state.analysisResult?.let { result ->
                AnalysisResultCard(result)
                ActionButtons(app = app, result = result, context = context, onNavigateToChat = onNavigateToChat)
            }

            // Coaching Tip
            state.coachingTip?.let { tip ->
                CoachingCard(tip = tip, onDismiss = { viewModel.dismissCoaching() })
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppIdentityCard(app: InstalledAppInfo) {
    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)

    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                bitmap = app.icon.toBitmap(width = 72, height = 72).asImageBitmap(),
                contentDescription = app.appName,
                modifier = Modifier.size(72.dp).clip(CircleShape)
            )

            Text(
                app.appName,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (app.developerName != null) {
                Text(
                    "by ${app.developerName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            if (app.versionName != null) {
                Text(
                    "Version ${app.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            InstallSourceBadge(app.installSource)

            Text(
                "Installed: ${dateFormat.format(Date(app.firstInstallTime))} (${
                    DateUtils.getRelativeTimeSpanString(
                        app.firstInstallTime,
                        System.currentTimeMillis(),
                        DateUtils.DAY_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    )
                })",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            if (app.lastUpdateTime != app.firstInstallTime) {
                Text(
                    "Last updated: ${dateFormat.format(Date(app.lastUpdateTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun PermissionsSection(app: InstalledAppInfo) {
    val highCount = app.requestedPermissions.count { it.riskLevel == PermissionRisk.HIGH }

    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "What this app can do",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            if (app.requestedPermissions.isEmpty()) {
                Text(
                    "This app doesn't need any special permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafeGreen
                )
            } else {
                // Risk summary
                if (highCount > 0) {
                    val riskColor = if (highCount >= 3) ScamRed else WarningAmber
                    Surface(
                        color = if (highCount >= 3) ScamRedLight else WarningAmberLight,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "This app has access to $highCount sensitive permission${if (highCount > 1) "s" else ""}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = riskColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Permission list
                app.requestedPermissions.forEach { perm ->
                    val color = when (perm.riskLevel) {
                        PermissionRisk.HIGH -> ScamRed
                        PermissionRisk.MEDIUM -> WarningAmber
                        PermissionRisk.LOW -> SafeGreen
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(perm.emoji, fontSize = 20.sp)
                        Text(
                            perm.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisResultCard(result: AppAnalysisResult) {
    val verdictEnum = result.verdict.toVerdict()
    val (bgColor, textColor) = when (result.verdict) {
        "SAFE" -> Pair(SafeGreenLight, SafeGreen)
        "SUSPICIOUS" -> Pair(WarningAmberLight, WarningAmber)
        "DANGEROUS" -> Pair(ScamRedLight, ScamRed)
        else -> Pair(LightSurface, TextPrimary)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VerdictIcon(verdict = verdictEnum, size = 48.dp, showLabel = false)
                Text(
                    result.verdict,
                    style = MaterialTheme.typography.headlineMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            }

            if (result.appDescription.isNotBlank()) {
                Text(
                    result.appDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    lineHeight = 24.sp
                )
            }

            Text(
                result.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                lineHeight = 24.sp
            )

            if (result.details.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("What we found", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(result.details, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            if (result.permissionConcerns.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Permission concerns", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                        result.permissionConcerns.forEach { concern ->
                            Text("• $concern", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
            }

            if (result.whatToDoNext.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("What to do next", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(result.whatToDoNext, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    app: InstalledAppInfo,
    result: AppAnalysisResult,
    context: Context,
    onNavigateToChat: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (result.verdict) {
            "DANGEROUS" -> {
                if (app.isSystemApp) {
                    // System app: disable instead of uninstall
                    Text(
                        "This app came with your phone and can't be removed, but you can disable it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Button(
                        onClick = { openAppSettings(context, app.packageName) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Manage This App's Settings", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            uninstallApp(context, app.packageName)
                            Toast.makeText(context, "A dialog should appear asking if you want to remove this app. Tap 'OK' to remove it.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ScamRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Uninstall This App", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Safe Companion found a concerning app on my phone: ${app.appName}.\n\n${result.summary}\n\n${result.whatToDoNext}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Tell My Family"))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = WarmGold)
                    Spacer(Modifier.width(8.dp))
                    Text("Tell My Family", color = WarmGold)
                }

                OutlinedButton(
                    onClick = { onNavigateToChat("I found a concerning app called '${app.appName}' (${app.packageName}). Safe Companion says it's DANGEROUS. What should I do?") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ask Safe Companion About This", color = WarmGold)
                }
            }

            "SUSPICIOUS" -> {
                Button(
                    onClick = { openAppSettings(context, app.packageName) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Open App Settings", color = Color.White, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { openAppSettings(context, app.packageName) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = WarningAmber)
                    Spacer(Modifier.width(8.dp))
                    Text("Uninstall or Modify This App", color = WarningAmber)
                }
                Text(
                    "This will open the app's settings where you can uninstall it, turn off permissions, or disable it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedButton(
                    onClick = { onNavigateToChat("I found an app called '${app.appName}' (${app.packageName}). Safe Companion says it's SUSPICIOUS. Can you tell me more about it?") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ask Safe Companion About This", color = WarmGold)
                }
            }

            "SAFE" -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SafeGreenLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("\u2705", fontSize = 24.sp)
                        Text("This App Looks Safe", color = SafeGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                OutlinedButton(
                    onClick = { openAppSettings(context, app.packageName) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Open App Settings", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun CoachingCard(tip: CoachingTip, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavyBlue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                tip.title,
                style = MaterialTheme.typography.titleMedium,
                color = WarmGold,
                fontWeight = FontWeight.Bold
            )

            tip.tips.forEach { tipText ->
                Text(
                    "\u2022 $tipText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    lineHeight = 22.sp
                )
            }

            TextButton(onClick = onDismiss) {
                Text("Got it", color = WarmGold)
            }
        }
    }
}

private fun uninstallApp(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE).apply {
        data = Uri.parse("package:$packageName")
    }
    context.startActivity(intent)
}

private fun openAppSettings(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }
    context.startActivity(intent)
}
