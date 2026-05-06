package com.safeharborsecurity.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.Telephony
import com.google.gson.Gson
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.repository.AlertRepository
import com.safeharborsecurity.app.notification.NotificationHelper
import com.safeharborsecurity.app.util.FamilyAlertManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var alertRepository: AlertRepository
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var familyAlertManager: FamilyAlertManager
    @Inject lateinit var gson: Gson

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }

        // Scope is local and self-cancelling: cancelled after pendingResult.finish()
        val pendingResult = goAsync()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)

        scope.launch {
            try {
                val smsShieldOn = userPreferences.isSmsShieldEnabled.first()
                if (!smsShieldOn) return@launch

                if (isInContacts(context, sender)) return@launch

                val trustedJson = userPreferences.trustedNumbersJson.first()
                val trusted = gson.fromJson(trustedJson, Array<String>::class.java)
                if (trusted?.contains(sender) == true) return@launch

                if (alertRepository.isBlocked(sender)) return@launch

                val apiKey = userPreferences.apiKey.first()
                if (apiKey.isBlank()) return@launch

                // Gift card alarm — immediate check before Claude analysis
                val giftCardResult = com.safeharborsecurity.app.util.GiftCardDetector.analyze(body)
                if (giftCardResult.isDetected) {
                    val giftCardAlert = com.safeharborsecurity.app.data.local.entity.AlertEntity(
                        type = "SMS",
                        sender = sender,
                        content = body,
                        riskLevel = "SCAM",
                        confidence = 0.95f,
                        reason = "GIFT CARD SCAM: This message asks you to buy gift cards (${giftCardResult.matchedKeywords.joinToString()}). This is almost always a scam. No real company or government agency asks for gift cards as payment.",
                        action = "Do NOT buy any gift cards. Delete this message immediately."
                    )
                    notificationHelper.showGiftCardAlert(giftCardAlert)
                    val userName = userPreferences.userName.first()
                    familyAlertManager.sendFamilyAlert(context, userName, "gift card scam text", giftCardAlert.reason)
                    return@launch
                }

                alertRepository.analyzeSms(apiKey, sender, body).onSuccess { alert ->
                    if (alert.riskLevel == "WARNING" || alert.riskLevel == "SCAM") {
                        notificationHelper.showSmsAlert(alert)

                        if (alert.riskLevel == "SCAM") {
                            val userName = userPreferences.userName.first()
                            familyAlertManager.sendFamilyAlert(context, userName, "text", alert.reason)
                        }
                    }
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
