package com.safeharborsecurity.app.ui.digest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeeklyDigestUiState(
    val content: String = "",
    val generatedAt: Long = 0L,
    val shareWithFamily: Boolean = false
)

/**
 * Item 4 (option a): backs the weekly-digest detail screen and the home-card
 * dismiss button. State is driven by UserPreferences so the worker writing a
 * new digest is reflected immediately without any refresh logic.
 */
@HiltViewModel
class WeeklyDigestViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {

    val uiState: StateFlow<WeeklyDigestUiState> = combine(
        prefs.weeklyDigestContent,
        prefs.weeklyDigestGeneratedAt,
        prefs.isWeeklyDigestShareFamily
    ) { content, ts, share ->
        WeeklyDigestUiState(content = content, generatedAt = ts, shareWithFamily = share)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyDigestUiState())

    fun dismiss() {
        viewModelScope.launch { prefs.dismissWeeklyDigest() }
    }
}
