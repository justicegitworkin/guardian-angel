package com.safeharborsecurity.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.safeharborsecurity.app.data.repository.WifiSafety
import com.safeharborsecurity.app.data.repository.WifiSecurityRepository
import com.safeharborsecurity.app.notification.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiConnectionMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiSecurityRepository: WifiSecurityRepository,
    private val notificationHelper: NotificationHelper
) {
    companion object {
        private const val TAG = "WifiConnectionMonitor"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun startMonitoring() {
        if (networkCallback != null) return // Already monitoring

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "WiFi connected — checking safety")
                checkCurrentWifi()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                checkCurrentWifi()
            }
        }

        try {
            cm.registerNetworkCallback(request, networkCallback!!)
            Log.d(TAG, "WiFi monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    private fun checkCurrentWifi() {
        scope.launch {
            try {
                wifiSecurityRepository.checkCurrentWifi()
                val status = wifiSecurityRepository.wifiStatus.value
                if (status.isTrusted || wifiSecurityRepository.isVpnActive()) {
                    return@launch  // Trusted network or VPN already on — no need to nag
                }
                when (status.safety) {
                    WifiSafety.UNSAFE -> {
                        notificationHelper.showVpnSuggestion(
                            ssid = status.networkName,
                            message = "Your connection to ${status.networkName} is not encrypted. Use a VPN for protection.",
                            level = "HIGH"
                        )
                    }
                    WifiSafety.CAUTION -> {
                        notificationHelper.showVpnSuggestion(
                            ssid = status.networkName,
                            message = "${status.networkName} looks like a public network. Consider using a VPN.",
                            level = "MEDIUM"
                        )
                    }
                    else -> { /* SECURE or NOT_ON_WIFI — no alert */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking WiFi: ${e.message}")
            }
        }
    }

    fun stopMonitoring() {
        networkCallback?.let {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering: ${e.message}")
            }
        }
        networkCallback = null
        Log.d(TAG, "WiFi monitoring stopped")
    }
}
