package com.safeharborsecurity.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safeharborsecurity.app.MainActivity
import com.safeharborsecurity.app.R
import com.safeharborsecurity.app.util.CallDurationTracker
import com.safeharborsecurity.app.util.PaymentAppMonitor
import com.safeharborsecurity.app.util.WifiConnectionMonitor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GuardianService : Service() {

    @Inject lateinit var wifiConnectionMonitor: WifiConnectionMonitor
    @Inject lateinit var callDurationTracker: CallDurationTracker
    @Inject lateinit var paymentAppMonitor: PaymentAppMonitor

    companion object {
        private const val TAG = "GuardianService"
        const val CHANNEL_GUARDIAN = "guardian_service"
        const val NOTIFICATION_ID = 7777
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Guardian Service started")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createPersistentNotification())
        wifiConnectionMonitor.startMonitoring()
        callDurationTracker.start()
        paymentAppMonitor.start()
    }

    override fun onDestroy() {
        Log.d(TAG, "Guardian Service stopped")
        wifiConnectionMonitor.stopMonitoring()
        callDurationTracker.stop()
        paymentAppMonitor.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_GUARDIAN,
                "Background Protection",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Safe Companion is protecting you in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createPersistentNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_GUARDIAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Safe Companion is protecting you")
            .setContentText("Monitoring for suspicious activity")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
