package com.safeharborsecurity.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log
import com.safeharborsecurity.app.data.repository.WifiSecurityRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WifiStateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var wifiSecurityRepository: WifiSecurityRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
            Log.d("WifiSecurity", "Network state changed — checking WiFi security")
            scope.launch { wifiSecurityRepository.checkCurrentWifi() }
        }
    }
}
