package com.safeharborsecurity.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.safeharborsecurity.app.SafeHarborApp
import com.safeharborsecurity.app.MainActivity
import com.safeharborsecurity.app.R
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.repository.PrivacyMonitorRepository
import com.safeharborsecurity.app.data.repository.PrivacyScanResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class PrivacyMonitorService : Service() {

    @Inject lateinit var privacyRepository: PrivacyMonitorRepository
    @Inject lateinit var userPreferences: UserPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null

    companion object {
        const val NOTIF_ID_FOREGROUND = 7790
        const val NOTIF_ID_THREAT = 7791
        private const val SCAN_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        internal val _lastScanResult = MutableStateFlow<PrivacyScanResult?>(null)
        val lastScanResult: StateFlow<PrivacyScanResult?> = _lastScanResult.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID_FOREGROUND, buildForegroundNotification())
        startPeriodicScan()
        return START_STICKY
    }

    private fun startPeriodicScan() {
        scanJob?.cancel()
        scanJob = scope.launch {
            while (isActive) {
                // Check if shield is still enabled
                val enabled = userPreferences.isListeningShieldEnabled.first()
                if (!enabled) {
                    stopSelf()
                    return@launch
                }

                val result = privacyRepository.performFullScan()
                _lastScanResult.value = result

                if (result.threats.isNotEmpty()) {
                    showThreatNotification(result)
                }

                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            data = android.net.Uri.parse("safeharbor://privacy")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, NOTIF_ID_FOREGROUND, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SafeHarborApp.CHANNEL_PRIVACY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Listening Shield is active")
            .setContentText("Safe Companion is watching for apps that might be listening")
            // PRIORITY_MIN + setSilent: collapse to a tiny icon, never
            // heads-up. setVisibility SECRET keeps it off the lock screen.
            // Together with the channel's IMPORTANCE_MIN this is the
            // quietest a foreground-service notification can legally be.
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun showThreatNotification(result: PrivacyScanResult) {
        val count = result.threats.size
        val title = if (count == 1) "1 app may be listening"
                    else "$count apps may be listening"

        val body = result.threats.take(3).joinToString("\n") { "- ${it.appName}: ${it.reason}" }

        val intent = Intent(this, MainActivity::class.java).apply {
            data = android.net.Uri.parse("safeharbor://privacy")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, NOTIF_ID_THREAT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SafeHarborApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(result.threats.first().appName + ": " + result.threats.first().reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID_THREAT, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        scanJob?.cancel()
        scope.cancel()
        _isRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
