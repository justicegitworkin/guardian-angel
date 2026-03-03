package com.guardianangel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.guardianangel.app.sync.ScamIntelligenceSync
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class GuardianAngelApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    companion object {
        const val CHANNEL_ALERTS = "guardian_alerts"
        const val CHANNEL_CALL   = "guardian_call"
        const val CHANNEL_INTEL  = "guardian_intel"
    }

    // WorkManager uses this factory so @HiltWorker workers are injected correctly.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleScamIntelSync()
    }

    private fun scheduleScamIntelSync() {
        val request = PeriodicWorkRequestBuilder<ScamIntelligenceSync>(60, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(2, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScamIntelligenceSync.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alerts_description)
                enableVibration(true)
            }

            val callChannel = NotificationChannel(
                CHANNEL_CALL,
                getString(R.string.channel_call_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_call_description)
            }

            val intelChannel = NotificationChannel(
                CHANNEL_INTEL,
                "Scam Intelligence Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when new high-priority scam patterns are downloaded"
                setSound(null, null)   // silent — informational only
            }

            manager.createNotificationChannels(listOf(alertsChannel, callChannel, intelChannel))
        }
    }
}
