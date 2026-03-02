package com.guardianangel.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.google.gson.Gson
import com.guardianangel.app.data.datastore.UserPreferences
import com.guardianangel.app.data.repository.AlertRepository
import com.guardianangel.app.notification.NotificationHelper
import com.guardianangel.app.util.FamilyAlertManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GuardianSmsReceiver"
    }

    @Inject lateinit var alertRepository: AlertRepository
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var familyAlertManager: FamilyAlertManager
    @Inject lateinit var gson: Gson

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive fired — action: ${intent.action}")
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.w(TAG, "No messages extracted from intent")
            return
        }

        val sender = messages[0].displayOriginatingAddress ?: run {
            Log.w(TAG, "No originating address found")
            return
        }
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        Log.d(TAG, "SMS from $sender (${body.length} chars)")

        // Scope is local and self-cancelling: cancelled after pendingResult.finish()
        val pendingResult = goAsync()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)

        scope.launch {
            try {
                val smsShieldOn = userPreferences.isSmsShieldEnabled.first()
                if (!smsShieldOn) {
                    Log.d(TAG, "SMS Shield is off — skipping")
                    return@launch
                }

                if (isInContacts(context, sender)) {
                    Log.d(TAG, "Sender $sender is in contacts — skipping")
                    return@launch
                }

                val trustedJson = userPreferences.trustedNumbersJson.first()
                val trusted = gson.fromJson(trustedJson, Array<String>::class.java)
                if (trusted?.contains(sender) == true) {
                    Log.d(TAG, "Sender $sender is trusted — skipping")
                    return@launch
                }

                if (alertRepository.isBlocked(sender)) {
                    Log.d(TAG, "Sender $sender is blocked — skipping")
                    return@launch
                }

                val apiKey = userPreferences.apiKey.first()
                if (apiKey.isBlank()) {
                    Log.w(TAG, "No API key configured — cannot analyse SMS")
                    return@launch
                }

                Log.d(TAG, "Sending SMS to Claude for analysis")
                alertRepository.analyzeSms(apiKey, sender, body).onSuccess { alert ->
                    Log.d(TAG, "Analysis result: ${alert.riskLevel} — ${alert.reason}")
                    if (alert.riskLevel == "WARNING" || alert.riskLevel == "SCAM") {
                        notificationHelper.showSmsAlert(alert)

                        if (alert.riskLevel == "SCAM") {
                            val userName = userPreferences.userName.first()
                            familyAlertManager.sendFamilyAlert(context, userName, "text", alert.reason)
                        }
                    }
                }.onFailure { e ->
                    Log.e(TAG, "SMS analysis failed: ${e.message}", e)
                }
            } finally {
                pendingResult.finish()
                job.cancel()
            }
        }
    }

    private fun isInContacts(context: Context, number: String): Boolean {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
