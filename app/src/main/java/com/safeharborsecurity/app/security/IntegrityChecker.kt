package com.safeharborsecurity.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Part C1 + C4: Runtime checks to detect tampering, rooting, emulators, and
 * debugging. Each check returns a risk level. The app degrades gracefully —
 * no hard crash on rooted devices so legitimate users still get core protection.
 */
object IntegrityChecker {

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    data class IntegrityReport(
        val isRooted: Boolean,
        val isEmulator: Boolean,
        val isDebuggable: Boolean,
        val isDebuggerAttached: Boolean,
        val isTampered: Boolean,
        val riskLevel: RiskLevel
    )

    fun check(context: Context): IntegrityReport {
        val rooted = checkRoot()
        val emulator = checkEmulator()
        val debuggable = checkDebuggable(context)
        val debuggerAttached = android.os.Debug.isDebuggerConnected()
        val tampered = checkTampering(context)

        val riskScore = listOf(rooted, emulator, debuggable, debuggerAttached, tampered)
            .count { it }
        val riskLevel = when {
            riskScore >= 3 -> RiskLevel.HIGH
            riskScore >= 1 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        return IntegrityReport(
            isRooted = rooted,
            isEmulator = emulator,
            isDebuggable = debuggable,
            isDebuggerAttached = debuggerAttached,
            isTampered = tampered,
            riskLevel = riskLevel
        )
    }

    private fun checkRoot(): Boolean {
        val rootPaths = listOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su",
            "/su/bin/su", "/system/app/SuperSU.apk", "/system/app/SuperSU",
            "/system/app/Magisk.apk", "/sbin/magisk"
        )
        if (rootPaths.any { File(it).exists() }) return true
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val line = process.inputStream.bufferedReader().readLine()
            process.destroy()
            !line.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun checkEmulator(): Boolean {
        return (Build.FINGERPRINT.contains("generic")
                || Build.FINGERPRINT.contains("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.BOARD == "QC_Reference_Phone"
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HOST.startsWith("Build")
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic")
                || Build.PRODUCT.contains("sdk_gphone")
                || Build.PRODUCT.contains("vbox86p")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu"))
    }

    private fun checkDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun checkTampering(context: Context): Boolean {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            val allowedInstallers = setOf(
                "com.android.vending",
                "com.sec.android.app.samsungapps",
                "com.amazon.venezia",
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
                null
            )
            installer !in allowedInstallers
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Part C4: Compare the app's signing certificate to a known-good SHA-256.
     * Returns true only if at least one signer matches `expectedSha256`
     * (case-insensitive hex string). When `expectedSha256` is blank we skip
     * the check (returns true) — useful for debug builds where the signing
     * cert is the Android debug keystore.
     */
    fun verifySignature(context: Context, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return true
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNATURES
                )
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            signatures?.any { sig ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(sig.toByteArray())
                val hexHash = hash.joinToString("") { "%02x".format(it) }
                hexHash == expectedSha256.lowercase()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
