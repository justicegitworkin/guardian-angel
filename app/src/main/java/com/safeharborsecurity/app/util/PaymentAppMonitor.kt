package com.safeharborsecurity.app.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.notification.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replacement for the previous PaymentWarningAccessibilityService.
 *
 * Google Play heavily restricts AccessibilityService use to apps whose core
 * function is helping users with disabilities. A "show a payment safety
 * reminder when the user opens Venmo" feature does not qualify.
 *
 * This monitor uses UsageStatsManager (PACKAGE_USAGE_STATS, a special permission
 * the user grants from system Settings) to detect when a payment app comes to
 * the foreground, then posts a one-shot reminder notification — same UX, no
 * accessibility service required.
 *
 * The poll runs from the GuardianService foreground service when the user has
 * the payment warning toggle on AND has granted Usage Access. Both are opt-in.
 */
@Singleton
class PaymentAppMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val notificationHelper: NotificationHelper
) {
    companion object {
        private val PAYMENT_PACKAGES = setOf(
            "com.venmo",
            "com.zellepay.zelle",
            "com.squareup.cash",
            "com.paypal.android.p2pmobile",
            "com.google.android.apps.walletnfcrel",
            "com.samsung.android.spay"
        )
        private const val POLL_INTERVAL_MS = 30_000L          // check every 30 s
        private const val WINDOW_MS = 60_000L                  // look back 60 s
        private const val PER_APP_COOLDOWN_MS = 30L * 60_000L  // 30 min between reminders per app
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val lastReminded = mutableMapOf<String, Long>()

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                tick()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun tick() {
        if (!userPreferences.isPaymentWarningEnabled.first()) return
        if (!hasUsageAccess()) return
        val foreground = currentForegroundPackage() ?: return
        if (foreground !in PAYMENT_PACKAGES) return
        val now = System.currentTimeMillis()
        val last = lastReminded[foreground] ?: 0L
        if (now - last < PER_APP_COOLDOWN_MS) return
        lastReminded[foreground] = now
        notificationHelper.showPaymentReminder()
    }

    private fun currentForegroundPackage(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val end = System.currentTimeMillis()
        val start = end - WINDOW_MS
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end) ?: return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Helper for the UI: open the system Usage Access settings page. */
    fun openUsageAccessSettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (_: Exception) {}
    }
}
