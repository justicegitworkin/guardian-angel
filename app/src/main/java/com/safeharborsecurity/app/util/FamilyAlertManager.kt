package com.safeharborsecurity.app.util

import android.content.Context
import android.telephony.SmsManager
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.model.AlertLevel
import com.safeharborsecurity.app.data.model.AlertTrigger
import com.safeharborsecurity.app.data.model.FamilyAlertEvent
import com.safeharborsecurity.app.data.remote.model.FamilyContact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "FamilyAlert"
        private const val RATE_LIMIT_MS = 10 * 60 * 1000L // 10 minutes
    }

    private var lastAlertTimestamp = 0L

    private val _alertHistory = MutableStateFlow<List<FamilyAlertEvent>>(emptyList())
    val alertHistory: StateFlow<List<FamilyAlertEvent>> = _alertHistory.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    var isEnabled: Boolean
        get() = _isEnabled.value
        set(value) { _isEnabled.value = value }

    private val _alertLevel = MutableStateFlow(AlertLevel.HIGH_ONLY)
    var alertLevel: AlertLevel
        get() = _alertLevel.value
        set(value) { _alertLevel.value = value }

    val isEnabledFlow: StateFlow<Boolean> = _isEnabled.asStateFlow()
    val alertLevelFlow: StateFlow<AlertLevel> = _alertLevel.asStateFlow()

    /**
     * Trigger a family alert for a specific event.
     * Returns true if alert was sent, false if skipped (rate limited, disabled, no contacts, etc.)
     */
    suspend fun triggerAlert(
        trigger: AlertTrigger,
        confidence: Int = 100
    ): Boolean {
        // Reflect persisted prefs into in-memory state
        val enabledPref = userPreferences.isFamilyAlertsEnabled.first()
        val consentedPref = userPreferences.isFamilyAlertsConsented.first()
        if (!enabledPref || !consentedPref) {
            Log.d(TAG, "Family alerts disabled/unconsented, skipping ${trigger.name}")
            return false
        }
        // Apply alert level filter from prefs
        val level = runCatching { AlertLevel.valueOf(userPreferences.familyAlertLevel.first()) }
            .getOrDefault(AlertLevel.HIGH_ONLY)
        if (confidence < level.minConfidence) {
            Log.d(TAG, "Confidence $confidence below threshold ${level.minConfidence}, skipping")
            return false
        }

        // Rate limiting
        val now = System.currentTimeMillis()
        if (now - lastAlertTimestamp < RATE_LIMIT_MS) {
            Log.d(TAG, "Rate limited, last alert ${(now - lastAlertTimestamp) / 1000}s ago")
            return false
        }

        val contacts = getContacts()
        if (contacts.isEmpty()) {
            Log.d(TAG, "No family contacts configured")
            return false
        }

        val userName = userPreferences.userName.first()
        val displayName = userName.ifBlank { "Your family member" }
        val message = trigger.messageTemplate.replace("{name}", displayName)

        var sent = false
        contacts.forEach { contact ->
            val success = sendSms(contact.number, message)
            if (success) {
                sent = true
                val event = FamilyAlertEvent(
                    trigger = trigger,
                    message = message,
                    contactName = contact.nickname,
                    contactNumber = contact.number,
                    delivered = true
                )
                _alertHistory.value = listOf(event) + _alertHistory.value
            }
        }

        if (sent) {
            lastAlertTimestamp = now
            Log.d(TAG, "Alert sent: ${trigger.name} to ${contacts.size} contacts")
        }

        return sent
    }

    /**
     * Legacy method — kept for backwards compatibility with existing callers.
     */
    suspend fun sendFamilyAlert(
        appContext: Context,
        userName: String,
        alertType: String,
        reason: String
    ) {
        val contacts = getContacts()
        if (contacts.isEmpty()) return

        val message = "Safe Companion Alert: ${userName.ifBlank { "Your family member" }} received a " +
            "suspicious $alertType that appears to be a scam. They have been warned. " +
            "Reason: $reason"

        contacts.forEach { contact ->
            sendSms(contact.number, message)
        }
    }

    private suspend fun getContacts(): List<FamilyContact> {
        val contactsJson = userPreferences.familyContactsJson.first()
        val familyType = object : TypeToken<List<FamilyContact>>() {}.type
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(contactsJson, familyType) as? List<FamilyContact>) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun sendSms(number: String, message: String): Boolean {
        return runCatching {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            if (message.length <= 160) {
                smsManager?.sendTextMessage(number, null, message, null, null)
            } else {
                val parts = smsManager?.divideMessage(message)
                if (parts != null) {
                    smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                }
            }
            Log.d(TAG, "SMS sent to $number")
            true
        }.getOrElse {
            Log.e(TAG, "Failed to send SMS to $number: ${it.message}")
            false
        }
    }
}
