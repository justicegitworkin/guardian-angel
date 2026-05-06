package com.safeharborsecurity.app.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.model.WifiNetworkInfo
import com.safeharborsecurity.app.data.repository.HiddenDeviceScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class WifiNetworkDetailUiState(
    val network: WifiNetworkInfo? = null,
    val legitimacySignals: List<String> = emptyList()
)

private val PUBLIC_PATTERNS = listOf(
    "guest", "free", "public", "airport", "hotel",
    "starbucks", "mcdonalds", "library", "marriott", "hilton",
    "hyatt", "ihg", "openwifi", "wifi_free"
)

@HiltViewModel
class WifiNetworkDetailViewModel @Inject constructor(
    private val scanRepository: HiddenDeviceScanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiNetworkDetailUiState())
    val uiState: StateFlow<WifiNetworkDetailUiState> = _uiState.asStateFlow()

    fun load(ssid: String, bssid: String) {
        viewModelScope.launch {
            val report = scanRepository.scanReport.value
            val cached = report.wifiResult.wifiNetworks.firstOrNull {
                it.ssid == ssid && it.bssid.equals(bssid, ignoreCase = true)
            }
            val network = if (cached != null) cached else withContext(Dispatchers.IO) {
                scanRepository.runWifiScan().wifiNetworks.firstOrNull {
                    it.ssid == ssid && it.bssid.equals(bssid, ignoreCase = true)
                }
            } ?: return@launch

            val allNetworks = report.wifiResult.wifiNetworks
            val signals = computeLegitimacySignals(network, allNetworks)
            _uiState.value = WifiNetworkDetailUiState(network, signals)
        }
    }

    private fun computeLegitimacySignals(
        target: WifiNetworkInfo,
        allNetworks: List<WifiNetworkInfo>
    ): List<String> {
        val signals = mutableListOf<String>()

        // Multiple SSIDs with similar name → possible evil twin
        val similar = allNetworks.filter {
            it.bssid != target.bssid &&
                similarSsid(it.ssid, target.ssid)
        }
        if (similar.isNotEmpty()) {
            signals.add("Multiple networks with similar names detected — possible evil twin attack")
        }

        // Very strong signal from unknown device → could be a portable hotspot
        if (target.signalLevel > -40) {
            signals.add("Very strong signal — the access point is unusually close (could be a portable hotspot)")
        }

        // Common public WiFi name pattern
        val ssidLower = target.ssid.lowercase()
        if (PUBLIC_PATTERNS.any { ssidLower.contains(it) }) {
            signals.add("Common public WiFi name pattern — verify the network with staff before connecting")
        }

        // Hidden SSID
        if (target.ssid == "Hidden Network" || target.ssid.isBlank()) {
            signals.add("Hidden SSID — networks that hide their name are unusual and worth a second look")
        }

        return signals
    }

    private fun similarSsid(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val cleanA = a.lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanB = b.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (cleanA == cleanB) return false  // exact match — actually duplicate, ignore
        // Same prefix or one contains the other (common evil-twin pattern)
        val shortest = minOf(cleanA.length, cleanB.length)
        if (shortest < 4) return false
        return cleanA.startsWith(cleanB) || cleanB.startsWith(cleanA) ||
            cleanA.contains(cleanB) || cleanB.contains(cleanA)
    }
}
