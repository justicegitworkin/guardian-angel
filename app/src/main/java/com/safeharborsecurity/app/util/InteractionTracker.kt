package com.safeharborsecurity.app.util

import javax.inject.Inject
import javax.inject.Singleton

enum class FamilyWatchTrigger(val level: String, val description: String) {
    LONG_SCAM_CALL("CRITICAL", "has been on a phone call flagged as a possible scam for over 1 minute"),
    VERY_LONG_SCAM_CALL("CRITICAL", "has been on a phone call flagged as a possible scam for over 5 minutes"),
    SCAM_LINK_TAPPED("HIGH", "tapped a link that was flagged as dangerous"),
    SCAM_SMS_REPLY("CRITICAL", "sent a reply to a number flagged as a scam"),
    GIFT_CARD_PURCHASE("CRITICAL", "may be purchasing gift cards after contact with a potential scammer"),
    WIRE_TRANSFER("CRITICAL", "may be making a wire transfer after contact with a potential scammer"),
    PAYMENT_APP_AFTER_SCAM("HIGH", "opened a payment app within 10 minutes of a scam flag"),
    REPEATED_SCAM_CONTACT("HIGH", "contacted a previously flagged scam number again"),
    PANIC_BUTTON("CRITICAL", "pressed the emergency help button"),
    DAILY_INACTIVITY("LOW", "has not used their phone in over 24 hours")
}

@Singleton
class InteractionTracker @Inject constructor() {
    private val flaggedNumbers = mutableSetOf<String>()
    private val flaggedUrls = mutableSetOf<String>()
    private var lastScamFlagTime: Long = 0

    fun flagNumber(number: String) {
        flaggedNumbers.add(number)
        lastScamFlagTime = System.currentTimeMillis()
    }

    fun flagUrl(url: String) {
        flaggedUrls.add(url)
        lastScamFlagTime = System.currentTimeMillis()
    }

    fun isNumberFlagged(number: String): Boolean = number in flaggedNumbers
    fun isUrlFlagged(url: String): Boolean = url in flaggedUrls

    fun isPaymentAppSuspicious(): Boolean {
        val tenMinutes = 10 * 60 * 1000L
        return System.currentTimeMillis() - lastScamFlagTime < tenMinutes
    }

    fun clearAll() {
        flaggedNumbers.clear()
        flaggedUrls.clear()
        lastScamFlagTime = 0
    }
}
