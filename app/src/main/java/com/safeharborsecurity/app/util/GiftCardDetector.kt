package com.safeharborsecurity.app.util

object GiftCardDetector {

    private val GIFT_CARD_KEYWORDS = listOf(
        "gift card", "giftcard", "itunes card", "google play card",
        "steam card", "amazon card", "prepaid card", "ebay card",
        "visa gift", "mastercard gift", "target card", "walmart card"
    )

    private val URGENCY_KEYWORDS = listOf(
        "urgent", "immediately", "right now", "asap", "hurry",
        "don't delay", "act fast", "last chance", "limited time",
        "expire", "suspended", "arrested", "warrant", "irs",
        "owe", "penalty", "fee", "fine", "payment required",
        "must pay", "send money", "wire", "transfer"
    )

    data class GiftCardDetection(
        val isDetected: Boolean,
        val matchedKeywords: List<String>,
        val hasUrgency: Boolean
    )

    fun analyze(text: String): GiftCardDetection {
        val lowerText = text.lowercase()
        val matchedGiftCards = GIFT_CARD_KEYWORDS.filter { lowerText.contains(it) }
        val matchedUrgency = URGENCY_KEYWORDS.filter { lowerText.contains(it) }

        // Only flag if gift card keywords found WITH urgency/payment context
        val isScamPattern = matchedGiftCards.isNotEmpty() && matchedUrgency.isNotEmpty()

        return GiftCardDetection(
            isDetected = isScamPattern,
            matchedKeywords = matchedGiftCards,
            hasUrgency = matchedUrgency.isNotEmpty()
        )
    }
}
