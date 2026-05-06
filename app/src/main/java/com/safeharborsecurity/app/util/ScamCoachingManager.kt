package com.safeharborsecurity.app.util

import javax.inject.Inject
import javax.inject.Singleton

data class CoachingTip(
    val title: String,
    val tips: List<String>,
    val shareText: String
)

@Singleton
class ScamCoachingManager @Inject constructor() {

    fun getCoachingForScamType(scamType: String): CoachingTip {
        return when (scamType.uppercase()) {
            "PHONE_SCAM", "CALL" -> CoachingTip(
                title = "Good job! You avoided a phone scam.",
                tips = listOf(
                    "Real banks never ask for your PIN or password over the phone.",
                    "If someone says your account is frozen, hang up and call the number on your bank card.",
                    "Government agencies like the IRS will never call you demanding immediate payment."
                ),
                shareText = "Scam safety tip: If someone calls saying your bank account is frozen, hang up and call your bank directly using the number on your card. Never give out your PIN over the phone."
            )
            "SMS_PHISHING", "SMS", "TEXT" -> CoachingTip(
                title = "Good job! You spotted a text scam.",
                tips = listOf(
                    "Scammers send fake delivery notices and bank alerts to trick you.",
                    "Never tap links in unexpected text messages, even if they look official.",
                    "Real companies won't ask for passwords or payments via text message."
                ),
                shareText = "Scam safety tip: Never tap links in unexpected text messages. Scammers send fake delivery notices and bank alerts. If unsure, contact the company directly."
            )
            "EMAIL_PHISHING", "EMAIL" -> CoachingTip(
                title = "Good job! You caught a phishing email.",
                tips = listOf(
                    "Check the sender's email address carefully \u2014 scammers use addresses that look similar but aren't right.",
                    "Hover over links before clicking \u2014 the real URL is often different from what's shown.",
                    "Legitimate companies won't ask you to verify your account via email."
                ),
                shareText = "Scam safety tip: Always check the sender's email address carefully. Scammers use addresses that look similar to real ones. When in doubt, go directly to the company's website."
            )
            "GIFT_CARD" -> CoachingTip(
                title = "Good catch! Gift card scams are very common.",
                tips = listOf(
                    "No real company or government agency accepts gift cards as payment.",
                    "If someone asks you to buy gift cards and read them the codes, it's always a scam.",
                    "Gift card codes can be used instantly by scammers \u2014 the money is gone immediately."
                ),
                shareText = "Scam safety tip: No real company or government agency accepts gift cards as payment. If someone asks you to buy gift cards and share the codes, it's always a scam."
            )
            "APP" -> CoachingTip(
                title = "Good job checking that app!",
                tips = listOf(
                    "Only install apps from the Google Play Store. If you don't recognise an app, check it with Safe Companion before signing in.",
                    "Be wary of apps that ask for lots of permissions, especially if they want to read your messages or make calls.",
                    "If an app appeared on your phone and you didn't install it, it could be harmful."
                ),
                shareText = "Scam safety tip: Only install apps from the Google Play Store. If you find an unfamiliar app, use Safe Companion to check if it's safe before signing in."
            )
            else -> CoachingTip(
                title = "Good job! You stayed safe.",
                tips = listOf(
                    "Trust your instincts \u2014 if something feels wrong, it probably is.",
                    "Never rush into giving money or personal information.",
                    "When in doubt, talk to someone you trust before taking action."
                ),
                shareText = "Scam safety tip: Trust your instincts. If something feels wrong, it probably is. Never rush into giving money or personal information."
            )
        }
    }
}
