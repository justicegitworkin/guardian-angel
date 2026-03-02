package com.guardianangel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GuardianAngelApp : Application() {

    companion object {
        const val CHANNEL_ALERTS = "guardian_alerts"
        const val CHANNEL_CALL = "guardian_call"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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

            manager.createNotificationChannels(listOf(alertsChannel, callChannel))
        }
    }
}
