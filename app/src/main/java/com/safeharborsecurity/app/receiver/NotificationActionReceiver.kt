package com.safeharborsecurity.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.repository.AlertRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alertRepository: AlertRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_BLOCK -> {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return
                scope.launch {
                    alertRepository.blockNumber(sender, reason = "Blocked from notification")
                }
            }
            ACTION_TRUST_WIFI -> {
                val ssid = intent.getStringExtra(EXTRA_SSID) ?: return
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                scope.launch { userPreferences.addTrustedWifiNetwork(ssid) }
                if (notifId >= 0) {
                    val nm = context.getSystemService(NotificationManager::class.java)
                    nm.cancel(notifId)
                }
            }
            ACTION_DISMISS -> {
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                if (notifId >= 0) {
                    val nm = context.getSystemService(NotificationManager::class.java)
                    nm.cancel(notifId)
                }
            }
        }
    }

    companion object {
        const val ACTION_BLOCK = "com.safeharborsecurity.app.ACTION_BLOCK_SENDER"
        const val ACTION_TRUST_WIFI = "com.safeharborsecurity.app.ACTION_TRUST_WIFI"
        const val ACTION_DISMISS = "com.safeharborsecurity.app.ACTION_DISMISS_NOTIF"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_SSID = "extra_ssid"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
