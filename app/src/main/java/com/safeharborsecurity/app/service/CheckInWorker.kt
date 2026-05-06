package com.safeharborsecurity.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.safeharborsecurity.app.MainActivity
import com.safeharborsecurity.app.R
import com.safeharborsecurity.app.SafeHarborApp
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.dao.CheckInDao
import com.safeharborsecurity.app.data.remote.model.FamilyContact
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class CheckInWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefs: UserPreferences,
    private val checkInDao: CheckInDao,
    private val gson: Gson
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_TAG = "daily_checkin"
        private const val WORK_REMINDER = "checkin_reminder"
        private const val WORK_DEADLINE = "checkin_deadline"

        fun enqueueDaily(context: Context) {
            // Reminder at 9:00 AM daily
            val reminderWork = PeriodicWorkRequestBuilder<CheckInWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(calculateDelayToHour(9), TimeUnit.MILLISECONDS)
                .addTag(WORK_REMINDER)
                .setInputData(workDataOf("type" to "reminder"))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_REMINDER,
                ExistingPeriodicWorkPolicy.KEEP,
                reminderWork
            )

            // Deadline check at 11:00 AM daily
            val deadlineWork = PeriodicWorkRequestBuilder<CheckInWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(calculateDelayToHour(11), TimeUnit.MILLISECONDS)
                .addTag(WORK_DEADLINE)
                .setInputData(workDataOf("type" to "deadline"))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_DEADLINE,
                ExistingPeriodicWorkPolicy.KEEP,
                deadlineWork
            )
        }

        private fun calculateDelayToHour(hour: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result {
        val type = inputData.getString("type") ?: "reminder"
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val checkedIn = checkInDao.countForDate(today) > 0

        return when (type) {
            "reminder" -> {
                if (!checkedIn) {
                    showReminderNotification()
                }
                Result.success()
            }
            "deadline" -> {
                if (!checkedIn) {
                    val notifyFamily = prefs.checkInNotifyFamily.first()
                    if (notifyFamily) {
                        notifyFamilyContacts()
                    }
                }
                Result.success()
            }
            else -> Result.success()
        }
    }

    private fun showReminderNotification() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 7777, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, SafeHarborApp.CHANNEL_CHECKIN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Good morning! Time to check in")
            .setContentText("Open Safe Companion and tap \"I'm OK Today\" so your family knows you're safe.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(7777, notification)
    }

    @Suppress("DEPRECATION")
    private suspend fun notifyFamilyContacts() {
        val familyJson = prefs.familyContactsJson.first()
        val familyType = object : TypeToken<List<FamilyContact>>() {}.type
        val contacts = runCatching {
            gson.fromJson<List<FamilyContact>>(familyJson, familyType) ?: emptyList()
        }.getOrDefault(emptyList())

        if (contacts.isEmpty()) return

        val userName = prefs.userName.first()
        val template = prefs.checkInMessageTemplate.first()
        val message = template.replace("{name}", userName.ifBlank { "Your family member" })

        contacts.forEach { contact ->
            try {
                SmsManager.getDefault().sendTextMessage(contact.number, null, message, null, null)
            } catch (_: Exception) { }
        }
    }
}
