package com.safeharborsecurity.app.ml

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Item 5 Stage 2: offline chat responder.
 *
 * When the user has no Anthropic API key, the chat agent (Grace / James /
 * Sophie / George) needs to keep being useful — not refuse to talk. This
 * class does intent matching against a small library of common scam-related
 * questions and returns a calm, scripted answer with the persona's tone.
 *
 * It's intentionally not an LLM — the whole point of Item 5 was "the app
 * works fully on-device." This is decision-tree-style scam coaching, the
 * same heuristic-classifier approach we use for SMS detection.
 *
 * The matcher is keyword-based with multiple-keyword scoring per intent.
 * If no intent matches well, we return a generic "I can't reach my smart
 * brain right now, but I can still help with safety checks" fallback.
 */
@Singleton
class LocalChatResponder @Inject constructor() {

    data class LocalResponse(
        val text: String,
        val matchedIntent: String?,
        val confidence: Float
    )

    /**
     * Return the best canned response for the given user utterance and
     * persona. Persona only affects the *opening phrase* (Grace says "Dear",
     * George says "Take your time"); the substance is identical so the
     * coaching is consistent.
     */
    fun respond(userText: String, personaId: String): LocalResponse {
        val lower = userText.lowercase().trim()
        if (lower.isBlank()) {
            return LocalResponse(genericFallback(personaId), null, 0f)
        }

        val matches = intents.map { intent ->
            val score = intent.keywords.count { lower.contains(it) }
            intent to score
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }

        if (matches.isEmpty()) {
            return LocalResponse(genericFallback(personaId), null, 0f)
        }
        val (best, score) = matches.first()
        val confidence = (score.toFloat() / best.keywords.size).coerceAtMost(1f)
        return LocalResponse(
            text = personaPrefix(personaId) + best.response,
            matchedIntent = best.id,
            confidence = confidence
        )
    }

    private fun personaPrefix(personaId: String): String = when (personaId) {
        "GRACE" -> "Hello dear. "
        "JAMES" -> "Good question. "
        "SOPHIE" -> "Great question — "
        "GEORGE" -> "Take your time. "
        else -> ""
    }

    private fun genericFallback(personaId: String): String =
        personaPrefix(personaId) +
            "I'm running on this phone alone right now without my smart brain, " +
            "so I can't have a full conversation. I can still help you check " +
            "if a message, link, or photo is safe — just tap the 'Is This Safe?' " +
            "button at the bottom of the screen. To talk with me normally, " +
            "you'd need to add a Claude key in Settings."

    private data class Intent(val id: String, val keywords: List<String>, val response: String)

