package com.safeharborsecurity.app.data.model

import android.graphics.drawable.Drawable

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val developerName: String?,
    val icon: Drawable,
    val versionName: String?,
    val installSource: InstallSource,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isSystemApp: Boolean,
    val requestedPermissions: List<AppPermission>,
    val hasLauncherIcon: Boolean
)

enum class InstallSource(val label: String) {
    PLAY_STORE("Play Store"),
    SAMSUNG_STORE("Galaxy Store"),
    AMAZON_STORE("Amazon Appstore"),
    PRE_INSTALLED("Pre-installed"),
    UNKNOWN_SOURCE("Unknown Source"),
    SIDELOADED("Sideloaded")
}

data class AppPermission(
    val permission: String,
    val label: String,
    val emoji: String,
    val riskLevel: PermissionRisk
)

enum class PermissionRisk { LOW, MEDIUM, HIGH }
