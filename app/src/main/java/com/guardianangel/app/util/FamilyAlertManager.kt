package com.guardianangel.app.util

import android.content.Context
import android.telephony.SmsManager
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.guardianangel.app.data.datastore.UserPreferences
import com.guardianangel.app.data.remote.model.FamilyContact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val gson: Gson
) {
    suspend fun sendFamilyAlert(
        appContext: Context,
        userName: String,
        alertType: String,   // "call" | "text"
        reason: String
    ) {
        val contactsJson = userPreferences.familyContactsJson.first()
        val familyType = object : TypeToken<List<FamilyContact>>() {}.type
        val contacts: List<FamilyContact> = runCatching {
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(contactsJson, familyType) as? List<FamilyContact>) ?: emptyList()
        }.getOrDefault(emptyList())

        if (contacts.isEmpty()) return

        val message = "Guardian Angel Alert: ${userName.ifBlank { "Your family member" }} received a " +
            "suspicious $alertType that appears to be a scam. They have been warned. " +
            "Reason: $reason"

        contacts.forEach { contact ->
            sendSms(contact.number, message)
        }
    }

    private fun sendSms(number: String, message: String) {
        runCatching {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager?.sendTextMessage(number, null, message, null, null)
        }
    }
}