    // The library is short, factual, and deliberately conservative. Every
    // response ends with a clear next-step, and never tells the user "this
    // IS a scam" without their info — only "this LOOKS like a scam pattern".
    private val intents = listOf(
        Intent(
            id = "irs_scam",
            keywords = listOf("irs", "tax", "social security", "ssn"),
            response = "If someone calls and says they're from the IRS or " +
                "Social Security and demands money or threatens arrest, that's " +
                "almost always a scam. The real IRS contacts you by mail first, " +
                "never asks for gift cards, and never threatens immediate arrest. " +
                "Hang up and don't call back. If you want, paste the message " +
                "into 'Is This Safe?' and I'll check it for you."
        ),
        Intent(
            id = "gift_card",
            keywords = listOf("gift card", "itunes", "google play card", "amazon card"),
            response = "Anyone asking you to pay with gift cards — for taxes, " +
                "fines, your computer, anything — is running a scam. There is " +
                "no real situation where a real person needs you to pay them in " +
                "gift cards. Don't buy them, don't read the numbers to anyone " +
                "on the phone. If a clerk at the store asks you why you're " +
                "buying so many, listen to them — they're trying to help."
        ),
        Intent(
            id = "bank_text",
            keywords = listOf("bank", "account suspended", "account locked", "verify account"),
            response = "Banks don't text you links and ask you to log in. If " +
                "your bank really needs you to do something, the safest move " +
                "is to ignore the message and call the number on the back of " +
                "your bank card. Don't click any link in the text. Want me to " +
                "check the message? Tap 'Is This Safe?' and paste it in."
        ),
        Intent(
            id = "grandparent_scam",
            keywords = listOf("grandson", "granddaughter", "grandkid", "emergency", "in jail", "lost my phone"),
            response = "This sounds like the 'grandparent scam' — someone " +
                "pretending to be a family member in trouble, asking for money " +
                "fast. Before you do anything, hang up and call the family " +
                "member on the number you already have for them. If you can't " +
                "reach them, call another relative who would know. Real " +
                "emergencies wait long enough to make one phone call."
        ),
        Intent(
            id = "tech_support",
            keywords = listOf("microsoft", "apple support", "computer virus", "tech support", "geek squad"),
            response = "Microsoft, Apple, and Geek Squad never call you out " +
                "of the blue about your computer. Anyone who does is a scammer " +
                "trying to get you to install software that lets them into your " +
                "device or your bank account. Hang up. Don't let them connect " +
                "to your computer. If your computer really has a problem, " +
                "take it to a local repair shop you trust."
        ),
        Intent(
            id = "romance",
            keywords = listOf("dating", "romance", "boyfriend", "girlfriend", "online", "love", "send money"),
            response = "If someone you've never met in person is asking you " +
                "for money — even just a small amount, even with a good story " +
                "— that's a romance scam. Real people who care about you don't " +
                "need money before you've ever met. Stop sending money, even " +
                "if they say it's just one more time. If you'd like to talk " +
                "to someone, the AARP Fraud Watch Helpline is 1-877-908-3360."
        ),
        Intent(
            id = "package",
            keywords = listOf("usps", "fedex", "ups", "package", "delivery", "couldn't deliver"),
            response = "Fake package-delivery texts are very common. Real " +
                "delivery services don't text you links to 'reschedule' — they " +
                "leave a sticker on your door. Don't click the link. If you " +
                "are expecting a package, go directly to the carrier's app " +
                "or website and check there. Want me to check the text? Tap " +
                "'Is This Safe?'."
        ),
        Intent(
            id = "lottery",
            keywords = listOf("won", "winner", "lottery", "prize", "sweepstakes", "claim"),
            response = "If you didn't enter a lottery or sweepstakes, you " +
                "didn't win one. And real lotteries never ask you to pay fees " +
                "to claim a prize — that's always a scam. Delete the message " +
                "and don't reply, even to say no. Replying tells the scammer " +
                "your number works."
        ),
        Intent(
            id = "what_is_safe_companion",
            keywords = listOf("what do you do", "what is this app", "what are you", "who are you", "how do you work"),
            response = "I'm Safe Companion. I watch the screen on your phone " +
                "for scam patterns in texts, emails, and phone calls, and warn " +
                "you when something looks suspicious. I work right on your " +
                "phone — I don't send your messages anywhere. You can also " +
                "tap 'Is This Safe?' anytime to ask me about a specific " +
                "message, link, or photo."
        ),
        Intent(
            id = "i_was_scammed",
            keywords = listOf("i was scammed", "they got me", "i sent money", "i fell for", "i clicked"),
            response = "I'm sorry that happened. Take a breath — you're not " +
                "alone, and there are steps to take right now. First, if you " +
                "sent money or shared bank info, call your bank's fraud line " +
                "(the number on the back of your card) immediately. Second, " +
                "change passwords on any accounts the scammer might know " +
                "about. Third, you can report the scam to the FTC at " +
                "reportfraud.ftc.gov. If you tap 'Is This Safe?' and pick " +
                "the right option, I can walk you through this step by step."
        ),
        Intent(
            id = "thanks_goodbye",
            keywords = listOf("thank you", "thanks", "got it", "goodbye", "bye", "appreciate"),
            response = "You're welcome. Stay safe out there. Tap me anytime " +
                "you have a question — I'll be here."
        )
    )
}
