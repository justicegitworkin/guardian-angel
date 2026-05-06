package com.safeharborsecurity.app.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessageDetailUiState(
    val alert: AlertEntity? = null,
    val isLoading: Boolean = true,
    val isBlocked: Boolean = false,
    val isDeleted: Boolean = false,
    val isMarkedSafe: Boolean = false
)

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val alertId: Long = savedStateHandle.get<Long>("alertId") ?: 0L

    private val _uiState = MutableStateFlow(MessageDetailUiState())
    val uiState: StateFlow<MessageDetailUiState> = _uiState.asStateFlow()

    init {
        loadAlert()
    }

    private fun loadAlert() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val alert = alertRepository.getAlertById(alertId)
            _uiState.update { it.copy(alert = alert, isLoading = false) }
        }
    }

    fun blockSender() {
        val sender = _uiState.value.alert?.sender ?: return
        viewModelScope.launch {
            alertRepository.blockNumber(sender, reason = "Blocked from message detail")
            _uiState.update { it.copy(isBlocked = true) }
        }
    }

    fun deleteMessage() {
        val alert = _uiState.value.alert ?: return
        viewModelScope.launch {
            alertRepository.deleteAlert(alert)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun markAsSafe() {
        val alert = _uiState.value.alert ?: return
        viewModelScope.launch {
            alertRepository.updateAlert(alert.copy(riskLevel = "SAFE"))
            _uiState.update { it.copy(isMarkedSafe = true) }
        }
    }
}
