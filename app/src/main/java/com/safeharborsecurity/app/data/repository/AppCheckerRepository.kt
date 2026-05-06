package com.safeharborsecurity.app.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.google.gson.Gson
import com.safeharborsecurity.app.data.local.dao.SafetyCheckResultDao
import com.safeharborsecurity.app.data.local.entity.SafetyCheckResultEntity
import com.safeharborsecurity.app.data.model.AppPermission
import com.safeharborsecurity.app.data.model.InstallSource
import com.safeharborsecurity.app.data.model.InstalledAppInfo
import com.safeharborsecurity.app.data.model.PermissionRisk
import com.safeharborsecurity.app.data.remote.ClaudeApiService
import com.safeharborsecurity.app.data.remote.model.ClaudeMessage
import com.safeharborsecurity.app.data.remote.model.ClaudeRequest
import com.safeharborsecurity.app.util.extractJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCheckerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val claudeApi: ClaudeApiService,
    private val safetyCheckResultDao: SafetyCheckResultDao,
    private val gson: Gson
) {
    private val pm: PackageManager get() = context.packageManager

    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        packages.mapNotNull { pkgInfo ->
            val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
            val hasLauncher = pm.getLaunchIntentForPackage(pkgInfo.packageName) != null

            // Skip system services without launcher icons
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !hasLauncher) return@mapNotNull null

            val appName = appInfo.loadLabel(pm).toString()
            val icon = appInfo.loadIcon(pm)
            val permissions = mapPermissions(pkgInfo.requestedPermissions ?: emptyArray())

            InstalledAppInfo(
                packageName = pkgInfo.packageName,
                appName = appName,
                developerName = getDeveloperName(pkgInfo.packageName),
                icon = icon,
                versionName = pkgInfo.versionName,
                installSource = getInstallSource(pkgInfo.packageName, isSystem),
                firstInstallTime = pkgInfo.firstInstallTime,
                lastUpdateTime = pkgInfo.lastUpdateTime,
                isSystemApp = isSystem,
                requestedPermissions = permissions,
                hasLauncherIcon = hasLauncher
            )
        }.sortedByDescending { it.firstInstallTime }
    }

    suspend fun getAppInfo(packageName: String): InstalledAppInfo? = withContext(Dispatchers.IO) {
        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return@withContext null
        }

        val appInfo = pkgInfo.applicationInfo ?: return@withContext null
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        InstalledAppInfo(
            packageName = pkgInfo.packageName,
            appName = appInfo.loadLabel(pm).toString(),
            developerName = getDeveloperName(pkgInfo.packageName),
            icon = appInfo.loadIcon(pm),
            versionName = pkgInfo.versionName,
            installSource = getInstallSource(pkgInfo.packageName, isSystem),
            firstInstallTime = pkgInfo.firstInstallTime,
            lastUpdateTime = pkgInfo.lastUpdateTime,
            isSystemApp = isSystem,
            requestedPermissions = mapPermissions(pkgInfo.requestedPermissions ?: emptyArray()),
            hasLauncherIcon = pm.getLaunchIntentForPackage(pkgInfo.packageName) != null
        )
    }

    suspend fun analyzeApp(apiKey: String, app: InstalledAppInfo): Result<AppAnalysisResult> = withContext(Dispatchers.IO) {
        runCatching {
            val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
            val permissionsList = if (app.requestedPermissions.isEmpty()) "No special permissions"
            else app.requestedPermissions.joinToString("\n") { "- ${it.emoji} ${it.label}" }

            val prompt = APP_ANALYSIS_PROMPT_TEMPLATE
                .replace("{appName}", app.appName)
                .replace("{packageName}", app.packageName)
                .replace("{developer}", app.developerName ?: app.packageName.split(".").take(3).joinToString("."))
                .replace("{version}", app.versionName ?: "Unknown")
                .replace("{installSource}", app.installSource.label)
                .replace("{isSystemApp}", app.isSystemApp.toString())
                .replace("{installDate}", dateFormat.format(Date(app.firstInstallTime)))
                .replace("{updateDate}", dateFormat.format(Date(app.lastUpdateTime)))
                .replace("{permissionsList}", permissionsList)

            val request = ClaudeRequest(
                messages = listOf(ClaudeMessage(role = "user", content = prompt)),
                system = APP_ANALYSIS_SYSTEM_PROMPT,
                maxTokens = 1024
            )
            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            if (!response.isSuccessful) throw Exception("API error: ${response.code()}")

            val rawText = response.body()?.text ?: throw Exception("Empty response")
            val json = gson.fromJson(extractJson(rawText), AppAnalysisJson::class.java)

            val result = AppAnalysisResult(
                verdict = json.verdict.uppercase(),
                appDescription = json.app_description,
                summary = json.summary,
                details = json.details,
                whatToDoNext = json.what_to_do_next,
                knownApp = json.known_app,
                knownDeveloper = json.known_developer,
                permissionConcerns = json.permission_concerns
            )

            // Save to safety check history
            safetyCheckResultDao.insert(
                SafetyCheckResultEntity(
                    contentType = "APP",
                    contentPreview = "${app.appName} (${app.packageName})",
                    verdict = result.verdict,
                    summary = result.summary,
                    detailJson = gson.toJson(result)
                )
            )

            result
        }
    }

    private fun getInstallSource(packageName: String, isSystem: Boolean): InstallSource {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } catch (_: Exception) { null }
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName)
        }

        return when {
            installer == "com.android.vending" -> InstallSource.PLAY_STORE
            installer == "com.sec.android.app.samsungapps" -> InstallSource.SAMSUNG_STORE
            installer == "com.amazon.venezia" -> InstallSource.AMAZON_STORE
            isSystem -> InstallSource.PRE_INSTALLED
            installer == null -> InstallSource.SIDELOADED
            else -> InstallSource.UNKNOWN_SOURCE
        }
    }

    private fun getDeveloperName(packageName: String): String? {
        val parts = packageName.split(".")
        return when {
            packageName.startsWith("com.google.") -> "Google"
            packageName.startsWith("com.samsung.") || packageName.startsWith("com.sec.") -> "Samsung"
            packageName.startsWith("com.android.") -> "Android"
            packageName.startsWith("com.meta.") || packageName.startsWith("com.facebook.") -> "Meta"
            packageName.startsWith("com.microsoft.") -> "Microsoft"
            packageName.startsWith("com.whatsapp") -> "Meta (WhatsApp)"
            packageName.startsWith("com.spotify.") -> "Spotify"
            packageName.startsWith("org.mozilla.") -> "Mozilla"
            packageName.startsWith("com.amazon.") -> "Amazon"
            packageName.startsWith("com.apple.") -> "Apple"
            parts.size >= 2 -> parts[1].replaceFirstChar { it.uppercase() }
            else -> null
        }
    }

    companion object {
        val PERMISSION_LABELS = mapOf(
            "android.permission.CAMERA" to
                AppPermission("android.permission.CAMERA", "Can use your camera", "\uD83D\uDCF7", PermissionRisk.HIGH),
            "android.permission.RECORD_AUDIO" to
                AppPermission("android.permission.RECORD_AUDIO", "Can use your microphone", "\uD83C\uDFA4", PermissionRisk.HIGH),
            "android.permission.ACCESS_FINE_LOCATION" to
                AppPermission("android.permission.ACCESS_FINE_LOCATION", "Can see your exact location", "\uD83D\uDCCD", PermissionRisk.HIGH),
            "android.permission.ACCESS_COARSE_LOCATION" to
                AppPermission("android.permission.ACCESS_COARSE_LOCATION", "Can see your approximate location", "\uD83D\uDCCD", PermissionRisk.MEDIUM),
            "android.permission.READ_CONTACTS" to
                AppPermission("android.permission.READ_CONTACTS", "Can read your contacts", "\uD83D\uDCC7", PermissionRisk.HIGH),
            "android.permission.READ_SMS" to
                AppPermission("android.permission.READ_SMS", "Can read your text messages", "\uD83D\uDCAC", PermissionRisk.HIGH),
            "android.permission.SEND_SMS" to
                AppPermission("android.permission.SEND_SMS", "Can send text messages", "\uD83D\uDCAC", PermissionRisk.HIGH),
            "android.permission.READ_CALL_LOG" to
                AppPermission("android.permission.READ_CALL_LOG", "Can see your call history", "\uD83D\uDCDE", PermissionRisk.HIGH),
            "android.permission.CALL_PHONE" to
                AppPermission("android.permission.CALL_PHONE", "Can make phone calls", "\uD83D\uDCF1", PermissionRisk.MEDIUM),
            "android.permission.READ_EXTERNAL_STORAGE" to
                AppPermission("android.permission.READ_EXTERNAL_STORAGE", "Can access your files and photos", "\uD83D\uDCC2", PermissionRisk.MEDIUM),
            "android.permission.WRITE_EXTERNAL_STORAGE" to
                AppPermission("android.permission.WRITE_EXTERNAL_STORAGE", "Can change your files and photos", "\uD83D\uDCC2", PermissionRisk.MEDIUM),
            "android.permission.INTERNET" to
                AppPermission("android.permission.INTERNET", "Can access the internet", "\uD83C\uDF10", PermissionRisk.LOW),
            "android.permission.ACCESS_WIFI_STATE" to
                AppPermission("android.permission.ACCESS_WIFI_STATE", "Can see your WiFi connection", "\uD83D\uDCF6", PermissionRisk.LOW),
            "android.permission.RECEIVE_BOOT_COMPLETED" to
                AppPermission("android.permission.RECEIVE_BOOT_COMPLETED", "Starts automatically when phone turns on", "\uD83D\uDD04", PermissionRisk.LOW),
            "android.permission.SYSTEM_ALERT_WINDOW" to
                AppPermission("android.permission.SYSTEM_ALERT_WINDOW", "Can show pop-ups over other apps", "\u26A0\uFE0F", PermissionRisk.HIGH),
            "android.permission.READ_PHONE_STATE" to
                AppPermission("android.permission.READ_PHONE_STATE", "Can read your phone number and status", "\uD83D\uDCF1", PermissionRisk.MEDIUM),
            "android.permission.BIND_ACCESSIBILITY_SERVICE" to
                AppPermission("android.permission.BIND_ACCESSIBILITY_SERVICE", "Has full screen access (accessibility)", "\uD83D\uDC41\uFE0F", PermissionRisk.HIGH),
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to
                AppPermission("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "Can read all your notifications", "\uD83D\uDD14", PermissionRisk.HIGH),
            "android.permission.READ_MEDIA_IMAGES" to
                AppPermission("android.permission.READ_MEDIA_IMAGES", "Can see your photos", "\uD83D\uDDBC\uFE0F", PermissionRisk.MEDIUM),
            "android.permission.READ_MEDIA_VIDEO" to
                AppPermission("android.permission.READ_MEDIA_VIDEO", "Can see your videos", "\uD83C\uDFA5", PermissionRisk.MEDIUM),
            "android.permission.POST_NOTIFICATIONS" to
                AppPermission("android.permission.POST_NOTIFICATIONS", "Can send you notifications", "\uD83D\uDD14", PermissionRisk.LOW),
            "android.permission.FOREGROUND_SERVICE" to
                AppPermission("android.permission.FOREGROUND_SERVICE", "Can run in the background", "\u2699\uFE0F", PermissionRisk.LOW),
            "android.permission.REQUEST_INSTALL_PACKAGES" to
                AppPermission("android.permission.REQUEST_INSTALL_PACKAGES", "Can install other apps", "\u26A0\uFE0F", PermissionRisk.HIGH)
        )

        fun mapPermissions(permissions: Array<String>): List<AppPermission> {
            return permissions.mapNotNull { perm ->
                PERMISSION_LABELS[perm]
            }.sortedByDescending { it.riskLevel.ordinal }
        }
    }
}

