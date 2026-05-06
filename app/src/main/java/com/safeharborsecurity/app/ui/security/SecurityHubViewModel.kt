package com.safeharborsecurity.app.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.local.entity.CameraAlertEntity
import com.safeharborsecurity.app.data.repository.CameraAlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CameraFilter(val label: String, val category: String?) {
    ALL("All", null),
    MOTION("Motion", "MOTION"),
    PERSON("Person", "PERSON"),
    SOUND("Sound", "SOUND"),
    DOORBELL("Doorbell", "DOORBELL"),
    OTHER("Other", "OTHER")
}

data class SecurityHubUiState(
    val alerts: List<CameraAlertEntity> = emptyList(),
    val activeFilter: CameraFilter = CameraFilter.ALL
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SecurityHubViewModel @Inject constructor(
    private val repository: CameraAlertRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(CameraFilter.ALL)

    val uiState: StateFlow<SecurityHubUiState> = _filter
        .flatMapLatest { filter ->
            val source = filter.category?.let { repository.byCategory(it) } ?: repository.all()
            source.map { SecurityHubUiState(alerts = it, activeFilter = filter) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecurityHubUiState())

    fun setFilter(filter: CameraFilter) { _filter.value = filter }

    fun markRead(id: Long) {
        viewModelScope.launch { repository.markRead(id) }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead() }
    }
}
