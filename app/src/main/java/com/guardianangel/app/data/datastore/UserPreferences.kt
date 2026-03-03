package com.guardianangel.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guardianangel.app.security.SecureStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "guardian_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorageManager
) {
    private val dataStore = context.dataStore

    companion object {
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_API_KEY = stringPreferencesKey("claude_api_key")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_complete")
        val KEY_SMS_SHIELD = booleanPreferencesKey("sms_shield_enabled")
        val KEY_CALL_SHIELD = booleanPreferencesKey("call_shield_enabled")
        val KEY_EMAIL_SHIELD = booleanPreferencesKey("email_shield_enabled")
        val KEY_TEXT_SIZE = stringPreferencesKey("text_size_pref")
        val KEY_FAMILY_CONTACTS = stringPreferencesKey("family_contacts_json")
        val KEY_TRUSTED_NUMBERS = stringPreferencesKey("trusted_numbers_json")
        val KEY_WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val KEY_PORCUPINE_KEY = stringPreferencesKey("porcupine_access_key")
        // Privacy
        val KEY_PRIVACY_MODE = stringPreferencesKey("privacy_mode")        // "AUTO"|"ON"|"OFF"
        val KEY_SAVE_CONVERSATION = booleanPreferencesKey("save_conversation_history")
        val KEY_CLOUD_MSG_COUNT = intPreferencesKey("cloud_messages_today")
        val KEY_CLOUD_MSG_DATE = stringPreferencesKey("cloud_messages_date") // "yyyy-MM-dd"
        // Scam intelligence sync
        val KEY_SCAM_INTEL_SERVER_URL    = stringPreferencesKey("scam_intel_server_url")
        val KEY_SCAM_INTEL_LAST_SYNC     = longPreferencesKey("scam_intel_last_sync")
        val KEY_SCAM_INTEL_NOTIFICATIONS = booleanPreferencesKey("scam_intel_notifications")
        // Analytics (local only, counts only, no personal data)
        val KEY_ANALYTICS_CHAT_SESSIONS = intPreferencesKey("analytics_chat_sessions")
        val KEY_ANALYTICS_SMS_ALERTS = intPreferencesKey("analytics_sms_alerts")
        val KEY_ANALYTICS_CALLS_SCREENED = intPreferencesKey("analytics_calls_screened")
        val KEY_ANALYTICS_CALL_FRIEND_TAPS = intPreferencesKey("analytics_call_friend_taps")
        // Shake to activate
        val KEY_SHAKE_ENABLED = booleanPreferencesKey("shake_to_activate_enabled")
        val KEY_SHAKE_INTRO_SHOWN = booleanPreferencesKey("shake_intro_shown")
    }

    // Single error-handled upstream shared by all derived flows
    private val safeData: Flow<Preferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val userName: Flow<String> = safeData.map { it[KEY_USER_NAME] ?: "" }
    // API key is stored encrypted; falls back to plaintext for migration from old installs
    val apiKey: Flow<String> = safeData.map { prefs ->
        val stored = prefs[KEY_API_KEY] ?: ""
        if (stored.isBlank()) "" else secureStorage.decrypt(stored) ?: stored
    }
    val isOnboardingDone: Flow<Boolean> = safeData.map { it[KEY_ONBOARDING_DONE] ?: false }
    val isSmsShieldEnabled: Flow<Boolean> = safeData.map { it[KEY_SMS_SHIELD] ?: true }
    val isCallShieldEnabled: Flow<Boolean> = safeData.map { it[KEY_CALL_SHIELD] ?: true }
    val isEmailShieldEnabled: Flow<Boolean> = safeData.map { it[KEY_EMAIL_SHIELD] ?: true }
    val textSizePref: Flow<String> = safeData.map { it[KEY_TEXT_SIZE] ?: "LARGE" }
    val familyContactsJson: Flow<String> = safeData.map { it[KEY_FAMILY_CONTACTS] ?: "[]" }
    val trustedNumbersJson: Flow<String> = safeData.map { it[KEY_TRUSTED_NUMBERS] ?: "[]" }
    val isWakeWordEnabled: Flow<Boolean> = safeData.map { it[KEY_WAKE_WORD_ENABLED] ?: false }
    val porcupineAccessKey: Flow<String> = safeData.map { it[KEY_PORCUPINE_KEY] ?: "" }
    // Privacy
    val privacyMode: Flow<String> = safeData.map { it[KEY_PRIVACY_MODE] ?: "AUTO" }
    val saveConversationHistory: Flow<Boolean> = safeData.map { it[KEY_SAVE_CONVERSATION] ?: false }
    val cloudMessagesToday: Flow<Int> = safeData.map { it[KEY_CLOUD_MSG_COUNT] ?: 0 }
    val cloudMessagesDate: Flow<String> = safeData.map { it[KEY_CLOUD_MSG_DATE] ?: "" }
    // Scam intelligence
    val scamIntelServerUrl: Flow<String>    = safeData.map { it[KEY_SCAM_INTEL_SERVER_URL]    ?: "" }
    val scamIntelLastSync: Flow<Long>       = safeData.map { it[KEY_SCAM_INTEL_LAST_SYNC]     ?: 0L }
    val scamIntelNotifications: Flow<Boolean> = safeData.map { it[KEY_SCAM_INTEL_NOTIFICATIONS] ?: true }
    // Analytics
    val chatSessionCount: Flow<Int> = safeData.map { it[KEY_ANALYTICS_CHAT_SESSIONS] ?: 0 }
    val smsAlertCount: Flow<Int> = safeData.map { it[KEY_ANALYTICS_SMS_ALERTS] ?: 0 }
    val callsScreenedCount: Flow<Int> = safeData.map { it[KEY_ANALYTICS_CALLS_SCREENED] ?: 0 }
    val callFriendTapCount: Flow<Int> = safeData.map { it[KEY_ANALYTICS_CALL_FRIEND_TAPS] ?: 0 }
    // Shake to activate
    val isShakeEnabled: Flow<Boolean> = safeData.map { it[KEY_SHAKE_ENABLED] ?: true }
    val shakeIntroShown: Flow<Boolean> = safeData.map { it[KEY_SHAKE_INTRO_SHOWN] ?: false }

    suspend fun setUserName(name: String) { dataStore.edit { it[KEY_USER_NAME] = name } }
    suspend fun setApiKey(key: String) {
        dataStore.edit { it[KEY_API_KEY] = if (key.isBlank()) "" else secureStorage.encrypt(key) }
    }
    suspend fun setOnboardingDone(done: Boolean) { dataStore.edit { it[KEY_ONBOARDING_DONE] = done } }
    suspend fun setSmsShield(enabled: Boolean) { dataStore.edit { it[KEY_SMS_SHIELD] = enabled } }
    suspend fun setCallShield(enabled: Boolean) { dataStore.edit { it[KEY_CALL_SHIELD] = enabled } }
    suspend fun setEmailShield(enabled: Boolean) { dataStore.edit { it[KEY_EMAIL_SHIELD] = enabled } }
    suspend fun setTextSize(pref: String) { dataStore.edit { it[KEY_TEXT_SIZE] = pref } }
    suspend fun setFamilyContactsJson(json: String) { dataStore.edit { it[KEY_FAMILY_CONTACTS] = json } }
    suspend fun setTrustedNumbersJson(json: String) { dataStore.edit { it[KEY_TRUSTED_NUMBERS] = json } }
    suspend fun setWakeWordEnabled(enabled: Boolean) { dataStore.edit { it[KEY_WAKE_WORD_ENABLED] = enabled } }
    suspend fun setPorcupineKey(key: String) { dataStore.edit { it[KEY_PORCUPINE_KEY] = key } }
    // Privacy
    suspend fun setPrivacyMode(mode: String) { dataStore.edit { it[KEY_PRIVACY_MODE] = mode } }
    suspend fun setSaveConversationHistory(save: Boolean) { dataStore.edit { it[KEY_SAVE_CONVERSATION] = save } }
    suspend fun incrementCloudMessageCount(date: String) {
        dataStore.edit { prefs ->
            val storedDate = prefs[KEY_CLOUD_MSG_DATE] ?: ""
            val count = if (storedDate == date) (prefs[KEY_CLOUD_MSG_COUNT] ?: 0) else 0
            prefs[KEY_CLOUD_MSG_DATE] = date
            prefs[KEY_CLOUD_MSG_COUNT] = count + 1
        }
    }
    suspend fun resetCloudMessageCount() {
        dataStore.edit {
            it[KEY_CLOUD_MSG_COUNT] = 0
            it[KEY_CLOUD_MSG_DATE] = ""
        }
    }

    // Scam intelligence setters
    suspend fun setScamIntelServerUrl(url: String) {
        dataStore.edit { it[KEY_SCAM_INTEL_SERVER_URL] = url }
    }
    suspend fun setScamIntelLastSync(ts: Long) {
        dataStore.edit { it[KEY_SCAM_INTEL_LAST_SYNC] = ts }
    }
    suspend fun setScamIntelNotifications(enabled: Boolean) {
        dataStore.edit { it[KEY_SCAM_INTEL_NOTIFICATIONS] = enabled }
    }

    // Analytics increment helpers
    suspend fun recordChatSession() {
        dataStore.edit { it[KEY_ANALYTICS_CHAT_SESSIONS] = (it[KEY_ANALYTICS_CHAT_SESSIONS] ?: 0) + 1 }
    }
    suspend fun recordSmsAlert() {
        dataStore.edit { it[KEY_ANALYTICS_SMS_ALERTS] = (it[KEY_ANALYTICS_SMS_ALERTS] ?: 0) + 1 }
    }
    suspend fun recordCallScreened() {
        dataStore.edit { it[KEY_ANALYTICS_CALLS_SCREENED] = (it[KEY_ANALYTICS_CALLS_SCREENED] ?: 0) + 1 }
    }
    suspend fun recordCallFriendTap() {
        dataStore.edit { it[KEY_ANALYTICS_CALL_FRIEND_TAPS] = (it[KEY_ANALYTICS_CALL_FRIEND_TAPS] ?: 0) + 1 }
    }

    // Shake to activate
    suspend fun setShakeEnabled(enabled: Boolean) { dataStore.edit { it[KEY_SHAKE_ENABLED] = enabled } }
    suspend fun setShakeIntroShown(shown: Boolean) { dataStore.edit { it[KEY_SHAKE_INTRO_SHOWN] = shown } }
}
