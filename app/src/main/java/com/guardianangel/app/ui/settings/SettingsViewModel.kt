package com.guardianangel.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.guardianangel.app.data.datastore.UserPreferences
import com.guardianangel.app.data.remote.ClaudeApiService
import com.guardianangel.app.data.remote.model.ClaudeMessage
import com.guardianangel.app.data.remote.model.ClaudeRequest
import com.guardianangel.app.data.remote.model.FamilyContact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userName: String = "",
    val apiKey: String = "",
    val isSmsShieldOn: Boolean = true,
    val isCallShieldOn: Boolean = true,
    val isEmailShieldOn: Boolean = true,
    val textSizePref: String = "NORMAL",
    val familyContacts: List<FamilyContact> = emptyList(),
    val trustedNumbers: List<String> = emptyList()
)

// Intermediate typed structs to avoid Array<Any> index casting in combine
private data class CorePrefs(
    val userName: String,
    val apiKey: String,
    val smsOn: Boolean,
    val callOn: Boolean,
    val emailOn: Boolean
)

private data class ExtPrefs(
    val textSize: String,
    val familyJson: String,
    val trustedJson: String
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val claudeApi: ClaudeApiService,
    private val gson: Gson
) : ViewModel() {

    private val familyType = object : TypeToken<List<FamilyContact>>() {}.type
    private val trustedType = object : TypeToken<List<String>>() {}.type

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            prefs.userName,
            prefs.apiKey,
            prefs.isSmsShieldEnabled,
            prefs.isCallShieldEnabled,
            prefs.isEmailShieldEnabled,
            ::CorePrefs
        ),
        combine(
            prefs.textSizePref,
            prefs.familyContactsJson,
            prefs.trustedNumbersJson,
            ::ExtPrefs
        )
    ) { core, ext ->
        SettingsUiState(
            userName = core.userName,
            apiKey = core.apiKey,
            isSmsShieldOn = core.smsOn,
            isCallShieldOn = core.callOn,
            isEmailShieldOn = core.emailOn,
            textSizePref = ext.textSize,
            familyContacts = runCatching {
                gson.fromJson<List<FamilyContact>>(ext.familyJson, familyType) ?: emptyList()
            }.getOrDefault(emptyList()),
            trustedNumbers = runCatching {
                gson.fromJson<List<String>>(ext.trustedJson, trustedType) ?: emptyList()
            }.getOrDefault(emptyList())
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    fun setUserName(name: String) = viewModelScope.launch { prefs.setUserName(name) }
    fun setApiKey(key: String) = viewModelScope.launch { prefs.setApiKey(key) }
    fun setSmsShield(on: Boolean) = viewModelScope.launch { prefs.setSmsShield(on) }
    fun setCallShield(on: Boolean) = viewModelScope.launch { prefs.setCallShield(on) }
    fun setEmailShield(on: Boolean) = viewModelScope.launch { prefs.setEmailShield(on) }
    fun setTextSize(pref: String) = viewModelScope.launch { prefs.setTextSize(pref) }

    fun addFamilyContact(nickname: String, number: String) {
        viewModelScope.launch {
            val current = uiState.value.familyContacts.toMutableList()
            current.add(FamilyContact(nickname = nickname, number = number))
            prefs.setFamilyContactsJson(gson.toJson(current))
        }
    }

    fun removeFamilyContact(contact: FamilyContact) {
        viewModelScope.launch {
            val current = uiState.value.familyContacts.filter { it != contact }
            prefs.setFamilyContactsJson(gson.toJson(current))
        }
    }

    fun addTrustedNumber(number: String) {
        viewModelScope.launch {
            val current = uiState.value.trustedNumbers.toMutableList()
            if (number !in current) current.add(number)
            prefs.setTrustedNumbersJson(gson.toJson(current))
        }
    }

    fun removeTrustedNumber(number: String) {
        viewModelScope.launch {
            val current = uiState.value.trustedNumbers.filter { it != number }
            prefs.setTrustedNumbersJson(gson.toJson(current))
        }
    }

    fun testConnection(apiKey: String) {
        viewModelScope.launch {
            _testResult.value = "testing"
            runCatching {
                val response = claudeApi.sendMessage(
                    apiKey = apiKey,
                    request = ClaudeRequest(
                        messages = listOf(ClaudeMessage(role = "user", content = "Say 'connected' only.")),
                        maxTokens = 16
                    )
                )
                _testResult.value = when {
                    response.isSuccessful -> "success"
                    response.code() == 401 -> "invalid_key"
                    else -> "error"
                }
            }.onFailure { _testResult.value = "error" }
        }
    }

    fun clearTestResult() { _testResult.value = null }
}
