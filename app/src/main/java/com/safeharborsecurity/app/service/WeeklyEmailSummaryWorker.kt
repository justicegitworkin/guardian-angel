package com.safeharborsecurity.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.safeharborsecurity.app.MainActivity
import com.safeharborsecurity.app.R
import com.safeharborsecurity.app.SafeHarborApp
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.data.repository.AlertRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Part G3: Weekly digest of email alerts. Posts a summary notification on
 * Sunday at 9 AM that breaks down phishing/spam/legit counts and lists the
 * most common threats.
 */
@HiltWorker
class WeeklyEmailSummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val alertRepository: AlertRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_TAG = "weekly_email_summary"
        private const val NOTIF_ID = 5556

        fun enqueueWeekly(context: Context) {
            val work = PeriodicWorkRequestBuilder<WeeklyEmailSummaryWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(calculateDelayToSunday9AM(), TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }

        private fun calculateDelayToSunday9AM(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result {
        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val alerts = alertRepository.getAlertsByTypeSince("EMAIL", weekAgo)
        if (alerts.isEmpty()) return Result.success()

        val summary = buildEmailSummary(alerts)
        showSummaryNotification(summary)
        return Result.success()
    }

    private fun buildEmailSummary(alerts: List<AlertEntity>): String {
        val blocked = alerts.count { it.riskLevel == "SCAM" }
        val warnings = alerts.count { it.riskLevel == "WARNING" }
        val safe = alerts.count { it.riskLevel == "SAFE" }
        val topThreats = topReasonPhrases(alerts)

        return buildString {
            appendLine("This week Safe Companion checked ${alerts.size} emails:")
            appendLine("• $blocked scam/phishing emails blocked")
            appendLine("• $warnings suspicious emails flagged")
            appendLine("• $safe emails looked safe")
            if (topThreats.isNotEmpty()) {
                appendLine()
                appendLine("Most common threats: $topThreats")
            }
            appendLine()
            append("Keep being careful with emails from unknown senders!")
        }
    }

    private fun topReasonPhrases(alerts: List<AlertEntity>): String {
        val keywords = listOf(
            "phishing" to "phishing",
            "fake invoice" to "fake invoices",
            "wire" to "wire-transfer scams",
            "gift card" to "gift-card scams",
            "bank" to "bank impersonation",
            "irs" to "IRS impersonation",
            "amazon" to "Amazon impersonation",
            "delivery" to "fake delivery notices",
            "refund" to "refund scams",
            "lottery" to "lottery scams",
            "crypto" to "crypto scams"
        )
        val counts = mutableMapOf<String, Int>()
        for (alert in alerts) {
            val text = ("${alert.reason} ${alert.content}").lowercase()
            for ((needle, label) in keywords) {
                if (text.contains(needle)) counts.merge(label, 1, Int::plus)
            }
        }
        return counts.entries.sortedByDescending { it.value }.take(3)
            .joinToString(", ") { it.key }
    }

    private fun showSummaryNotification(summary: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            data = android.net.Uri.parse("safeharbor://messages?tab=emails")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, NOTIF_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, SafeHarborApp.CHANNEL_REPORT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your Weekly Email Safety Report")
            .setContentText("Tap to see what Safe Companion found this week.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, notification)
    }
}
