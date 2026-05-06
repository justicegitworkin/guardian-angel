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
import com.safeharborsecurity.app.data.local.dao.AlertDao
import com.safeharborsecurity.app.data.local.dao.CallLogDao
import com.safeharborsecurity.app.data.local.dao.PanicEventDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class WeeklyReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val alertDao: AlertDao,
    private val callLogDao: CallLogDao,
    private val panicEventDao: PanicEventDao
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_TAG = "weekly_report"

        fun enqueueWeekly(context: Context) {
            val work = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(calculateDelayToSunday8AM(), TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }

        private fun calculateDelayToSunday8AM(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result {
        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)

        val smsBlocked = alertDao.countScamsBlocked(weekAgo)
        val callsScreened = callLogDao.countSince(weekAgo)
        val panicEvents = panicEventDao.countSince(weekAgo)

        val safetyScore = (100 - (2 * smsBlocked)).coerceIn(0, 100)

        val summary = buildString {
            appendLine("Your weekly security report:")
            appendLine("- $smsBlocked suspicious texts blocked")
            appendLine("- $callsScreened calls screened")
            appendLine("- $panicEvents panic events")
            appendLine("- Safety score: $safetyScore/100")
        }

        showReportNotification(summary, safetyScore)
        return Result.success()
    }

    private fun showReportNotification(summary: String, score: Int) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(applicationContext, 5555, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val emoji = when {
            score >= 90 -> "🟢"
            score >= 70 -> "🟡"
            else -> "🔴"
        }

        val notification = NotificationCompat.Builder(applicationContext, SafeHarborApp.CHANNEL_REPORT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$emoji Weekly Security Report — Score: $score/100")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(5555, notification)
    }
}
