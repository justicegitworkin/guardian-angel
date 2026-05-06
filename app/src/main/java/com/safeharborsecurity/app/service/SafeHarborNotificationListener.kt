package com.safeharborsecurity.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.data.local.entity.CameraAlertEntity
import com.safeharborsecurity.app.data.repository.AlertRepository
import com.safeharborsecurity.app.data.repository.CameraAlertRepository
import com.safeharborsecurity.app.data.repository.HybridAnalysisRepository
import com.safeharborsecurity.app.notification.NotificationHelper
import com.safeharborsecurity.app.util.FamilyAlertManager
import com.safeharborsecurity.app.util.GiftCardDetector
import com.safeharborsecurity.app.util.NumberLookupUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class SafeHarborNotificationListener : NotificationListenerService() {

    @Inject lateinit var alertRepository: AlertRepository
    @Inject lateinit var hybridAnalysisRepository: HybridAnalysisRepository
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var familyAlertManager: FamilyAlertManager
    @Inject lateinit var cameraAlertRepository: CameraAlertRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListenerService connected")
        scope.launch { userPreferences.setLastScanTimestamp(System.currentTimeMillis()) }
    }

    companion object {
        private const val TAG = "SHNotifListener"

        // Known SMS/messaging app packages
        private val SMS_APP_PACKAGES = setOf(
            "com.google.android.apps.messaging",   // Google Messages
            "com.samsung.android.messaging",        // Samsung Messages
            "com.android.mms",                      // AOSP MMS
            "com.oneplus.mms",                      // OnePlus
            "com.sonyericsson.conversations",       // Sony
            "com.motorola.messaging",               // Motorola
            "com.huawei.message",                    // Huawei
            "com.lge.message",                       // LG
            "com.asus.message",                      // Asus
            "com.verizon.messaging.vzmsgs",           // Verizon Messages
            "com.att.android.attsms",                 // AT&T Messages
            "com.tmobile.pr.adapt",                   // T-Mobile
            "org.thoughtcrime.securesms",            // Signal
            "com.whatsapp",                          // WhatsApp
            "com.facebook.orca",                     // Messenger
            "org.telegram.messenger",                // Telegram
        )

        // Known social media app packages
        private val SOCIAL_APP_PACKAGES = setOf(
            "com.facebook.katana", "com.facebook.lite",      // Facebook
            "com.facebook.orca", "com.facebook.mlite",        // Messenger
            "com.whatsapp", "com.whatsapp.w4b",               // WhatsApp
            "com.instagram.android",                           // Instagram
            "com.twitter.android", "com.twitter.android.lite", // X/Twitter
            "com.nextdoor",                                    // NextDoor
            "com.linkedin.android",                            // LinkedIn
            "com.pinterest",                                   // Pinterest
            "com.reddit.frontpage",                            // Reddit
            "org.telegram.messenger",                          // Telegram
            "com.snapchat.android",                            // Snapchat
            "com.discord",                                     // Discord
            "com.tinder"                                       // Tinder (romance scams)
        )

        // Platform display names for social apps
        private val SOCIAL_APP_NAMES = mapOf(
            "com.facebook.katana" to "Facebook",
            "com.facebook.lite" to "Facebook Lite",
            "com.facebook.orca" to "Messenger",
            "com.facebook.mlite" to "Messenger Lite",
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.instagram.android" to "Instagram",
            "com.twitter.android" to "X (Twitter)",
            "com.twitter.android.lite" to "X Lite",
            "com.nextdoor" to "NextDoor",
            "com.linkedin.android" to "LinkedIn",
            "com.pinterest" to "Pinterest",
            "com.reddit.frontpage" to "Reddit",
            "org.telegram.messenger" to "Telegram",
            "com.snapchat.android" to "Snapchat",
            "com.discord" to "Discord",
            "com.tinder" to "Tinder"
        )

        // Known email app packages
        private val EMAIL_APP_PACKAGES = setOf(
            "com.google.android.gm",               // Gmail
            "com.microsoft.office.outlook",         // Outlook
            "com.samsung.android.email.provider",   // Samsung Email
            "com.yahoo.mobile.client.android.mail", // Yahoo Mail
            "me.bluemail.mail",                     // BlueMail
            "com.easilydo.mail",                    // Edison Mail
            "org.mozilla.thunderbird",              // Thunderbird
            "com.android.email",                    // AOSP Email
        )

        // Cooldown to avoid processing the same message twice
        private val recentMessages = LinkedHashMap<String, Long>(50, 0.75f, true)
        private const val COOLDOWN_MS = 60_000L // 1 minute

        // Camera/security app packages to consolidate (Part E1)
        private val CAMERA_APP_PACKAGES = mapOf(
            "com.arlo.app" to "Arlo",
            "com.netgear.android" to "Arlo",
            "com.mcu.reolink" to "Reolink",
            "com.ring.android.safe" to "Ring",
            "com.ringapp" to "Ring",
            "com.nest.android" to "Nest",
            "com.google.android.apps.chromecast.app" to "Google Home",
            "com.hikvision.hikconnect" to "Hikvision",
            "com.connect.hik" to "Hikvision",
            "com.hik.connect" to "Hikvision",
            "com.wyze" to "Wyze",
            "com.hualai" to "Wyze",
            "com.mm.android.smartlifeiot" to "Reolink",
            "com.dahua.technology.dmss" to "Dahua",
            "com.eufy.eufyHome" to "Eufy",
            "com.anker.eufygenie" to "Eufy",
            "com.eufylife.smarthome" to "Eufy",
            "com.swann.swannview" to "Swann",
            "com.lorextechnology.lorexhome" to "Lorex",
            "com.lorex.cirrus" to "Lorex",
            "com.blink.blinkapp" to "Blink",
            "com.amazon.blink" to "Blink"
        )

        private val ACTIONABLE_KEYWORDS = listOf(
            "motion", "person", "detected", "someone", "doorbell",
            "ring", "sound", "offline", "disconnected", "alert",
            "package", "spotted", "visitor"
        )

        private val NOISE_KEYWORDS = listOf(
            "battery low", "low battery", "firmware", "update available",
            "subscription", "promo", "deal", "upgrade your"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: return

        // Don't process our own notifications
        if (packageName == applicationContext.packageName) return

        val isSocialApp = packageName in SOCIAL_APP_PACKAGES
        val isMessagingApp = !isSocialApp && (packageName in SMS_APP_PACKAGES ||
            sbn.notification?.category == Notification.CATEGORY_MESSAGE)
        val isEmailApp = packageName in EMAIL_APP_PACKAGES ||
            sbn.notification?.category == Notification.CATEGORY_EMAIL
        val isCameraApp = packageName in CAMERA_APP_PACKAGES

        if (!isMessagingApp && !isEmailApp && !isSocialApp && !isCameraApp) return

        val extras = sbn.notification?.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val content = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        if (content.isBlank() || sender.isBlank()) return

        // Dedup check
        val messageKey = "$sender:${content.hashCode()}"
        val now = System.currentTimeMillis()
        synchronized(recentMessages) {
            val lastSeen = recentMessages[messageKey]
            if (lastSeen != null && now - lastSeen < COOLDOWN_MS) return
            recentMessages[messageKey] = now
            // Prune old entries
            recentMessages.entries.removeAll { now - it.value > COOLDOWN_MS * 5 }
        }

        scope.launch {
            try {
                userPreferences.setLastScanTimestamp(System.currentTimeMillis())
                when {
                    isCameraApp -> handleCameraAlert(packageName, sender, content)
                    isSocialApp -> analyzeSocialMessage(packageName, sender, content)
                    isEmailApp -> analyzeEmail(sender, content)
                    else -> analyzeMessage(sender, content)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing notification", e)
            }
        }
    }

    /**
     * Part E1 + E4: Capture and consolidate camera notifications.
     * Filter out marketing/firmware/battery noise and tag actionable alerts.
     */
    private suspend fun handleCameraAlert(packageName: String, title: String, message: String) {
        val source = CAMERA_APP_PACKAGES[packageName] ?: return
        val haystack = ("$title $message").lowercase()

        // Drop marketing / housekeeping noise
        if (NOISE_KEYWORDS.any { haystack.contains(it) }) {
            Log.d(TAG, "Camera notification skipped (noise): $title")
            return
        }

        val isActionable = ACTIONABLE_KEYWORDS.any { haystack.contains(it) }
        // Only persist actionable alerts — keeps the hub clean
        if (!isActionable) {
            Log.d(TAG, "Camera notification skipped (not actionable): $title")
            return
        }

        val category = when {
            haystack.contains("doorbell") || haystack.contains("ring") -> "DOORBELL"
            haystack.contains("person") || haystack.contains("someone") || haystack.contains("visitor") -> "PERSON"
            haystack.contains("motion") -> "MOTION"
            haystack.contains("sound") -> "SOUND"
            haystack.contains("offline") || haystack.contains("disconnected") -> "OFFLINE"
            else -> "OTHER"
        }

        val entity = CameraAlertEntity(
            source = source,
            sourcePackage = packageName,
            title = title,
            message = message,
            category = category,
            isActionable = true
        )
        cameraAlertRepository.add(entity)
        Log.d(TAG, "Camera alert recorded from $source ($category): $title")

        // Prune anything older than 30 days
        cameraAlertRepository.pruneOlderThan(System.currentTimeMillis() - 30L * 24L * 3600L * 1000L)
    }

    private suspend fun analyzeMessage(sender: String, body: String) {
        val smsShieldOn = userPreferences.isSmsShieldEnabled.first()
        if (!smsShieldOn) return

        // Check trusted numbers
        val trustedJson = userPreferences.trustedNumbersJson.first()
        try {
            val trusted = com.google.gson.Gson().fromJson(trustedJson, Array<String>::class.java)
            if (trusted?.any { sender.contains(it) } == true) return
        } catch (_: Exception) { }

        if (alertRepository.isBlocked(sender)) return

        // Skip known contacts
        val safety = NumberLookupUtil.lookupNumber(applicationContext, sender)
        if (safety == NumberLookupUtil.NumberSafety.KNOWN_SAFE) return

        // Gift card alarm — immediate, deterministic check (does not need network)
        val giftCardResult = GiftCardDetector.analyze(body)
        if (giftCardResult.isDetected) {
            val alert = AlertEntity(
                type = "SMS",
                sender = sender,
                content = body,
                riskLevel = "SCAM",
                confidence = 0.95f,
                reason = "GIFT CARD SCAM: This message asks you to buy gift cards (${giftCardResult.matchedKeywords.joinToString()}). This is almost always a scam.",
                action = "Do NOT buy any gift cards. Delete this message immediately."
            )
            notificationHelper.showGiftCardAlert(alert)
            val userName = userPreferences.userName.first()
            familyAlertManager.sendFamilyAlert(applicationContext, userName, "gift card scam text", alert.reason)
            return
        }

        // Part A4: Hybrid analysis — local SLM first, Claude only if ambiguous + key present.
        val analysis = hybridAnalysisRepository.analyzeSms(sender, body)
        if (analysis.verdict == "WARNING" || analysis.verdict == "SUSPICIOUS" || analysis.verdict == "SCAM" || analysis.verdict == "DANGEROUS") {
            val alert = analysis.cloudAlertEntity ?: AlertEntity(
                type = "SMS",
                sender = sender,
                content = body,
                riskLevel = if (analysis.verdict == "DANGEROUS") "SCAM" else "WARNING",
                confidence = analysis.confidence,
                reason = analysis.reasons.joinToString("; "),
                action = "Be careful. Do not respond, click links, or share information."
            ).let { it.copy(id = alertRepository.insertLocal(it)) }
            notificationHelper.showSmsAlert(alert)
            if (alert.riskLevel == "SCAM") {
                val userName = userPreferences.userName.first()
                familyAlertManager.sendFamilyAlert(applicationContext, userName, "text", alert.reason)
            }
        }
    }

    private suspend fun analyzeSocialMessage(packageName: String, sender: String, content: String) {
        val smsShieldOn = userPreferences.isSmsShieldEnabled.first()
        if (!smsShieldOn) return

        // Only process messages with meaningful content (skip short status updates)
        val fullText = "$sender $content"
        if (fullText.length <= 20) return

        val apiKey = userPreferences.apiKey.first()
        if (apiKey.isBlank()) return

        val platformName = SOCIAL_APP_NAMES[packageName] ?: "Social Media"

        alertRepository.analyzeSocial(apiKey, platformName, fullText).onSuccess { alert ->
            // Only alert on HIGH confidence DANGEROUS verdicts for social media
            if (alert.riskLevel == "SCAM" && alert.confidence >= 0.7f) {
                notificationHelper.showSmsAlert(alert)
                val userName = userPreferences.userName.first()
                familyAlertManager.sendFamilyAlert(
                    applicationContext, userName, "$platformName message", alert.reason
                )
            } else if (alert.riskLevel == "WARNING" && alert.confidence >= 0.8f) {
                notificationHelper.showSmsAlert(alert)
            }
        }
    }

    private suspend fun analyzeEmail(sender: String, body: String) {
        val smsShieldOn = userPreferences.isSmsShieldEnabled.first()
        if (!smsShieldOn) return

        val apiKey = userPreferences.apiKey.first()
        if (apiKey.isBlank()) return

        // Extract subject from body if possible (email notifications often include it)
        val subject = if (body.contains("\n")) body.substringBefore("\n").trim() else ""
        val emailBody = if (body.contains("\n")) body.substringAfter("\n").trim() else body

        alertRepository.analyzeEmail(apiKey, sender, subject, emailBody).onSuccess { alert ->
            if (alert.riskLevel == "WARNING" || alert.riskLevel == "SCAM") {
                notificationHelper.showEmailAlert(alert)

                if (alert.riskLevel == "SCAM") {
                    val userName = userPreferences.userName.first()
                    familyAlertManager.sendFamilyAlert(applicationContext, userName, "email", alert.reason)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
