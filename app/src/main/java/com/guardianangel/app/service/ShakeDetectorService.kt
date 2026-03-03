package com.guardianangel.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.guardianangel.app.GuardianAngelApp
import com.guardianangel.app.MainActivity
import com.guardianangel.app.R
import com.guardianangel.app.data.datastore.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class ShakeDetectorService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 8001
        private const val SHAKE_THRESHOLD = 12f      // m/s² net over gravity
        private const val SHAKE_DEBOUNCE_MS = 1_000L
    }

    @Inject lateinit var prefs: UserPreferences

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            val net = sqrt((x * x + y * y + z * z).toDouble()).toFloat() - SensorManager.GRAVITY_EARTH
            if (net > SHAKE_THRESHOLD) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > SHAKE_DEBOUNCE_MS) {
                    lastShakeTime = now
                    onShakeDetected()
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check pref — if shake is disabled, stop immediately without showing notification
        serviceScope.launch {
            val enabled = prefs.isShakeEnabled.first()
            if (!enabled) {
                stopSelf()
                return@launch
            }
            // Show persistent foreground notification and register listener
            withContext(Dispatchers.Main) {
                startForeground(NOTIFICATION_ID, buildNotification())
                accelerometer?.let {
                    sensorManager.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(shakeListener)
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onShakeDetected() {
        // Play brief notification chime
        runCatching {
            val chimeUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, chimeUri)?.play()
        }

        // Toast on main thread
        serviceScope.launch(Dispatchers.Main) {
            Toast.makeText(applicationContext, "Guardian Angel activated", Toast.LENGTH_SHORT).show()
        }

        // Launch MainActivity with chat deep link — brings app to foreground if backgrounded
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            data = Uri.parse("guardianangel://chat?context=")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(launchIntent)
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, GuardianAngelApp.CHANNEL_SHAKE)
            .setSmallIcon(R.drawable.ic_guardian_angel)
            .setContentTitle("Guardian Angel — Shake Active")
            .setContentText("Shake your phone to reach Guardian Angel instantly")
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
