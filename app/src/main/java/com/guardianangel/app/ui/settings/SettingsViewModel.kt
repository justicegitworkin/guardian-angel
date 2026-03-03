package com.guardianangel.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.guardianangel.app.data.datastore.UserPreferences
import com.guardianangel.app.data.local.dao.AlertDao
import com.guardianangel.app.data.local.dao.CallLogDao
import com.guardianangel.app.data.local.dao.MessageDao
import com.guardianangel.app.data.local.dao.ScamRuleDao
import com.guardianangel.app.data.local.entity.ScamRuleEntity
import com.guardianangel.app.data.remote.ClaudeApiService
import com.guardianangel.app.data.remote.model.ClaudeMessage
import com.guardianangel.app.data.remote.model.ClaudeRequest
import com.guardianangel.app.data.remote.model.FamilyContact
import com.guardianangel.app.service.WakeWordService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val trustedNumbers: List<String> = emptyList(),
    val isWakeWordEnabled: Boolean = false,
    val porcupineKey: String = "",
    // Privacy
    val privacyMode: String = "AUTO",       // "AUTO" | "ON" | "OFF"
    val saveHistory: Boolean = false,
    val cloudMessagesToday: Int = 0,
    // Analytics
    val chatSessions: Int = 0,
    val smsAlerts: Int = 0,
    val callsScreened: Int = 0,
    val callFriendTaps: Int = 0
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

private data class WakePrefs(
    val wakeWordEnabled: Boolean,
    val porcupineKey: String
)

private data class PrivacyPrefs(
    val privacyMode: String,
    val saveHistory: Boolean,
    val cloudMsgCount: Int
)

private data class AnalyticsPrefs(
    val chatSessions: Int,
    val smsAlerts: Int,
    val callsScreened: Int,
    val callFriendTaps: Int
)

data class IntelSettingsState(
    val serverUrl: String = "",
    val lastSyncMs: Long = 0L,
    val notifications: Boolean = true,
    val allRules: List<ScamRuleEntity> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
    private val claudeApi: ClaudeApiService,
    private val gson: Gson,
    private val alertDao: AlertDao,
    private val messageDao: MessageDao,
    private val callLogDao: CallLogDao,
    private val scamRuleDao: ScamRuleDao
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
        ),
        combine(
            prefs.isWakeWordEnabled,
            prefs.porcupineAccessKey,
            ::WakePrefs
        ),
        combine(
            prefs.privacyMode,
            prefs.saveConversationHistory,
            prefs.cloudMessagesToday,
            ::PrivacyPrefs
        ),
        combine(
            prefs.chatSessionCount,
            prefs.smsAlertCount,
            prefs.callsScreenedCount,
            prefs.callFriendTapCount,
            ::AnalyticsPrefs
        )
    ) { core, ext, wake, privacy, analytics ->
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
            }.getOrDefault(emptyList()),
            isWakeWordEnabled = wake.wakeWordEnabled,
            porcupineKey = wake.porcupineKey,
            privacyMode = privacy.privacyMode,
            saveHistory = privacy.saveHistory,
            cloudMessagesToday = privacy.cloudMsgCount,
            chatSessions = analytics.chatSessions,
            smsAlerts = analytics.smsAlerts,
            callsScreened = analytics.callsScreened,
            callFriendTaps = analytics.callFriendTaps
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

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setWakeWordEnabled(enabled)
            val intent = Intent(context, WakeWordService::class.java)
            if (enabled) {
                context.startForegroundService(intent)
            } else {
                context.stopService(intent)
            }
        }
    }

    fun setPorcupineKey(key: String) = viewModelScope.launch { prefs.setPorcupineKey(key) }

    // ── Privacy ───────────────────────────────────────────────────────────
    fun setPrivacyMode(mode: String) = viewModelScope.launch { prefs.setPrivacyMode(mode) }
    fun setSaveHistory(save: Boolean) = viewModelScope.launch { prefs.setSaveConversationHistory(save) }

    private val _clearDataResult = MutableStateFlow<String?>(null)
    val clearDataResult: StateFlow<String?> = _clearDataResult.asStateFlow()

    fun clearAllData() {
        viewModelScope.launch {
            runCatching {
                alertDao.deleteAll()
                messageDao.deleteAll()
                callLogDao.deleteAll()
                prefs.resetCloudMessageCount()
            }.onSuccess {
                _clearDataResult.value = "cleared"
            }.onFailure {
                _clearDataResult.value = "error"
            }
        }
    }

    fun dismissClearDataResult() { _clearDataResult.value = null }

    // ── Scam Intelligence ─────────────────────────────────────────────
    val intelSettings: StateFlow<IntelSettingsState> = combine(
        combine(prefs.scamIntelServerUrl, prefs.scamIntelLastSync, prefs.scamIntelNotifications) {
            url, sync, notify -> Triple(url, sync, notify)
        },
        scamRuleDao.observeAllRules()
    ) { (url, sync, notify), rules ->
        IntelSettingsState(
            serverUrl     = url,
            lastSyncMs    = sync,
            notifications = notify,
            allRules      = rules
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IntelSettingsState())

    fun setScamIntelServerUrl(url: String) = viewModelScope.launch { prefs.setScamIntelServerUrl(url) }
    fun setScamIntelNotifications(on: Boolean) = viewModelScope.launch { prefs.setScamIntelNotifications(on) }
}
