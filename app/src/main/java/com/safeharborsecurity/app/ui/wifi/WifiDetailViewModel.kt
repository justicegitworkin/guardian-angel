package com.safeharborsecurity.app.ui.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.repository.WifiSecurityRepository
import com.safeharborsecurity.app.data.repository.WifiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WifiDetailUiState(
    val wifiStatus: WifiStatus = WifiStatus(),
    val trustedNetworks: Set<String> = emptySet(),
    val isTrusted: Boolean = false
)

@HiltViewModel
class WifiDetailViewModel @Inject constructor(
    private val wifiSecurityRepository: WifiSecurityRepository
) : ViewModel() {

    val uiState: StateFlow<WifiDetailUiState> = combine(
        wifiSecurityRepository.wifiStatus,
        wifiSecurityRepository.trustedNetworks
    ) { status, trusted ->
        WifiDetailUiState(
            wifiStatus = status,
            trustedNetworks = trusted,
            isTrusted = status.networkName.isNotBlank() && status.networkName in trusted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WifiDetailUiState())

    init {
        viewModelScope.launch { wifiSecurityRepository.checkCurrentWifi() }
    }

    fun refresh() {
        viewModelScope.launch { wifiSecurityRepository.checkCurrentWifi() }
    }

    fun markAsTrusted(ssid: String) {
        viewModelScope.launch { wifiSecurityRepository.markAsTrusted(ssid) }
    }

    fun removeTrusted(ssid: String) {
        viewModelScope.launch { wifiSecurityRepository.removeTrusted(ssid) }
    }
}
