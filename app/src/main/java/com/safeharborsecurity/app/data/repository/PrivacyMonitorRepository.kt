package com.safeharborsecurity.app.data.repository

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of a single privacy threat detection.
 */
data class PrivacyThreat(
    val packageName: String,
    val appName: String,
    val reason: String,
    val category: ThreatCategory,
    val actionLabel: String = "Go to Settings",
    val settingsAction: String? = null
)

enum class ThreatCategory(val section: String, val sectionEmoji: String) {
    MICROPHONE_ACCESS("Microphone Access", "🎤"),
    BACKGROUND_MIC("Microphone Access", "🎤"),
    CAMERA_ACCESS("Camera Access", "📷"),
    SCREEN_ACCESS("Screen Access", "🖥️"),
    LOCATION_ACCESS("Location Access", "📍"),
    CONTACTS_ACCESS("Clipboard & Data Access", "📋"),
    OVERLAY_APP("Device Control", "🔐"),
    DEVICE_ADMIN("Device Control", "🔐"),
    VPN_APP("Network & VPN", "🌐"),
    AD_SERVICES("Microphone Access", "🎤"),
    ACCESSIBILITY_SERVICE("Screen Access", "🖥️"),
    TRACKING_SDK("Clipboard & Data Access", "📋")
}

data class PrivacyScanResult(
    val threats: List<PrivacyThreat>,
    val scanTimeMillis: Long = System.currentTimeMillis()
)

/** Known ad-listening / tracking package prefixes. */
private val KNOWN_TRACKING_PREFIXES = listOf(
    "com.google.android.adservices",
    "com.facebook.ads",
    "com.appsflyer",
    "com.adjust.sdk",
    "com.mopub",
    "com.inmobi",
    "com.unity3d.ads",
    "com.ironsource",
    "com.startapp",
    "com.chartboost"
)

/** System packages or well-known safe accessibility services. */
private val SAFE_ACCESSIBILITY_PACKAGES = setOf(
    "com.google.android.marvin.talkback",   // TalkBack
    "com.samsung.accessibility",
    "com.google.android.apps.accessibility.voiceaccess",
    "com.android.switchaccess.SwitchAccessService"
)

