package com.guardianangel.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardianangel.app.data.repository.AlertRepository
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_BLOCK -> {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                scope.launch {
                    alertRepository.blockNumber(sender, reason = "Blocked from notification")
                }
            }
        }
    }

    companion object {
        const val ACTION_BLOCK = "com.guardianangel.app.ACTION_BLOCK_SENDER"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
