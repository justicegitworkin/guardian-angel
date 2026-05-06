package com.safeharborsecurity.app.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safeharborsecurity.app.SafeHarborApp
import com.safeharborsecurity.app.MainActivity
import com.safeharborsecurity.app.R
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.receiver.NotificationActionReceiver
import com.safeharborsecurity.app.ui.alert.GrabAttentionActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /**
     * Source F: When true, notifications are posted silently (no sound/vibration)
     * to avoid audio blips during active voice conversation.
     */
    @Volatile
    var isConversationActive: Boolean = false

    /** Apply silent mode if conversation is active */
    private fun applySilenceIfNeeded(builder: NotificationCompat.Builder): NotificationCompat.Builder {
        if (isConversationActive) {
            builder.setSilent(true)
        }
        return builder
    }

    private fun getAlertLevel(): String {
        return userPreferences.getAlertLevelSync()
    }

    /**
     * Show grab-your-attention full-screen alert
     */
    private fun showGrabAttention(
        title: String,
        description: String,
        threatLevel: String,
        threatType: String,
        notifId: Int
    ) {
        val fullScreenIntent = Intent(context, GrabAttentionActivity::class.java).apply {
            putExtra("title", title)
            putExtra("description", description)
            putExtra("threatLevel", threatLevel)
            putExtra("threatType", threatType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_HISTORY
        }

        // CRITICAL: setFullScreenIntent() only fires the activity when the
        // device is locked or the system declines to show a heads-up. On
        // unlocked devices (which is most tester scenarios) the heads-up
        // bubble appears instead — which is exactly the symptom users see.
        // To get full-screen on unlocked too, launch the activity directly
        // from this foreground-service context. Foreground services (which
        // is what calls into NotificationHelper here) can launch activities
        // from background even on Android 14+.
        try {
            context.startActivity(fullScreenIntent)
        } catch (e: Exception) {
            android.util.Log.w("NotificationHelper", "Direct activity launch failed", e)
        }

        val fullScreenPi = PendingIntent.getActivity(
            context, notifId + 5000, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, SafeHarborApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPi, true)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 800, 200, 800, 200, 800))
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        manager.notify(notifId, notification)
    }

    fun showSmsAlert(alert: AlertEntity) {
        val level = getAlertLevel()
        if (level == "off") {
            Log.d("NotificationHelper", "Alert suppressed (level=off): SMS from ${alert.sender}")
            return
        }

        val notifId = alert.id.toInt()

        if (level == "attention") {
            showGrabAttention(
                title = "Suspicious Text Message",
                description = "A message from ${alert.sender} looks like a scam. ${alert.reason}",
                threatLevel = "HIGH",
                threatType = "SMS",
                notifId = notifId
            )
            return
        }

        // Deep link to message detail screen
        val detailIntent = Intent(context, MainActivity::class.java).apply {
            data = android.net.Uri.parse("safeharbor://message_detail?alertId=${alert.id}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val chatPi = PendingIntent.getActivity(
            context, notifId + 1000, detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val blockIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_BLOCK
            putExtra(NotificationActionReceiver.EXTRA_SENDER, alert.sender)
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val blockPi = PendingIntent.getBroadcast(
            context, notifId + 2000, blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = applySilenceIfNeeded(
            NotificationCompat.Builder(context, SafeHarborApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                // Tints the small shield icon and the app-name accent red so
                // the notification reads as urgent at a glance.
                .setColor(0xFFD32F2F.toInt())
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentTitle(boldSpanned("⚠️ Suspicious text message"))
                .setContentText("A message from ${alert.sender} looks like a scam. Tap to review.")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("From ${alert.sender}\n\n${alert.reason}\n\nAdvice: ${alert.action}"))
                // Bumped from DEFAULT to HIGH so the notification heads-up.
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(30 * 60 * 1000L) // Auto-dismiss after 30 min
                .setContentIntent(chatPi)
                .addAction(0, "Review", chatPi)
                .addAction(0, "Dismiss", blockPi)
        ).build()

        manager.notify(notifId, notification)
    }

    /** Spannable string that renders the title in bold across all Android
     *  versions. Some launchers ignore HTML in titles; SpannableString is
     *  honoured everywhere. */
    private fun boldSpanned(text: String): CharSequence {
        val s = android.text.SpannableString(text)
        s.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            0, text.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return s
    }

    fun showEmailAlert(alert: AlertEntity) {
        val level = getAlertLevel()
        if (level == "off") {
            Log.d("NotificationHelper", "Alert suppressed (level=off): Email from ${alert.sender}")
            return
        }

        val notifId = (alert.id + 5000).toInt()

        if (level == "attention") {
            showGrabAttention(
                title = "Suspicious Email Detected",
                description = "An email from ${alert.sender} looks suspicious. ${alert.reason}",
                threatLevel = "HIGH",
                threatType = "EMAIL",
                notifId = notifId
            )
            return
        }

        val emailDetailIntent = Intent(context, MainActivity::class.java).apply {
            data = android.net.Uri.parse("safeharbor://message_detail?alertId=${alert.id}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val chatPi = PendingIntent.getActivity(
            context, notifId + 1000, emailDetailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = applySilenceIfNeeded(
            NotificationCompat.Builder(context, SafeHarborApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFD32F2F.toInt())
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentTitle(boldSpanned("⚠️ Suspicious email"))
                .setContentText("An email from ${alert.sender} looks suspicious. Tap to review.")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("From ${alert.sender}\n\n${alert.reason}\n\nAdvice: ${alert.action}"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(30 * 60 * 1000L)
                .setContentIntent(chatPi)
                .addAction(0, "Review", chatPi)
        ).build()

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

        return NotificationCompat.Builder(context, SafeHarborApp.CHANNEL_CALL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Safe Companion is screening a call")
            .setContentText("Caller: $callerNumber")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    fun showScamCallWarning(callerNumber: String, warning: String) {
        val level = getAlertLevel()
        if (level == "off") {
            Log.d("NotificationHelper", "Alert suppressed (level=off): Scam call from $callerNumber")
            return
        }

        if (level == "attention") {
            showGrabAttention(
                title = "SCAM CALL DETECTED",
                description = "The call from $callerNumber has been flagged as a scam. $warning",
                threatLevel = "CRITICAL",
                threatType = "CALL",
                notifId = 8888
            )
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 8888, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = applySilenceIfNeeded(
            NotificationCompat.Builder(context, SafeHarborApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Scam call detected")
                .setContentText(warning)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(30 * 60 * 1000L)
                .setContentIntent(pi)
        ).build()

        manager.notify(8888, notification)
    }

    fun showGiftCardAlert(alert: AlertEntity) {
        val level = getAlertLevel()
        if (level == "off") {
            Log.d("NotificationHelper", "Alert suppressed (level=off): Gift card from ${alert.sender}")
            return
        }

        val notifId = (alert.id + 3000).toInt()

        if (level == "attention") {
            showGrabAttention(
                title = "GIFT CARD SCAM DETECTED",
                description = "Someone may be trying to get you to buy gift cards. This is a common scam. ${alert.reason}",
                threatLevel = "CRITICAL",
                threatType = "GIFT_CARD",
                notifId = notifId
            )
            return
        }

        val chatIntent = Intent(context, MainActivity::class.java).apply {
            data = android.net.Uri.parse("safeharbor://chat?context=${java.net.URLEncoder.encode(alert.reason, "UTF-8")}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val chatPi = PendingIntent.getActivity(context, notifId + 1000, chatIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = applySilenceIfNeeded(
            NotificationCompat.Builder(context, SafeHarborApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Gift card scam warning")
                .setContentText("Someone may be trying to scam you with gift cards. Tap to review.")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("From ${alert.sender}\n\n${alert.reason}\n\n${alert.action}"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(30 * 60 * 1000L)
                .setContentIntent(chatPi)
                .addAction(0, "Review", chatPi)
        ).build()

        manager.notify(notifId, notification)
    }

    fun showWifiWarning(title: String, message: String) {
        val level = getAlertLevel()
        if (level == "off") return

        if (level == "attention") {
            showGrabAttention(
                title = title,
                description = message,
                threatLevel = "HIGH",
                threatType = "WIFI",
                notifId = 6666
            )
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            data = android.net.Uri.parse("safeharbor://wifi_detail")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 6666, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = applySilenceIfNeeded(
            NotificationCompat.Builder(context, SafeHarborApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(60 * 60 * 1000L) // 1 hour
                .setContentIntent(pi)
        ).build()

        manager.notify(6666, notification)
    }

    fun cancelNotification(id: Int) = manager.cancel(id)

    /**
     * Lightweight payment safety reminder shown when the user opens a payment
     * app (Venmo / Zelle / Cash App / etc.) — replacement for the old
     * AccessibilityService-based prompt. Auto-dismisses after a few seconds.
     */
    fun showPaymentReminder() {
        if (getAlertLevel() == "off") return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 6666, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = applySilenceIfNeeded(
            NotificationCompat.Builder(context, SafeHarborApp.CHANNEL_PAYMENT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Payment Safety Reminder")
                .setContentText("Only send money to people you know and trust in real life.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(8_000)
                .setContentIntent(pi)
        ).build()
        manager.notify(6666, notification)
    }

    /**
     * Part F2: Suggest enabling a VPN when on an open or otherwise risky WiFi.
     * Includes "Enable VPN" (open Play Store/launch a VPN app), "I Trust This Network",
     * and "Dismiss" actions.
     */
    fun showVpnSuggestion(ssid: String, message: String, level: String) {
        val alertLevel = getAlertLevel()
        if (alertLevel == "off") return

        val notifId = (6700 + (ssid.hashCode() and 0x0F)).coerceIn(6700, 6799)

        // Open Safe Companion's WiFi detail screen to see the VPN button
        val openIntent = Intent(context, MainActivity::class.java).apply {
            data = android.net.Uri.parse("safeharbor://wifi_detail")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val trustIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TRUST_WIFI
            putExtra(NotificationActionReceiver.EXTRA_SSID, ssid)
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val trustPi = PendingIntent.getBroadcast(
            context, notifId + 100, trustIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context, notifId + 200, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = if (level == "HIGH") NotificationCompat.PRIORITY_HIGH
                       else NotificationCompat.PRIORITY_DEFAULT

        val notification = applySilenceIfNeeded(
            NotificationCompat.Builder(context, SafeHarborApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⚠️ You're on an unsecured WiFi network")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(priority)
                .setAutoCancel(true)
                .setContentIntent(openPi)
                .addAction(0, "Enable VPN", openPi)
                .addAction(0, "I Trust This Network", trustPi)
                .addAction(0, "Dismiss", dismissPi)
        ).build()

        manager.notify(notifId, notification)
    }
}
