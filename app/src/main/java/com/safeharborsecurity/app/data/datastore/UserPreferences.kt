package com.safeharborsecurity.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "safeharbor_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: com.safeharborsecurity.app.util.KeystoreManager
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
        val KEY_LISTENING_SHIELD = booleanPreferencesKey("listening_shield_enabled")
        val KEY_REMEDIATION_LAST_SYNC = longPreferencesKey("remediation_last_sync")

        // Panic Button
        val KEY_BANK_PHONE_NUMBER = stringPreferencesKey("bank_phone_number")

        // Daily Check-In
        val KEY_CHECKIN_REMINDER_TIME = stringPreferencesKey("checkin_reminder_time")
        val KEY_CHECKIN_NOTIFY_FAMILY = booleanPreferencesKey("checkin_notify_family")
        val KEY_CHECKIN_DELAY_HOURS = intPreferencesKey("checkin_delay_hours")
        val KEY_CHECKIN_MESSAGE_TEMPLATE = stringPreferencesKey("checkin_message_template")
        val KEY_LAST_CHECKIN_DATE = stringPreferencesKey("last_checkin_date")

        // Payment Warning
        val KEY_PAYMENT_WARNING_ENABLED = booleanPreferencesKey("payment_warning_enabled")
        val KEY_PAYMENT_DISMISSED_APPS = stringPreferencesKey("payment_dismissed_apps_json")

        // Item 2: Screen Monitor (MediaProjection-based on-device OCR scan).
        // Replaces NotificationListener-based SMS scanning AND UsageStats-based
        // payment-app detection.
        val KEY_SCREEN_MONITOR_ENABLED = booleanPreferencesKey("screen_monitor_enabled")

        // Operating Mode — how Safe Companion behaves when it spots a scam.
        //   WATCH_AND_WARN (default): the user gets an alert, decides what
        //                             to do. Current end-to-end behaviour.
        //   SILENT_GUARDIAN: Safe Companion auto-quarantines / auto-declines
        //                    scams and emails a weekly summary. Requires extra
        //                    permissions (default-SMS app, gmail.modify scope,
        //                    CallScreeningService role). Capabilities are
        //                    being built incrementally — see roadmap in
        //                    Settings UI.
        val KEY_OPERATING_MODE = stringPreferencesKey("operating_mode")

        // Weekly Digest Card — option (a) of the weekly-summary feature. The
        // in-app card flows from these four prefs:
        //   ENABLED      — user opted in during onboarding (or in Settings)
        //   SHARE_FAMILY — also surface a "Share with [family]" button
        //   CONTENT      — the rendered summary text (last produced by worker)
        //   GENERATED_AT — when the worker last produced a digest
        //   DISMISSED_AT — when the user last tapped Dismiss on the card
        // Card shows iff ENABLED && GENERATED_AT > DISMISSED_AT.
        val KEY_WEEKLY_DIGEST_ENABLED = booleanPreferencesKey("weekly_digest_enabled")
        val KEY_WEEKLY_DIGEST_SHARE_FAMILY = booleanPreferencesKey("weekly_digest_share_family")
        val KEY_WEEKLY_DIGEST_CONTENT = stringPreferencesKey("weekly_digest_content")
        val KEY_WEEKLY_DIGEST_GENERATED_AT = longPreferencesKey("weekly_digest_generated_at")
        val KEY_WEEKLY_DIGEST_DISMISSED_AT = longPreferencesKey("weekly_digest_dismissed_at")
        // Item 4 (option c): when true, the WeeklyDigestWorker also emails the
        // digest to the signed-in Gmail address every Sunday evening. Family
        // CC is automatic if KEY_WEEKLY_DIGEST_SHARE_FAMILY is also true AND a
        // family contact has an email saved.
        val KEY_WEEKLY_DIGEST_EMAIL_ME = booleanPreferencesKey("weekly_digest_email_me")

        // PIN / Biometric Lock
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_AUTO_LOCK_TIMEOUT = intPreferencesKey("auto_lock_timeout_minutes")
        val KEY_LAST_ACTIVE_TIME = longPreferencesKey("last_active_time")
        val KEY_PIN_SETUP_DONE = booleanPreferencesKey("pin_setup_done")

        // Privacy Promise
        val KEY_PRIVACY_ACKNOWLEDGED = booleanPreferencesKey("privacy_promise_acknowledged")

        // Notification Listener ping
        val KEY_LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")

        // Setup for family member
        val KEY_SETUP_FOR_FAMILY = booleanPreferencesKey("setup_for_family")
        val KEY_ONBOARDING_STEP = intPreferencesKey("onboarding_step")

        // WiFi Security
        val KEY_TRUSTED_WIFI = stringSetPreferencesKey("trusted_wifi_networks")

        // HIBP
        val KEY_HIBP_API_KEY = stringPreferencesKey("hibp_api_key")

        // Chat Persona & Voice
        val KEY_CHAT_PERSONA = stringPreferencesKey("chat_persona")
        val KEY_AUTO_SPEAK_RESPONSES = booleanPreferencesKey("auto_speak_responses")

        // Voice Tier System
        val KEY_VOICE_MODE = stringPreferencesKey("voice_mode")
        val KEY_ELEVEN_LABS_API_KEY = stringPreferencesKey("elevenlabs_api_key")
        val KEY_GOOGLE_NEURAL_API_KEY = stringPreferencesKey("google_neural_api_key")

        // Simple Mode
        val KEY_SIMPLE_MODE = booleanPreferencesKey("simple_mode_enabled")

        // Family Safety Alerts
        val KEY_FAMILY_ALERTS_ENABLED = booleanPreferencesKey("family_alerts_enabled")
        val KEY_FAMILY_ALERT_LEVEL = stringPreferencesKey("family_alert_level")
        val KEY_FAMILY_ALERTS_CONSENTED = booleanPreferencesKey("family_alerts_consented")

        // Alert Level (off / subtle / attention)
        val KEY_ALERT_LEVEL = stringPreferencesKey("alert_level")

        // Family Safety Watch (smart family alerts)
        val KEY_FAMILY_WATCH_ENABLED = booleanPreferencesKey("family_watch_enabled")
        val KEY_FAMILY_WATCH_ALERT_FILTER = stringPreferencesKey("family_watch_alert_filter")

        // Trusted Caller Announcements
        val KEY_CALLER_ANNOUNCEMENTS = booleanPreferencesKey("caller_announcements_enabled")

        // Daily Tip
        val KEY_LAST_TIP_DATE = stringPreferencesKey("last_tip_date")
        val KEY_LAST_TIP_INDEX = intPreferencesKey("last_tip_index")
        val KEY_TIP_DISMISSED_TODAY = booleanPreferencesKey("tip_dismissed_today")

        // Auto-Start & Guardian Service
        val KEY_AUTO_START_ENABLED = booleanPreferencesKey("auto_start_enabled")
        val KEY_AUTO_CHECK_NEW_APPS = booleanPreferencesKey("auto_check_new_apps")

        // Part D3: First-run sample data seeding
        val KEY_SAMPLE_DATA_SEEDED = booleanPreferencesKey("sample_data_seeded")
    }

    private val safeData: Flow<Preferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    // Core prefs
    val userName: Flow<String> = safeData.map { it[KEY_USER_NAME] ?: "" }
    val apiKey: Flow<String> = safeData.map { keystoreManager.decrypt(it[KEY_API_KEY] ?: "") }
    val isOnboardingDone: Flow<Boolean> = safeData.map { it[KEY_ONBOARDING_DONE] ?: false }
    val isSmsShieldEnabled: Flow<Boolean> = safeData.map { it[KEY_SMS_SHIELD] ?: true }
    val isCallShieldEnabled: Flow<Boolean> = safeData.map { it[KEY_CALL_SHIELD] ?: true }
    val isEmailShieldEnabled: Flow<Boolean> = safeData.map { it[KEY_EMAIL_SHIELD] ?: true }
    val textSizePref: Flow<String> = safeData.map { it[KEY_TEXT_SIZE] ?: "NORMAL" }
    val familyContactsJson: Flow<String> = safeData.map { it[KEY_FAMILY_CONTACTS] ?: "[]" }
    val trustedNumbersJson: Flow<String> = safeData.map { it[KEY_TRUSTED_NUMBERS] ?: "[]" }
    val isListeningShieldEnabled: Flow<Boolean> = safeData.map { it[KEY_LISTENING_SHIELD] ?: false }
    val remediationLastSync: Flow<Long> = safeData.map { it[KEY_REMEDIATION_LAST_SYNC] ?: 0L }

    // Panic button
    val bankPhoneNumber: Flow<String> = safeData.map { it[KEY_BANK_PHONE_NUMBER] ?: "" }

    // Check-in
    val checkInReminderTime: Flow<String> = safeData.map { it[KEY_CHECKIN_REMINDER_TIME] ?: "09:00" }
    val checkInNotifyFamily: Flow<Boolean> = safeData.map { it[KEY_CHECKIN_NOTIFY_FAMILY] ?: true }
    val checkInDelayHours: Flow<Int> = safeData.map { it[KEY_CHECKIN_DELAY_HOURS] ?: 2 }
    val checkInMessageTemplate: Flow<String> = safeData.map {
        it[KEY_CHECKIN_MESSAGE_TEMPLATE]
            ?: "Hi, this is an automated message from Safe Companion. {name} has not checked in today. They may need a call."
    }
    val lastCheckInDate: Flow<String> = safeData.map { it[KEY_LAST_CHECKIN_DATE] ?: "" }

    // Payment warning
    val isPaymentWarningEnabled: Flow<Boolean> = safeData.map { it[KEY_PAYMENT_WARNING_ENABLED] ?: true }
    val isScreenMonitorEnabled: Flow<Boolean> = safeData.map { it[KEY_SCREEN_MONITOR_ENABLED] ?: false }

    val operatingMode: Flow<String> = safeData.map { it[KEY_OPERATING_MODE] ?: "WATCH_AND_WARN" }

    val isWeeklyDigestEnabled: Flow<Boolean> = safeData.map { it[KEY_WEEKLY_DIGEST_ENABLED] ?: false }
    val isWeeklyDigestShareFamily: Flow<Boolean> = safeData.map { it[KEY_WEEKLY_DIGEST_SHARE_FAMILY] ?: false }
    val weeklyDigestContent: Flow<String> = safeData.map { it[KEY_WEEKLY_DIGEST_CONTENT] ?: "" }
    val weeklyDigestGeneratedAt: Flow<Long> = safeData.map { it[KEY_WEEKLY_DIGEST_GENERATED_AT] ?: 0L }
    val weeklyDigestDismissedAt: Flow<Long> = safeData.map { it[KEY_WEEKLY_DIGEST_DISMISSED_AT] ?: 0L }
    val isWeeklyDigestEmailMe: Flow<Boolean> = safeData.map { it[KEY_WEEKLY_DIGEST_EMAIL_ME] ?: false }
    val paymentDismissedAppsJson: Flow<String> = safeData.map { it[KEY_PAYMENT_DISMISSED_APPS] ?: "[]" }

    // PIN / Biometric Lock
    val pinHash: Flow<String> = safeData.map { it[KEY_PIN_HASH] ?: "" }
    val isBiometricEnabled: Flow<Boolean> = safeData.map { it[KEY_BIOMETRIC_ENABLED] ?: false }
    val autoLockTimeoutMinutes: Flow<Int> = safeData.map { it[KEY_AUTO_LOCK_TIMEOUT] ?: 5 }
    val lastActiveTime: Flow<Long> = safeData.map { it[KEY_LAST_ACTIVE_TIME] ?: 0L }
    val isPinSetupDone: Flow<Boolean> = safeData.map { it[KEY_PIN_SETUP_DONE] ?: false }

    // Privacy Promise
    val isPrivacyAcknowledged: Flow<Boolean> = safeData.map { it[KEY_PRIVACY_ACKNOWLEDGED] ?: false }

    // Notification Listener
    val lastScanTimestamp: Flow<Long> = safeData.map { it[KEY_LAST_SCAN_TIMESTAMP] ?: 0L }

    // Onboarding
    val isSetupForFamily: Flow<Boolean> = safeData.map { it[KEY_SETUP_FOR_FAMILY] ?: false }
    val onboardingStep: Flow<Int> = safeData.map { it[KEY_ONBOARDING_STEP] ?: 0 }

    // WiFi Security
    val trustedWifiNetworks: Flow<Set<String>> = safeData.map { it[KEY_TRUSTED_WIFI] ?: emptySet() }

    // HIBP
    val hibpApiKey: Flow<String> = safeData.map { keystoreManager.decrypt(it[KEY_HIBP_API_KEY] ?: "") }

    // Chat Persona & Voice
    val chatPersona: Flow<String> = safeData.map { it[KEY_CHAT_PERSONA] ?: "JAMES" }
    val autoSpeakResponses: Flow<Boolean> = safeData.map { it[KEY_AUTO_SPEAK_RESPONSES] ?: false }

    // Voice Tier System
    val voiceMode: Flow<String> = safeData.map { it[KEY_VOICE_MODE] ?: "AUTO" }
    val elevenLabsApiKey: Flow<String> = safeData.map { keystoreManager.decrypt(it[KEY_ELEVEN_LABS_API_KEY] ?: "") }
    val googleNeuralApiKey: Flow<String> = safeData.map { keystoreManager.decrypt(it[KEY_GOOGLE_NEURAL_API_KEY] ?: "") }

    // Simple Mode
    val isSimpleMode: Flow<Boolean> = safeData.map { it[KEY_SIMPLE_MODE] ?: false }

    // Family Safety Alerts
    val isFamilyAlertsEnabled: Flow<Boolean> = safeData.map { it[KEY_FAMILY_ALERTS_ENABLED] ?: false }
    val familyAlertLevel: Flow<String> = safeData.map { it[KEY_FAMILY_ALERT_LEVEL] ?: "HIGH_ONLY" }
    val isFamilyAlertsConsented: Flow<Boolean> = safeData.map { it[KEY_FAMILY_ALERTS_CONSENTED] ?: false }

    // Alert Level
    // Default alert level is "attention" — a scam detection fires the full-
    // screen GrabAttentionActivity so older users notice it. Testers can drop
    // to "subtle" (a small heads-up notification) or "off" in Settings →
    // Alert level. Three valid values: "off" | "subtle" | "attention".
    val alertLevel: Flow<String> = safeData.map { it[KEY_ALERT_LEVEL] ?: "attention" }

    // Family Safety Watch
    val isFamilyWatchEnabled: Flow<Boolean> = safeData.map { it[KEY_FAMILY_WATCH_ENABLED] ?: false }
    val familyWatchAlertFilter: Flow<String> = safeData.map { it[KEY_FAMILY_WATCH_ALERT_FILTER] ?: "HIGH_AND_CRITICAL" }

    // Trusted Caller Announcements
    val isCallerAnnouncementsEnabled: Flow<Boolean> = safeData.map { it[KEY_CALLER_ANNOUNCEMENTS] ?: true }

    // Daily Tip
    val lastTipDate: Flow<String> = safeData.map { it[KEY_LAST_TIP_DATE] ?: "" }
    val lastTipIndex: Flow<Int> = safeData.map { it[KEY_LAST_TIP_INDEX] ?: -1 }
    val isTipDismissedToday: Flow<Boolean> = safeData.map { it[KEY_TIP_DISMISSED_TODAY] ?: false }

    // Auto-Start & Guardian Service
    val isAutoStartEnabled: Flow<Boolean> = safeData.map { it[KEY_AUTO_START_ENABLED] ?: true }
    val isAutoCheckNewApps: Flow<Boolean> = safeData.map { it[KEY_AUTO_CHECK_NEW_APPS] ?: true }

    // First-run sample data
    val isSampleDataSeeded: Flow<Boolean> = safeData.map { it[KEY_SAMPLE_DATA_SEEDED] ?: false }

    // Setters
    suspend fun setUserName(name: String) { dataStore.edit { it[KEY_USER_NAME] = name } }
    suspend fun setApiKey(key: String) { dataStore.edit { it[KEY_API_KEY] = keystoreManager.encrypt(key) } }
    suspend fun setOnboardingDone(done: Boolean) { dataStore.edit { it[KEY_ONBOARDING_DONE] = done } }
    suspend fun setSmsShield(enabled: Boolean) { dataStore.edit { it[KEY_SMS_SHIELD] = enabled } }
    suspend fun setCallShield(enabled: Boolean) { dataStore.edit { it[KEY_CALL_SHIELD] = enabled } }
    suspend fun setEmailShield(enabled: Boolean) { dataStore.edit { it[KEY_EMAIL_SHIELD] = enabled } }
    suspend fun setTextSize(pref: String) { dataStore.edit { it[KEY_TEXT_SIZE] = pref } }
    suspend fun setFamilyContactsJson(json: String) { dataStore.edit { it[KEY_FAMILY_CONTACTS] = json } }
    suspend fun setTrustedNumbersJson(json: String) { dataStore.edit { it[KEY_TRUSTED_NUMBERS] = json } }
    suspend fun setListeningShield(enabled: Boolean) { dataStore.edit { it[KEY_LISTENING_SHIELD] = enabled } }
    suspend fun setRemediationLastSync(timestamp: Long) { dataStore.edit { it[KEY_REMEDIATION_LAST_SYNC] = timestamp } }
    suspend fun setBankPhoneNumber(number: String) { dataStore.edit { it[KEY_BANK_PHONE_NUMBER] = number } }
    suspend fun setCheckInReminderTime(time: String) { dataStore.edit { it[KEY_CHECKIN_REMINDER_TIME] = time } }
    suspend fun setCheckInNotifyFamily(enabled: Boolean) { dataStore.edit { it[KEY_CHECKIN_NOTIFY_FAMILY] = enabled } }
    suspend fun setCheckInDelayHours(hours: Int) { dataStore.edit { it[KEY_CHECKIN_DELAY_HOURS] = hours } }
    suspend fun setCheckInMessageTemplate(template: String) { dataStore.edit { it[KEY_CHECKIN_MESSAGE_TEMPLATE] = template } }
    suspend fun setLastCheckInDate(date: String) { dataStore.edit { it[KEY_LAST_CHECKIN_DATE] = date } }
    suspend fun setPaymentWarningEnabled(enabled: Boolean) { dataStore.edit { it[KEY_PAYMENT_WARNING_ENABLED] = enabled } }
    suspend fun setScreenMonitorEnabled(enabled: Boolean) { dataStore.edit { it[KEY_SCREEN_MONITOR_ENABLED] = enabled } }

    suspend fun setOperatingMode(mode: String) { dataStore.edit { it[KEY_OPERATING_MODE] = mode } }

    suspend fun setWeeklyDigestEnabled(enabled: Boolean) { dataStore.edit { it[KEY_WEEKLY_DIGEST_ENABLED] = enabled } }
    suspend fun setWeeklyDigestShareFamily(enabled: Boolean) { dataStore.edit { it[KEY_WEEKLY_DIGEST_SHARE_FAMILY] = enabled } }
    suspend fun setWeeklyDigestContent(content: String, generatedAt: Long) {
        dataStore.edit {
            it[KEY_WEEKLY_DIGEST_CONTENT] = content
            it[KEY_WEEKLY_DIGEST_GENERATED_AT] = generatedAt
        }
    }
    suspend fun dismissWeeklyDigest() { dataStore.edit { it[KEY_WEEKLY_DIGEST_DISMISSED_AT] = System.currentTimeMillis() } }
    suspend fun setWeeklyDigestEmailMe(enabled: Boolean) { dataStore.edit { it[KEY_WEEKLY_DIGEST_EMAIL_ME] = enabled } }
    suspend fun setPaymentDismissedAppsJson(json: String) { dataStore.edit { it[KEY_PAYMENT_DISMISSED_APPS] = json } }

    // PIN / Biometric Lock
    suspend fun setPinHash(hash: String) { dataStore.edit { it[KEY_PIN_HASH] = hash } }
    suspend fun setBiometricEnabled(enabled: Boolean) { dataStore.edit { it[KEY_BIOMETRIC_ENABLED] = enabled } }
    suspend fun setAutoLockTimeoutMinutes(minutes: Int) { dataStore.edit { it[KEY_AUTO_LOCK_TIMEOUT] = minutes } }
    suspend fun setLastActiveTime(time: Long) { dataStore.edit { it[KEY_LAST_ACTIVE_TIME] = time } }
    suspend fun setPinSetupDone(done: Boolean) { dataStore.edit { it[KEY_PIN_SETUP_DONE] = done } }

    // Privacy Promise
    suspend fun setPrivacyAcknowledged(acknowledged: Boolean) { dataStore.edit { it[KEY_PRIVACY_ACKNOWLEDGED] = acknowledged } }

    // Notification Listener
    suspend fun setLastScanTimestamp(timestamp: Long) { dataStore.edit { it[KEY_LAST_SCAN_TIMESTAMP] = timestamp } }

    // Onboarding
    suspend fun setSetupForFamily(forFamily: Boolean) { dataStore.edit { it[KEY_SETUP_FOR_FAMILY] = forFamily } }
    suspend fun setOnboardingStep(step: Int) { dataStore.edit { it[KEY_ONBOARDING_STEP] = step } }

    // WiFi Security
    suspend fun addTrustedWifiNetwork(ssid: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_TRUSTED_WIFI] ?: emptySet()
            prefs[KEY_TRUSTED_WIFI] = current + ssid
        }
    }

    suspend fun removeTrustedWifiNetwork(ssid: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_TRUSTED_WIFI] ?: emptySet()
            prefs[KEY_TRUSTED_WIFI] = current - ssid
        }
    }

    // HIBP
    suspend fun setHibpApiKey(key: String) { dataStore.edit { it[KEY_HIBP_API_KEY] = keystoreManager.encrypt(key) } }

    suspend fun setChatPersona(persona: String) { dataStore.edit { it[KEY_CHAT_PERSONA] = persona } }
    suspend fun setAutoSpeakResponses(enabled: Boolean) { dataStore.edit { it[KEY_AUTO_SPEAK_RESPONSES] = enabled } }

    // Voice Tier System
    suspend fun setVoiceMode(mode: String) { dataStore.edit { it[KEY_VOICE_MODE] = mode } }
    suspend fun setElevenLabsApiKey(key: String) { dataStore.edit { it[KEY_ELEVEN_LABS_API_KEY] = keystoreManager.encrypt(key) } }
    suspend fun setGoogleNeuralApiKey(key: String) { dataStore.edit { it[KEY_GOOGLE_NEURAL_API_KEY] = keystoreManager.encrypt(key) } }

    // Simple Mode
    suspend fun setSimpleMode(enabled: Boolean) { dataStore.edit { it[KEY_SIMPLE_MODE] = enabled } }

    // Family Safety Alerts
    suspend fun setFamilyAlertsEnabled(enabled: Boolean) { dataStore.edit { it[KEY_FAMILY_ALERTS_ENABLED] = enabled } }
    suspend fun setFamilyAlertLevel(level: String) { dataStore.edit { it[KEY_FAMILY_ALERT_LEVEL] = level } }
    suspend fun setFamilyAlertsConsented(consented: Boolean) { dataStore.edit { it[KEY_FAMILY_ALERTS_CONSENTED] = consented } }

    // Alert Level
    suspend fun setAlertLevel(level: String) { dataStore.edit { it[KEY_ALERT_LEVEL] = level } }

    // Family Safety Watch
    suspend fun setFamilyWatchEnabled(enabled: Boolean) { dataStore.edit { it[KEY_FAMILY_WATCH_ENABLED] = enabled } }
    suspend fun setFamilyWatchAlertFilter(filter: String) { dataStore.edit { it[KEY_FAMILY_WATCH_ALERT_FILTER] = filter } }

    // Trusted Caller Announcements
    suspend fun setCallerAnnouncementsEnabled(enabled: Boolean) { dataStore.edit { it[KEY_CALLER_ANNOUNCEMENTS] = enabled } }

    // Daily Tip
    suspend fun setLastTipDate(date: String) { dataStore.edit { it[KEY_LAST_TIP_DATE] = date } }
    suspend fun setLastTipIndex(index: Int) { dataStore.edit { it[KEY_LAST_TIP_INDEX] = index } }
    suspend fun setTipDismissedToday(dismissed: Boolean) { dataStore.edit { it[KEY_TIP_DISMISSED_TODAY] = dismissed } }

    // Auto-Start & Guardian
    suspend fun setAutoStartEnabled(enabled: Boolean) { dataStore.edit { it[KEY_AUTO_START_ENABLED] = enabled } }
    suspend fun setAutoCheckNewApps(enabled: Boolean) { dataStore.edit { it[KEY_AUTO_CHECK_NEW_APPS] = enabled } }

    // First-run sample data
    suspend fun setSampleDataSeeded(seeded: Boolean) { dataStore.edit { it[KEY_SAMPLE_DATA_SEEDED] = seeded } }

    // Synchronous alert level getter for NotificationHelper
    fun getAlertLevelSync(): String {
        return kotlinx.coroutines.runBlocking {
            alertLevel.first()
        }
    }

    // Clear all data
    suspend fun clearAll() { dataStore.edit { it.clear() } }
}
