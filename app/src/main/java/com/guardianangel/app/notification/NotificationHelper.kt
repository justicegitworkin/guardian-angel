package com.guardianangel.app.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.guardianangel.app.GuardianAngelApp
import com.guardianangel.app.MainActivity
import com.guardianangel.app.R
import com.guardianangel.app.data.local.entity.AlertEntity
import com.guardianangel.app.receiver.NotificationActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun showSmsAlert(alert: AlertEntity) {
        val notifId = alert.id.toInt()

        // "Ask Guardian" intent — deep link to chat with context
        val encodedContext = java.net.URLEncoder.encode(
            "I got a suspicious text from ${alert.sender}. " +
                "Guardian flagged it: ${alert.reason}. What should I do?",
            "UTF-8"
        )
        val chatIntent = Intent(context, MainActivity::class.java).apply {
            data = android.net.Uri.parse("guardianangel://chat?context=$encodedContext")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val chatPi = PendingIntent.getActivity(
            context, notifId + 1000, chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Block Sender" intent
        val blockIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_BLOCK
            putExtra(NotificationActionReceiver.EXTRA_SENDER, alert.sender)
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val blockPi = PendingIntent.getBroadcast(
            context, notifId + 2000, blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, GuardianAngelApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_guardian_angel)
            .setContentTitle("⚠️ Suspicious Text Detected")
            .setContentText("From ${alert.sender}: ${alert.reason}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("From ${alert.sender}\n\n${alert.reason}\n\nAdvice: ${alert.action}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(chatPi)
            .addAction(0, "Ask Guardian 😇", chatPi)
            .addAction(0, "Block Sender", blockPi)
            .build()

        manager.notify(notifId, notification)
    }

    fun showCallScreeningNotification(callerNumber: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 9999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, GuardianAngelApp.CHANNEL_CALL)
            .setSmallIcon(R.drawable.ic_guardian_angel)
            .setContentTitle("📞 Guardian is screening a call")
            .setContentText("Caller: $callerNumber")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    fun showScamCallWarning(callerNumber: String, warning: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 8888, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, GuardianAngelApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_guardian_angel)
            .setContentTitle("🚨 SCAM CALL DETECTED")
            .setContentText(warning)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        manager.notify(8888, notification)
    }

    fun showScamIntelUpdate(newHighCriticalCount: Int) {
        val notification = NotificationCompat.Builder(context, GuardianAngelApp.CHANNEL_INTEL)
            .setSmallIcon(R.drawable.ic_guardian_angel)
            .setContentTitle("🛡️ Scam intelligence updated")
            .setContentText("$newHighCriticalCount new HIGH/CRITICAL threat alert${if (newHighCriticalCount > 1) "s" else ""} downloaded")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        manager.notify(7777, notification)
    }

    fun cancelNotification(id: Int) = manager.cancel(id)
}
