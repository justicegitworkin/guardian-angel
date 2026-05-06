package com.safeharborsecurity.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safeharborsecurity.app.MainActivity
import com.safeharborsecurity.app.R
import com.safeharborsecurity.app.SafeHarborApp
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.repository.AppCheckerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AppCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val appCheckerRepository: AppCheckerRepository,
    private val prefs: UserPreferences
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AppCheckWorker"
    }

    override suspend fun doWork(): Result {
        val packageName = inputData.getString("packageName") ?: return Result.failure()

        // Check if auto-check is enabled
        val enabled = prefs.isAutoCheckNewApps.first()
        if (!enabled) {
            Log.d(TAG, "Auto-check disabled, skipping $packageName")
            return Result.success()
        }

        val apiKey = prefs.apiKey.first()
        if (apiKey.isBlank()) {
            Log.d(TAG, "No API key, skipping auto-check for $packageName")
            return Result.success()
        }

        Log.d(TAG, "Auto-checking newly installed app: $packageName")

        val appInfo = appCheckerRepository.getAppInfo(packageName) ?: return Result.success()
        val result = appCheckerRepository.analyzeApp(apiKey, appInfo)

        result.onSuccess { analysis ->
            if (analysis.verdict == "SUSPICIOUS" || analysis.verdict == "DANGEROUS") {
                showWarningNotification(appInfo.appName, packageName, analysis.verdict, analysis.summary)
            } else {
                Log.d(TAG, "App $packageName is SAFE")
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to check $packageName: ${e.message}")
        }

        return Result.success()
    }

    private fun showWarningNotification(appName: String, packageName: String, verdict: String, summary: String) {
        val detailIntent = Intent(applicationContext, MainActivity::class.java).apply {
            data = android.net.Uri.parse("safeharbor://app_detail?packageName=${java.net.URLEncoder.encode(packageName, "UTF-8")}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, packageName.hashCode(), detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val emoji = if (verdict == "DANGEROUS") "\uD83D\uDEA8" else "\u26A0\uFE0F"
        val notification = NotificationCompat.Builder(applicationContext, SafeHarborApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$emoji New app may not be safe: $appName")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        manager.notify(packageName.hashCode(), notification)
    }
}
