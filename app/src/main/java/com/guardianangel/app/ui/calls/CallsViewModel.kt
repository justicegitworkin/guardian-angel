package com.guardianangel.app.ui.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianangel.app.data.local.entity.CallLogEntity
import com.guardianangel.app.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallsUiState(
    val callLogs: List<CallLogEntity> = emptyList()
)

@HiltViewModel
class CallsViewModel @Inject constructor(
    private val callRepository: CallRepository
) : ViewModel() {

    val uiState: StateFlow<CallsUiState> = callRepository.getAllCallLogs()
        .map { CallsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallsUiState())

    fun blockNumber(number: String) {
        viewModelScope.launch {
            callRepository.blockNumber(number)
        }
    }
}
