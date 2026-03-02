package com.guardianangel.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
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
    @ApplicationContext private val context: Context
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
    }

    // Single error-handled upstream shared by all derived flows
    private val safeData: Flow<Preferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val userName: Flow<String> = safeData.map { it[KEY_USER_NAME] ?: "" }
    val apiKey: Flow<String> = safeData.map { it[KEY_API_KEY] ?: "" }
    val isOnboardingDone: Flow<Boolean> = safeData.map { it[KEY_ONBOARDING_DONE] ?: false }
    val isSmsShieldEnabled: Flow<Boolean> = safeData.map { it[KEY_SMS_SHIELD] ?: true }
    val isCallShieldEnabled: Flow<Boolean> = safeData.map { it[KEY_CALL_SHIELD] ?: true }
    val isEmailShieldEnabled: Flow<Boolean> = safeData.map { it[KEY_EMAIL_SHIELD] ?: true }
    val textSizePref: Flow<String> = safeData.map { it[KEY_TEXT_SIZE] ?: "NORMAL" }
    val familyContactsJson: Flow<String> = safeData.map { it[KEY_FAMILY_CONTACTS] ?: "[]" }
    val trustedNumbersJson: Flow<String> = safeData.map { it[KEY_TRUSTED_NUMBERS] ?: "[]" }

    suspend fun setUserName(name: String) { dataStore.edit { it[KEY_USER_NAME] = name } }
    suspend fun setApiKey(key: String) { dataStore.edit { it[KEY_API_KEY] = key } }
    suspend fun setOnboardingDone(done: Boolean) { dataStore.edit { it[KEY_ONBOARDING_DONE] = done } }
    suspend fun setSmsShield(enabled: Boolean) { dataStore.edit { it[KEY_SMS_SHIELD] = enabled } }
    suspend fun setCallShield(enabled: Boolean) { dataStore.edit { it[KEY_CALL_SHIELD] = enabled } }
    suspend fun setEmailShield(enabled: Boolean) { dataStore.edit { it[KEY_EMAIL_SHIELD] = enabled } }
    suspend fun setTextSize(pref: String) { dataStore.edit { it[KEY_TEXT_SIZE] = pref } }
    suspend fun setFamilyContactsJson(json: String) { dataStore.edit { it[KEY_FAMILY_CONTACTS] = json } }
    suspend fun setTrustedNumbersJson(json: String) { dataStore.edit { it[KEY_TRUSTED_NUMBERS] = json } }
}
