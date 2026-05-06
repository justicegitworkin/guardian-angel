package com.safeharborsecurity.app.ui.appchecker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.model.InstalledAppInfo
import com.safeharborsecurity.app.data.repository.AppCheckerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppFilter { ALL, RECENTLY_INSTALLED, NOT_FROM_PLAY_STORE }

data class AppCheckerUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val filteredApps: List<InstalledAppInfo> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedFilter: AppFilter = AppFilter.ALL
)

@HiltViewModel
class AppCheckerViewModel @Inject constructor(
    private val repository: AppCheckerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppCheckerUiState())
    val uiState: StateFlow<AppCheckerUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val apps = repository.getInstalledApps()
            _uiState.update { it.copy(apps = apps, isLoading = false) }
            applyFilters()
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setFilter(filter: AppFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)

        val filtered = state.apps.filter { app ->
            // Search filter
            val matchesSearch = state.searchQuery.isBlank() ||
                app.appName.contains(state.searchQuery, ignoreCase = true) ||
                app.packageName.contains(state.searchQuery, ignoreCase = true)

            // Category filter
            val matchesFilter = when (state.selectedFilter) {
                AppFilter.ALL -> true
                AppFilter.RECENTLY_INSTALLED -> app.firstInstallTime >= thirtyDaysAgo
                AppFilter.NOT_FROM_PLAY_STORE -> {
                    app.installSource != com.safeharborsecurity.app.data.model.InstallSource.PLAY_STORE &&
                        !app.isSystemApp
                }
            }

            matchesSearch && matchesFilter
        }

        _uiState.update { it.copy(filteredApps = filtered) }
    }
}