@Singleton
class PrivacyMonitorRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager: PackageManager = context.packageManager
    private val appOpsManager: AppOpsManager =
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    /**
     * Runs all privacy checks and returns a combined result.
     */
    fun performFullScan(): PrivacyScanResult {
        val threats = mutableListOf<PrivacyThreat>()

        threats.addAll(checkRecentMicrophoneUsage())
        threats.addAll(checkAdServices())
        threats.addAll(checkAccessibilityServices())
        threats.addAll(checkBackgroundMicAccess())
        threats.addAll(checkKnownTrackingSdks())
        threats.addAll(checkCameraAccess())
        threats.addAll(checkOverlayApps())
        threats.addAll(checkBackgroundLocation())
        threats.addAll(checkContactsAccess())
        threats.addAll(checkDeviceAdmins())
        threats.addAll(checkActiveVpn())

        // Deduplicate by package name + category
        val deduplicated = threats.distinctBy { it.packageName + it.category }

        return PrivacyScanResult(threats = deduplicated)
    }

    /**
     * 1. Microphone usage — checks which apps have recently accessed the microphone
     *    via AppOpsManager (OP_RECORD_AUDIO).
     */
    private fun checkRecentMicrophoneUsage(): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()
        try {
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (pkg in installedPackages) {
                if (isSystemApp(pkg.applicationInfo)) continue
                if (pkg.packageName == context.packageName) continue

                val mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_RECORD_AUDIO,
                    pkg.applicationInfo?.uid ?: continue,
                    pkg.packageName
                )
                if (mode == AppOpsManager.MODE_ALLOWED) {
                    // This app has microphone permission granted — flag it
                    threats.add(
                        PrivacyThreat(
                            packageName = pkg.packageName,
                            appName = getAppLabel(pkg.packageName),
                            reason = "This app has access to your microphone",
                            category = ThreatCategory.MICROPHONE_ACCESS,
                            settingsAction = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful degradation — if we can't check, don't crash
        }
        return threats
    }

    /**
     * 2. Android Ad Services / Privacy Sandbox — check if the ad services package is enabled.
     */
    private fun checkAdServices(): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()
        try {
            val adServicesInfo = try {
                packageManager.getPackageInfo("com.google.android.adservices.api", 0)
            } catch (_: PackageManager.NameNotFoundException) {
                try {
                    packageManager.getPackageInfo("com.google.android.adservices", 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }

            if (adServicesInfo != null) {
                val appInfo = adServicesInfo.applicationInfo
                if (appInfo != null && appInfo.enabled) {
                    threats.add(
                        PrivacyThreat(
                            packageName = adServicesInfo.packageName,
                            appName = "Android Ad Services",
                            reason = "Ad services are turned on. Companies may use this to show you targeted ads based on your activity. You can turn this off in your phone settings.",
                            category = ThreatCategory.AD_SERVICES,
                            actionLabel = "Open Privacy Settings",
                            settingsAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                "android.adservices.ui.SETTINGS"
                            else
                                android.provider.Settings.ACTION_PRIVACY_SETTINGS
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Ad services package not present — no threat
        }
        return threats
    }

    /**
     * 3. Accessibility Services — enumerate enabled accessibility services and flag any
     *    that are not system services or known safe apps.
     */
    private fun checkAccessibilityServices(): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()
        try {
            val accessibilityManager =
                context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

            for (service in enabledServices) {
                val serviceInfo = service.resolveInfo?.serviceInfo ?: continue
                val pkg = serviceInfo.packageName

                // Skip system apps and known safe accessibility services
                if (SAFE_ACCESSIBILITY_PACKAGES.any { pkg.startsWith(it) }) continue
                try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    if (isSystemApp(appInfo)) continue
                } catch (_: PackageManager.NameNotFoundException) {
                    continue
                }

                threats.add(
                    PrivacyThreat(
                        packageName = pkg,
                        appName = getAppLabel(pkg),
                        reason = "This app has accessibility access, which means it can see what's on your screen and hear what you type. Make sure you trust it.",
                        category = ThreatCategory.ACCESSIBILITY_SERVICE,
                        actionLabel = "Review in Settings",
                        settingsAction = android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
                    )
                )
            }
        } catch (_: Exception) {
            // Graceful degradation
        }
        return threats
    }

    /**
     * 4. Background microphone access — find apps granted RECORD_AUDIO permission
     *    that have used it in the background recently.
     */
    private fun checkBackgroundMicAccess(): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()

        try {
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (pkg in installedPackages) {
                if (isSystemApp(pkg.applicationInfo)) continue
                if (pkg.packageName == context.packageName) continue

                // Check if the app has RECORD_AUDIO permission
                val permissions = pkg.requestedPermissions ?: continue
                if (android.Manifest.permission.RECORD_AUDIO !in permissions) continue

                // Check if the app can record audio in the background
                val uid = pkg.applicationInfo?.uid ?: continue
                val mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_RECORD_AUDIO, uid, pkg.packageName
                )

                if (mode == AppOpsManager.MODE_ALLOWED) {
                    // Check if the app also has a background service or runs in background
                    val hasBackgroundPermission = permissions.any {
                        it == "android.permission.FOREGROUND_SERVICE" ||
                        it == "android.permission.RECEIVE_BOOT_COMPLETED"
                    }
                    if (hasBackgroundPermission) {
                        threats.add(
                            PrivacyThreat(
                                packageName = pkg.packageName,
                                appName = getAppLabel(pkg.packageName),
                                reason = "This app can use your microphone in the background. It might be listening when you're not using it.",
                                category = ThreatCategory.BACKGROUND_MIC,
                                settingsAction = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful degradation
        }
        return threats
    }

    /**
     * 5. Known ad/tracking SDKs — check if known tracking packages are installed and active.
     */
    private fun checkKnownTrackingSdks(): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()
        try {
            val installedPackages = packageManager.getInstalledPackages(0)
            for (pkg in installedPackages) {
                val matchedPrefix = KNOWN_TRACKING_PREFIXES.firstOrNull {
                    pkg.packageName.startsWith(it)
                } ?: continue

                // Skip if already flagged by ad services check
                if (matchedPrefix == "com.google.android.adservices") continue

                val appInfo = pkg.applicationInfo ?: continue
                if (appInfo.enabled) {
                    threats.add(
                        PrivacyThreat(
                            packageName = pkg.packageName,
                            appName = getAppLabel(pkg.packageName),
                            reason = "This is a known advertising or tracking app that may collect data about you.",
                            category = ThreatCategory.TRACKING_SDK,
                            settingsAction = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful degradation
        }
        return threats
    }

    /** 6. Camera access — apps with OP_CAMERA allowed. */
    private fun checkCameraAccess(): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()
        try {
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (pkg in installedPackages) {
                if (isSystemApp(pkg.applicationInfo)) continue
                if (pkg.packageName == context.packageName) continue

                val permissions = pkg.requestedPermissions ?: continue
                if (android.Manifest.permission.CAMERA !in permissions) continue

                val uid = pkg.applicationInfo?.uid ?: continue
                val mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_CAMERA, uid, pkg.packageName
                )
                if (mode == AppOpsManager.MODE_ALLOWED) {
                    threats.add(
                        PrivacyThreat(
                            packageName = pkg.packageName,
                            appName = getAppLabel(pkg.packageName),
                            reason = "This app has access to your camera and could take photos or videos.",
                            category = ThreatCategory.CAMERA_ACCESS,
                            settingsAction = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return threats
    }

    /** 7. Overlay apps — apps with SYSTEM_ALERT_WINDOW permission. */
    private fun checkOverlayApps(): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()
        try {
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (pkg in installedPackages) {
                if (isSystemApp(pkg.applicationInfo)) continue
                if (pkg.packageName == context.packageName) continue

                val permissions = pkg.requestedPermissions ?: continue
                if (android.Manifest.permission.SYSTEM_ALERT_WINDOW !in permissions) continue

                val uid = pkg.applicationInfo?.uid ?: continue
                val mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg.packageName
                )
                if (mode == AppOpsManager.MODE_ALLOWED) {
                    threats.add(
                        PrivacyThreat(
                            packageName = pkg.packageName,
                            appName = getAppLabel(pkg.packageName),
                            reason = "This app can draw over other apps. It could show fake screens or hide things from you.",
                            category = ThreatCategory.SCREEN_ACCESS,
                            settingsAction = android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return threats
    }

    /** 8. Background location — apps with location access. */
    private fun checkBackgroundLocation(): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()
        try {
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (pkg in installedPackages) {
                if (isSystemApp(pkg.applicationInfo)) continue
                if (pkg.packageName == context.packageName) continue

                val permissions = pkg.requestedPermissions ?: continue
                val hasBackgroundLocation = "android.permission.ACCESS_BACKGROUND_LOCATION" in permissions

                if (!hasBackgroundLocation) continue

                val uid = pkg.applicationInfo?.uid ?: continue
                val mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_FINE_LOCATION, uid, pkg.packageName
                )
                if (mode == AppOpsManager.MODE_ALLOWED) {
                    threats.add(
                        PrivacyThreat(
                            packageName = pkg.packageName,
                            appName = getAppLabel(pkg.packageName),
                            reason = "This app can track your location even when you're not using it.",
                            category = ThreatCategory.LOCATION_ACCESS,
                            settingsAction = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return threats
    }

    /** 9. Contacts & call log access — apps reading your contacts in background. */
    private fun checkContactsAccess(): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()
        try {
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (pkg in installedPackages) {
                if (isSystemApp(pkg.applicationInfo)) continue
                if (pkg.packageName == context.packageName) continue

                val permissions = pkg.requestedPermissions ?: continue
                val readsContacts = android.Manifest.permission.READ_CONTACTS in permissions
                val readsCallLog = android.Manifest.permission.READ_CALL_LOG in permissions

                if (!readsContacts && !readsCallLog) continue

                val uid = pkg.applicationInfo?.uid ?: continue
                val hasContactAccess = if (readsContacts) {
                    appOpsManager.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_READ_CONTACTS, uid, pkg.packageName
                    ) == AppOpsManager.MODE_ALLOWED
                } else false

                val hasCallLogAccess = if (readsCallLog) {
                    appOpsManager.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_READ_CALL_LOG, uid, pkg.packageName
                    ) == AppOpsManager.MODE_ALLOWED
                } else false

                if (hasContactAccess || hasCallLogAccess) {
                    val what = when {
                        hasContactAccess && hasCallLogAccess -> "contacts and call history"
                        hasContactAccess -> "contacts"
                        else -> "call history"
                    }
                    threats.add(
                        PrivacyThreat(
                            packageName = pkg.packageName,
                            appName = getAppLabel(pkg.packageName),
                            reason = "This app can read your $what.",
                            category = ThreatCategory.CONTACTS_ACCESS,
                            settingsAction = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return threats
    }

    /** 10. Device admin apps — flag non-standard device administrators. */
    private fun checkDeviceAdmins(): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admins = dpm.activeAdmins ?: return threats

            val safeAdminPrefixes = setOf(
                "com.google.", "com.android.", "com.samsung.", "com.microsoft.intune"
            )

            for (admin in admins) {
                val pkg = admin.packageName
                if (safeAdminPrefixes.any { pkg.startsWith(it) }) continue

                threats.add(
                    PrivacyThreat(
                        packageName = pkg,
                        appName = getAppLabel(pkg),
                        reason = "This app is a device administrator. It can lock your phone, erase data, or change settings. Make sure you trust it.",
                        category = ThreatCategory.DEVICE_ADMIN,
                        actionLabel = "Review Admins",
                        settingsAction = Settings.ACTION_SECURITY_SETTINGS
                    )
                )
            }
        } catch (_: Exception) { }
        return threats
    }

    /** 11. Active VPN — check if an unknown VPN is active. */
    private fun checkActiveVpn(): List<PrivacyThreat> {
        val threats = mutableListOf<PrivacyThreat>()
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return threats
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return threats

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                // Try to identify the VPN app
                val vpnPackages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                    .filter { pkg ->
                        val perms = pkg.requestedPermissions ?: emptyArray()
                        "android.permission.BIND_VPN_SERVICE" in perms && !isSystemApp(pkg.applicationInfo)
                    }

                val safeVpnPrefixes = setOf(
                    "com.google.", "com.android.", "com.nordvpn", "com.expressvpn",
                    "com.privateinternetaccess", "net.mullvad", "com.wireguard"
                )

                for (pkg in vpnPackages) {
                    if (safeVpnPrefixes.any { pkg.packageName.startsWith(it) }) continue
                    threats.add(
                        PrivacyThreat(
                            packageName = pkg.packageName,
                            appName = getAppLabel(pkg.packageName),
                            reason = "A VPN is active on your phone. All your internet traffic goes through this app. Make sure you trust it.",
                            category = ThreatCategory.VPN_APP,
                            settingsAction = Settings.ACTION_VPN_SETTINGS
                        )
                    )
                }

                // If no specific VPN app found but VPN is active
                if (vpnPackages.isEmpty() || threats.none { it.category == ThreatCategory.VPN_APP }) {
                    threats.add(
                        PrivacyThreat(
                            packageName = "android.vpn",
                            appName = "Active VPN Connection",
                            reason = "A VPN is running on your phone. All your internet traffic goes through it. Check Settings to see which app is responsible.",
                            category = ThreatCategory.VPN_APP,
                            settingsAction = Settings.ACTION_VPN_SETTINGS
                        )
                    )
                }
            }
        } catch (_: Exception) { }
        return threats
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun isSystemApp(appInfo: ApplicationInfo?): Boolean {
        if (appInfo == null) return true
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }
}
