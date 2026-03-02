package com.guardianangel.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianangel.app.data.local.entity.AlertEntity
import com.guardianangel.app.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MessageFilter { ALL, SCAMS, WARNINGS, SAFE }

data class MessagesUiState(
    val alerts: List<AlertEntity> = emptyList(),
    val filter: MessageFilter = MessageFilter.ALL
) {
    val filtered: List<AlertEntity> get() = when (filter) {
        MessageFilter.ALL -> alerts
        MessageFilter.SCAMS -> alerts.filter { it.riskLevel == "SCAM" }
        MessageFilter.WARNINGS -> alerts.filter { it.riskLevel == "WARNING" }
        MessageFilter.SAFE -> alerts.filter { it.riskLevel == "SAFE" }
    }
}

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(MessageFilter.ALL)

    val uiState: StateFlow<MessagesUiState> = combine(
        alertRepository.getAlertsByType("SMS"),
        _filter
    ) { alerts, filter ->
        MessagesUiState(alerts = alerts, filter = filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessagesUiState())

    fun setFilter(filter: MessageFilter) {
        _filter.value = filter
    }

    fun blockSender(sender: String) {
        viewModelScope.launch {
            alertRepository.blockNumber(sender, reason = "Manually blocked by user")
        }
    }

    fun markAsRead(alertId: Long) {
        viewModelScope.launch {
            alertRepository.markAsRead(alertId)
        }
    }
}