data class AppAnalysisResult(
    val verdict: String = "SAFE",
    val appDescription: String = "",
    val summary: String = "",
    val details: String = "",
    val whatToDoNext: String = "",
    val knownApp: Boolean = false,
    val knownDeveloper: String? = null,
    val permissionConcerns: List<String> = emptyList()
)

private data class AppAnalysisJson(
    val verdict: String = "SAFE",
    val app_description: String = "",
    val summary: String = "",
    val details: String = "",
    val what_to_do_next: String = "",
    val known_app: Boolean = false,
    val known_developer: String? = null,
    val permission_concerns: List<String> = emptyList()
)

private const val APP_ANALYSIS_SYSTEM_PROMPT = """You are a security analyst for Safe Companion, an app that protects elderly users.
You analyze installed Android apps and explain in plain, simple English whether they are safe. Your audience is seniors who are not tech-savvy.

IMPORTANT RULES:
- Use simple words. No jargon.
- Be reassuring for known safe apps ("This is a well-known app from Google")
- Be clear and direct for risky apps ("This app might not be safe")
- Never use the word "malware" — say "harmful software" or "dangerous app"
- If the app is from a major company (Google, Samsung, Meta, Microsoft, Apple), say so clearly
- If the app has suspicious permissions for what it claims to do, explain WHY that's unusual in simple terms"""

