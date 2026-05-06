package com.safeharborsecurity.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.remote.ClaudeApiService
import com.safeharborsecurity.app.data.remote.model.ClaudeMessage
import com.safeharborsecurity.app.data.remote.model.ClaudeRequest
import com.safeharborsecurity.app.data.remote.model.FamilyContact
import com.safeharborsecurity.app.service.RemediationSyncWorker
import com.safeharborsecurity.app.util.KeystoreManager
import com.safeharborsecurity.app.util.SafeHarborVoiceManager
import com.safeharborsecurity.app.data.model.VoiceTier
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
    val remediationLastSync: Long = 0L,
    val bankPhoneNumber: String = "",
    val checkInNotifyFamily: Boolean = true,
    val checkInReminderTime: String = "09:00",
    val hasPinSet: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockTimeoutMinutes: Int = 5,
    val elevenLabsApiKey: String = "",
    val currentVoiceTier: VoiceTier = VoiceTier.ANDROID_TTS,
    val isFamilyAlertsEnabled: Boolean = false,
    val familyAlertLevel: String = "HIGH_ONLY",
    val isFamilyAlertsConsented: Boolean = false,
    val alertLevel: String = "subtle",
    val callerAnnouncementsEnabled: Boolean = true,
    val operatingMode: String = "WATCH_AND_WARN"
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
    private val gson: Gson,
    private val keystoreManager: KeystoreManager,
    private val voiceManager: SafeHarborVoiceManager,
    @ApplicationContext private val appContext: Context
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
        prefs.remediationLastSync
    ) { core, ext, lastSync ->
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
            remediationLastSync = lastSync
        )
    }.combine(combine(prefs.bankPhoneNumber, prefs.checkInNotifyFamily, prefs.checkInReminderTime) { bank, notify, time ->
        Triple(bank, notify, time)
    }) { state, (bank, notify, time) ->
        state.copy(
            bankPhoneNumber = bank,
            checkInNotifyFamily = notify,
            checkInReminderTime = time
        )
    }.combine(combine(prefs.pinHash, prefs.isBiometricEnabled, prefs.autoLockTimeoutMinutes) { pinHash, bio, timeout ->
        Triple(pinHash, bio, timeout)
    }) { state, (pinHash, bio, timeout) ->
        state.copy(
            hasPinSet = pinHash.isNotBlank(),
            biometricEnabled = bio,
            autoLockTimeoutMinutes = timeout
        )
    }.combine(prefs.elevenLabsApiKey) { state, elKey ->
        state.copy(
            elevenLabsApiKey = elKey,
            currentVoiceTier = if (elKey.isNotBlank()) VoiceTier.ELEVEN_LABS else VoiceTier.ANDROID_TTS
        )
    }.combine(combine(prefs.isFamilyAlertsEnabled, prefs.familyAlertLevel, prefs.isFamilyAlertsConsented) { enabled, level, consented ->
        Triple(enabled, level, consented)
    }) { state, (enabled, level, consented) ->
        state.copy(
            isFamilyAlertsEnabled = enabled,
            familyAlertLevel = level,
            isFamilyAlertsConsented = consented
        )
    }.combine(combine(prefs.alertLevel, prefs.isCallerAnnouncementsEnabled) { alert, caller ->
        Pair(alert, caller)
    }) { state, (alert, caller) ->
        state.copy(
            alertLevel = alert,
            callerAnnouncementsEnabled = caller
        )
    }.combine(prefs.operatingMode) { state, mode ->
        state.copy(operatingMode = mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    fun setUserName(name: String) = viewModelScope.launch { prefs.setUserName(name) }
    fun setApiKey(key: String) = viewModelScope.launch { prefs.setApiKey(key) }
    fun setSmsShield(on: Boolean) = viewModelScope.launch { prefs.setSmsShield(on) }
    fun setOperatingMode(mode: String) = viewModelScope.launch { prefs.setOperatingMode(mode) }
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

    fun refreshKnowledgeBase() {
        RemediationSyncWorker.enqueueOneTimeSync(appContext)
    }

    fun setBankPhoneNumber(number: String) = viewModelScope.launch { prefs.setBankPhoneNumber(number) }
    fun setCheckInNotifyFamily(enabled: Boolean) = viewModelScope.launch { prefs.setCheckInNotifyFamily(enabled) }

    fun changePin(newPin: String) = viewModelScope.launch {
        prefs.setPinHash(keystoreManager.hashPin(newPin))
        prefs.setPinSetupDone(true)
    }
    fun removePin() = viewModelScope.launch {
        prefs.setPinHash("")
        prefs.setPinSetupDone(false)
    }
    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setBiometricEnabled(enabled) }
    fun setAutoLockTimeout(minutes: Int) = viewModelScope.launch { prefs.setAutoLockTimeoutMinutes(minutes) }
    fun lockAppNow() = viewModelScope.launch { prefs.setLastActiveTime(0L) }

    // Family Safety Alerts
    fun setFamilyAlertsEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setFamilyAlertsEnabled(enabled) }
    fun setFamilyAlertLevel(level: String) = viewModelScope.launch { prefs.setFamilyAlertLevel(level) }
    fun setFamilyAlertsConsented() = viewModelScope.launch { prefs.setFamilyAlertsConsented(true) }

    // Alert Level
    fun setAlertLevel(level: String) = viewModelScope.launch { prefs.setAlertLevel(level) }

    // Caller Announcements
    fun setCallerAnnouncements(enabled: Boolean) = viewModelScope.launch { prefs.setCallerAnnouncementsEnabled(enabled) }

    fun deleteAllData() = viewModelScope.launch {
        prefs.clearAll()
    }

    // Voice Quality
    private val _voiceTestResult = MutableStateFlow<String?>(null)
    val voiceTestResult: StateFlow<String?> = _voiceTestResult.asStateFlow()

    val voiceDebugLog: StateFlow<String> = voiceManager.debugLog

    fun saveElevenLabsKey(key: String) {
        viewModelScope.launch {
            // Part B1: never log key length or prefix.
            android.util.Log.d("EL_DEBUG", "Check A: Saving ElevenLabs key (present=${key.isNotBlank()})")
            prefs.setElevenLabsApiKey(key)
        }
    }

    fun validateElevenLabsKey(key: String): String? {
        return when {
            key.isBlank() -> "Key cannot be empty"
            key.length < 32 -> "Key seems too short — please check it"
            !key.startsWith("sk_") -> "Key should start with sk_ — please check it"
            else -> null
        }
    }

    fun testElevenLabsVoice(apiKey: String) {
        viewModelScope.launch {
            _voiceTestResult.value = "Connecting to ElevenLabs..."
            voiceManager.initialize()
            // Save the key so voice manager can read it
            prefs.setElevenLabsApiKey(apiKey)

            // Small delay to let DataStore flush
            kotlinx.coroutines.delay(200)

            voiceManager.addDebugLine("Test: starting voice test")

            try {
                voiceManager.speak(
                    text = "Hello! I'm Grace, your Safe Companion companion. I'm here to keep you safe.",
                    personaId = "GRACE",
                    onStart = {
                        val tier = voiceManager.currentTier.value
                        _voiceTestResult.value = "Playing via $tier..."
                        voiceManager.addDebugLine("Test: playing via $tier")
                    },
                    onDone = {
                        val tier = voiceManager.currentTier.value
                        if (tier == VoiceTier.ELEVEN_LABS) {
                            _voiceTestResult.value = "el_success"
                        } else {
                            _voiceTestResult.value = "fallback_$tier"
                        }
                        voiceManager.addDebugLine("Test: done via $tier")
                    }
                )
            } catch (e: Exception) {
                _voiceTestResult.value = "error:${e.message}"
                voiceManager.addDebugLine("Test: EXCEPTION — ${e.message}")
            }
        }
    }

    fun clearVoiceTestResult() { _voiceTestResult.value = null }
}
