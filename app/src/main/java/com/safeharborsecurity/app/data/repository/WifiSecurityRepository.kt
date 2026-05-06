package com.safeharborsecurity.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.safeharborsecurity.app.data.datastore.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

enum class WifiSafety(val label: String, val emoji: String) {
    SECURE("WiFi Secure", "\uD83D\uDFE2"),
    CAUTION("WiFi — Be Careful", "\uD83D\uDFE1"),
    UNSAFE("WiFi Unsafe", "\uD83D\uDD34"),
    NOT_ON_WIFI("Not on WiFi", "\u26AB")
}

data class WifiStatus(
    val safety: WifiSafety = WifiSafety.NOT_ON_WIFI,
    val networkName: String = "",
    val securityType: String = "",
    val recommendation: String = "You're on mobile data — that's usually safer than public WiFi.",
    val isTrusted: Boolean = false
)

@Singleton
class WifiSecurityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences
) {
    private val _wifiStatus = MutableStateFlow(WifiStatus())
    val wifiStatus: StateFlow<WifiStatus> = _wifiStatus.asStateFlow()

    val trustedNetworks: Flow<Set<String>> = prefs.trustedWifiNetworks

    private val suspiciousNames = setOf(
        "free wifi", "free_wifi", "public wifi", "public_wifi",
        "open wifi", "open_wifi", "guest", "hotel wifi",
        "airport wifi", "starbucks", "mcdonalds", "default",
        "linksys", "netgear", "dlink", "tp-link", "xfinitywifi",
        "library", "marriott", "hilton", "hyatt", "ihg",
        "panera", "subway", "cafe", "coffee"
    )

    suspend fun checkCurrentWifi() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            _wifiStatus.value = WifiStatus()
            return
        }

        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo?.ssid?.removeSurrounding("\"") ?: "Unknown"

        // Determine security type
        val securityType = getSecurityType(wifiManager)
        val isSuspiciousName = suspiciousNames.any { ssid.lowercase().contains(it) }
        val trusted = prefs.trustedWifiNetworks.first().contains(ssid)

        val safety = when {
            trusted -> WifiSafety.SECURE
            securityType == "OPEN" || securityType == "NONE" -> WifiSafety.UNSAFE
            securityType == "WEP" -> WifiSafety.UNSAFE
            isSuspiciousName -> WifiSafety.CAUTION
            else -> WifiSafety.SECURE
        }

        val recommendation = when {
            trusted -> "You've marked this network as trusted."
            safety == WifiSafety.UNSAFE -> "This network is not secure. Avoid online banking or entering passwords while connected."
            safety == WifiSafety.CAUTION -> "This looks like a public network. Be careful with personal information."
            safety == WifiSafety.SECURE -> "This network appears to be properly secured."
            else -> "You're on mobile data — that's usually safer than public WiFi."
        }

        _wifiStatus.value = WifiStatus(
            safety = safety,
            networkName = ssid,
            securityType = securityType,
            recommendation = recommendation,
            isTrusted = trusted
        )
    }

    fun isVpnActive(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    @Suppress("DEPRECATION")
    private fun getSecurityType(wifiManager: WifiManager): String {
        try {
            val scanResults = wifiManager.scanResults
            val currentSsid = wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
            val current = scanResults?.find { it.SSID == currentSsid }
            if (current != null) {
                val caps = current.capabilities
                return when {
                    caps.contains("WPA3") -> "WPA3"
                    caps.contains("WPA2") -> "WPA2"
                    caps.contains("WPA") -> "WPA"
                    caps.contains("WEP") -> "WEP"
                    else -> "OPEN"
                }
            }
        } catch (_: SecurityException) {
            // Location permission not granted — can't determine security
        }
        return "Unknown"
    }

    suspend fun markAsTrusted(ssid: String) {
        prefs.addTrustedWifiNetwork(ssid)
    }

    suspend fun removeTrusted(ssid: String) {
        prefs.removeTrustedWifiNetwork(ssid)
    }
}
