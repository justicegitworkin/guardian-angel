package com.guardianangel.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
}