private const val APP_ANALYSIS_PROMPT_TEMPLATE = """Please analyze this Android app and determine if it is safe for an elderly user.

App Name: {appName}
Package Name: {packageName}
Developer/Package Origin: {developer}
Version: {version}
Install Source: {installSource}
Is System App: {isSystemApp}
First Installed: {installDate}
Last Updated: {updateDate}

Permissions this app has:
{permissionsList}

Please respond with ONLY this JSON (no other text):
{
  "verdict": "SAFE" or "SUSPICIOUS" or "DANGEROUS",
  "app_description": "One sentence explaining what this app actually does, in simple English",
  "summary": "One sentence safety summary for a senior citizen",
  "details": "2-3 sentences about what you found. Mention the developer, whether it's well-known, and if the permissions seem appropriate for what the app does.",
  "what_to_do_next": "Clear, specific action steps. For safe apps: 'No action needed.' For suspicious: 'Consider disabling this app.' For dangerous: 'We recommend uninstalling this app right away.'",
  "known_app": true/false,
  "known_developer": "Google" or "Samsung" or null,
  "permission_concerns": ["list of specific permission concerns in plain English"] or []
}

Common well-known packages to recognize as SAFE:
- com.google.* (Google apps — Gmail, Chrome, Maps, YouTube, etc.)
- com.samsung.* (Samsung apps — Galaxy Store, Samsung Internet, etc.)
- com.android.* (Android system — Settings, Phone, Messages, etc.)
- com.meta.* or com.facebook.* (Meta/Facebook/Instagram/WhatsApp)
- com.microsoft.* (Microsoft — Outlook, Teams, Office, etc.)
- com.whatsapp (WhatsApp)
- com.spotify.* (Spotify)
- org.mozilla.* (Firefox)
- com.amazon.* (Amazon)

Apps that are SUSPICIOUS if the user didn't intentionally install them:
- Any app with no Play Store listing AND high permissions
- Apps that auto-start + read SMS + access internet (classic spyware pattern)
- "Cleaner", "Booster", "Battery Saver", "Free VPN" apps with excessive permissions
- Apps with generic names like "System Update", "Inbox", "Settings" that are NOT from the actual system manufacturer
- Apps requesting accessibility + notification listener for no clear reason

Apps that are DANGEROUS:
- Known malware package names
- Apps requesting SMS + internet + contacts + accessibility with no legitimate reason
- Apps impersonating system apps (fake package names mimicking com.android.*)
- Apps with names designed to confuse ("Google Update Service", "System UI") that aren't actually from Google/Samsung/Android"""
