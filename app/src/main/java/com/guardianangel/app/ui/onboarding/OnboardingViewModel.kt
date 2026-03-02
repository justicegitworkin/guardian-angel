package com.guardianangel.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardianangel.app.data.datastore.UserPreferences
import com.guardianangel.app.data.remote.ClaudeApiService
import com.guardianangel.app.data.remote.model.ClaudeMessage
import com.guardianangel.app.data.remote.model.ClaudeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val claudeApi: ClaudeApiService
) : ViewModel() {

    val isOnboardingDone: StateFlow<Boolean?> = prefs.isOnboardingDone
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val textSizePref: StateFlow<String> = prefs.textSizePref
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "LARGE")

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    fun saveNameAndKey(name: String, apiKey: String) {
        viewModelScope.launch {
            prefs.setUserName(name)
            prefs.setApiKey(apiKey)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            prefs.setOnboardingDone(true)
        }
    }

    fun testApiConnection(apiKey: String) {
        viewModelScope.launch {
            _testResult.value = "testing"
            runCatching {
                val response = claudeApi.sendMessage(
                    apiKey = apiKey,
                    request = ClaudeRequest(
                        messages = listOf(ClaudeMessage(role = "user", content = "Say 'Guardian Angel connected!' in one sentence.")),
                        maxTokens = 64
                    )
                )
                _testResult.value = when {
                    response.isSuccessful -> "success"
                    response.code() == 401 -> "invalid_key"
                    else -> "error"
                }
            }.onFailure {
                _testResult.value = "error"
            }
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }
}
