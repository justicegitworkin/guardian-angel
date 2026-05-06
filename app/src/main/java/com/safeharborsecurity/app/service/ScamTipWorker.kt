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
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.dao.ScamTipDao
import com.safeharborsecurity.app.data.local.entity.ScamTipEntity
import com.safeharborsecurity.app.data.remote.ClaudeApiService
import com.safeharborsecurity.app.data.remote.model.ClaudeMessage
import com.safeharborsecurity.app.data.remote.model.ClaudeRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class ScamTipWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val claudeApi: ClaudeApiService,
    private val scamTipDao: ScamTipDao,
    private val prefs: UserPreferences
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_TAG = "scam_tip_weekly"

        fun enqueueWeekly(context: Context) {
            val work = PeriodicWorkRequestBuilder<ScamTipWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(calculateDelayToMonday9AM(), TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }

        private fun calculateDelayToMonday9AM(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result {
        val apiKey = prefs.apiKey.first()
        if (apiKey.isBlank()) return Result.success()

        return try {
            val prompt = """Generate a scam warning tip for senior citizens. Describe ONE current common scam in 2-3 sentences of plain English. No jargon. Include:
1. What the scam looks like (how it arrives — phone call, text, email)
2. How to spot it (one key red flag)
3. What to do (one clear action)

Format your response as:
TITLE: [short title, 5-8 words]
TIP: [2-3 sentences]"""

            val request = ClaudeRequest(
                messages = listOf(ClaudeMessage(role = "user", content = prompt)),
                system = "You are a scam awareness educator for senior citizens. Be warm, clear, and practical.",
                maxTokens = 256
            )

            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            if (!response.isSuccessful) return Result.retry()

            val text = response.body()?.text ?: return Result.retry()

            val titleMatch = Regex("TITLE:\\s*(.+)", RegexOption.IGNORE_CASE).find(text)
            val tipMatch = Regex("TIP:\\s*(.+)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(text)

            val title = titleMatch?.groupValues?.get(1)?.trim() ?: "Scam Alert"
            val tip = tipMatch?.groupValues?.get(1)?.trim() ?: text.take(300)

            val entity = ScamTipEntity(title = title, content = tip)
            scamTipDao.insert(entity)

            // Keep only last 10 tips
            val count = scamTipDao.count()
            if (count > 10) scamTipDao.deleteOldest(count - 10)

            showTipNotification(title, tip)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showTipNotification(title: String, tip: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(applicationContext, 4444, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, SafeHarborApp.CHANNEL_SCAM_TIP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Scam of the Week: $title")
            .setStyle(NotificationCompat.BigTextStyle().bigText(tip))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(4444, notification)
    }
}
