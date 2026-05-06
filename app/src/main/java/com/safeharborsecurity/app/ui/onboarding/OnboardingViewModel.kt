package com.safeharborsecurity.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.remote.ClaudeApiService
import com.safeharborsecurity.app.data.remote.model.ClaudeMessage
import com.safeharborsecurity.app.data.remote.model.ClaudeRequest
import com.safeharborsecurity.app.data.remote.model.FamilyContact
import com.safeharborsecurity.app.data.repository.PointsRepository
import com.safeharborsecurity.app.util.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val claudeApi: ClaudeApiService,
    private val keystoreManager: KeystoreManager,
    private val gson: Gson,
    private val pointsRepository: PointsRepository
) : ViewModel() {

    val isOnboardingDone: StateFlow<Boolean?> = prefs.isOnboardingDone
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val textSizePref: StateFlow<String> = prefs.textSizePref
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "NORMAL")

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
            // Welcome bonus: 10 safety points for completing setup. Uses a
            // unique eventType + maxPerDay=1 + the duplicate-window guard so
            // it can never grant more than once even if the user re-enters
            // and re-finishes onboarding (which the prefs gate generally
            // prevents anyway).
            pointsRepository.awardPoints(
                eventType = "WELCOME_BONUS",
                points = 10,
                description = "Welcome bonus — thanks for setting up Safe Companion",
                maxPerDay = 1
            )
        }
    }

    fun testApiConnection(apiKey: String) {
        viewModelScope.launch {
            _testResult.value = "testing"
            runCatching {
                val response = claudeApi.sendMessage(
                    apiKey = apiKey,
                    request = ClaudeRequest(
                        messages = listOf(ClaudeMessage(role = "user", content = "Say 'Safe Companion connected!' in one sentence.")),
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

    fun setSetupForFamily(forFamily: Boolean) {
        viewModelScope.launch { prefs.setSetupForFamily(forFamily) }
    }

    fun addFamilyContact(nickname: String, number: String) {
        viewModelScope.launch {
            val currentJson = prefs.familyContactsJson.first()
            val current = runCatching {
                gson.fromJson(currentJson, Array<FamilyContact>::class.java)?.toMutableList()
            }.getOrNull() ?: mutableListOf()
            current.add(FamilyContact(nickname = nickname, number = number))
            prefs.setFamilyContactsJson(gson.toJson(current))
        }
    }

    fun setupPin(pin: String) {
        viewModelScope.launch {
            prefs.setPinHash(keystoreManager.hashPin(pin))
            prefs.setPinSetupDone(true)
        }
    }

    /** Item 4 (option a): persist the weekly-digest opt-in choices. Called
     *  from the family-contacts onboarding page where the toggles live. */
    /** Onboarding now exposes a single "weekly report" opt-in. The Share and
     *  Email buttons live on the report screen itself, so the family-sharing
     *  flag is forced on (so the Share button is always rendered) and the
     *  auto-email flag is forced off (email is on-demand from the report). */
    fun setWeeklyDigestPrefs(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setWeeklyDigestEnabled(enabled)
            prefs.setWeeklyDigestShareFamily(true)   // always show Share on the report
            prefs.setWeeklyDigestEmailMe(false)      // no auto-email; user emails on demand
        }
    }
}
