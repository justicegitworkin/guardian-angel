package com.safeharborsecurity.app.ml

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Part A2: On-device scam classifier. Rule-based + heuristic scoring — no
 * ONNX model file required. Handles the 80% of scam-detection cases that are
 * obvious pattern matches; flags ambiguous cases for cloud (Claude) verification.
 */
data class ScamClassification(
    val verdict: String,                  // "DANGEROUS" | "SUSPICIOUS" | "SAFE" | "UNKNOWN"
    val confidence: Float,                // 0.0 .. 1.0
    val reasons: List<String>,
    val needsCloudVerification: Boolean
)

@Singleton
class OnDeviceScamClassifier @Inject constructor() {

    private val urgencyPhrases = listOf(
        "act now", "immediate action", "urgent", "expires today",
        "last chance", "limited time", "don't delay", "right away",
        "within 24 hours", "within 48 hours", "account suspended",
        "account will be closed", "verify immediately", "confirm now",
        "failure to respond", "your account has been compromised"
    )

    private val financialScamPhrases = listOf(
        "wire transfer", "gift card", "itunes card", "google play card",
        "send money", "western union", "moneygram", "bitcoin",
        "cryptocurrency", "investment opportunity", "guaranteed return",
        "double your money", "nigerian prince", "lottery winner",
        "you've won", "claim your prize", "inheritance",
        "irs", "tax refund", "social security", "medicare",
        "bank account", "routing number", "account number",
        "credit card number", "ssn", "social security number"
    )

    private val impersonationPhrases = listOf(
        "this is the irs", "this is the fbi", "microsoft support",
        "apple support", "amazon security", "bank of america fraud",
        "wells fargo alert", "chase security", "paypal security",
        "geek squad", "norton security", "mcafee alert",
        "your computer has a virus", "we detected suspicious activity",
        "your package could not be delivered", "usps", "fedex delivery",
        "customs and border"
    )

    private val phishingUrlPatterns = listOf(
        Regex("""https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""),
        Regex("""https?://[^/]*\.tk(/|$)"""),
        Regex("""https?://[^/]*\.ml(/|$)"""),
        Regex("""https?://[^/]*\.ga(/|$)"""),
        Regex("""https?://[^/]*\.cf(/|$)"""),
        Regex("""https?://bit\.ly/"""),
        Regex("""https?://tinyurl\.com/"""),
        Regex("""https?://t\.co/"""),
        Regex("""https?://goo\.gl/"""),
        Regex("""https?://[^/]*-login[^/]*\."""),
        Regex("""https?://[^/]*verify[^/]*-account"""),
        Regex("""https?://[^/]*secure[^/]*-update"""),
        Regex("""http://""")
    )

    private val safePatterns = listOf(
        "your order has shipped", "delivery confirmed",
        "appointment reminder", "your prescription is ready",
        "weather alert", "school closing", "meeting reminder"
    )

    private val scamNumberPatterns = listOf(
        // Premium-rate North American numbers. NANP numbers are always
        // 1 + 3-digit area code + 7-digit subscriber, so require \d{7,}
        // after the area code. Without that, "12345" parses as
        // "1 + 234 (Bahamas) + 5" and false-positives.
        Regex("""^\+?1?900\d{7,}"""),
        Regex("""^\+?1?976\d{7,}"""),
        // Caribbean / scam-prone NANP area codes. International callers see
        // these as +1-876-555-1234 (Jamaica) etc. — the +1 must be optional
        // because some carriers strip it. The \d{7,} suffix ensures we only
        // match real 11-digit NANP numbers, not short codes.
        Regex("""^\+?1?(232|234|242|246|284|340|441|473|649|664|721|758|767|784|809|829|849|868|869|876)\d{7,}""")
    )

    fun classifyText(text: String, sender: String? = null): ScamClassification {
        val lowerText = text.lowercase()
        val reasons = mutableListOf<String>()
        var dangerScore = 0f
        var safeScore = 0f

        for (pattern in safePatterns) if (lowerText.contains(pattern)) safeScore += 0.3f

        val urgencyHits = urgencyPhrases.count { lowerText.contains(it) }
        if (urgencyHits >= 2) {
            dangerScore += 0.3f
            reasons.add("Uses urgent pressure tactics ($urgencyHits phrases)")
        } else if (urgencyHits == 1) dangerScore += 0.1f

        val financialHits = financialScamPhrases.count { lowerText.contains(it) }
        if (financialHits >= 2) {
            dangerScore += 0.4f
            reasons.add("Contains financial scam language ($financialHits matches)")
        } else if (financialHits == 1) {
            dangerScore += 0.15f
            reasons.add("Mentions financial terms that scammers commonly use")
        }

        val impersonationHits = impersonationPhrases.count { lowerText.contains(it) }
        if (impersonationHits >= 1) {
            dangerScore += 0.35f
            reasons.add("Appears to impersonate a known organization")
        }

        val urls = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""").findAll(text)
        for (url in urls) {
            for (pattern in phishingUrlPatterns) {
                if (pattern.containsMatchIn(url.value)) {
                    dangerScore += 0.25f
                    reasons.add("Contains suspicious URL: ${url.value.take(50)}")
                    break
                }
            }
        }

        if (sender != null) {
            for (pattern in scamNumberPatterns) {
                if (pattern.containsMatchIn(sender.replace("[^+\\d]".toRegex(), ""))) {
                    dangerScore += 0.3f
                    reasons.add("Sender number matches known scam patterns")
                    break
                }
            }
        }

        val grammarIssues = listOf(
            "kindly", "do the needful", "dear customer", "dear user",
            "dear friend", "valued customer", "esteemed", "revert back"
        )
        val grammarHits = grammarIssues.count { lowerText.contains(it) }
        if (grammarHits >= 2) {
            dangerScore += 0.15f
            reasons.add("Language patterns common in scam messages")
        }

        val capsRatio = text.count { it.isUpperCase() }.toFloat() / text.length.coerceAtLeast(1)
        if (capsRatio > 0.4f && text.length > 20) {
            dangerScore += 0.1f
            reasons.add("Excessive use of capital letters (urgency tactic)")
        }

        val verdict: String
        val confidence: Float
        val needsCloud: Boolean
        when {
            dangerScore >= 0.6f -> {
                verdict = "DANGEROUS"
                confidence = dangerScore.coerceAtMost(0.95f)
                needsCloud = false
            }
            dangerScore >= 0.35f -> {
                verdict = "SUSPICIOUS"
                confidence = dangerScore
                needsCloud = dangerScore < 0.5f
            }
            safeScore >= 0.3f && dangerScore < 0.15f -> {
                verdict = "SAFE"
                confidence = (0.7f + safeScore).coerceAtMost(0.9f)
                needsCloud = false
            }
            dangerScore < 0.1f -> {
                verdict = "SAFE"
                confidence = 0.6f
                needsCloud = false
            }
            else -> {
                verdict = "UNKNOWN"
                confidence = 0.3f
                needsCloud = true
            }
        }

        if (reasons.isEmpty() && verdict == "SAFE") reasons.add("No scam indicators detected")
        return ScamClassification(verdict, confidence, reasons, needsCloud)
    }

    fun classifyUrl(url: String): ScamClassification {
        val reasons = mutableListOf<String>()
        var dangerScore = 0f
        for (pattern in phishingUrlPatterns) {
            if (pattern.containsMatchIn(url)) {
                dangerScore += 0.3f
                reasons.add("URL matches known phishing pattern")
            }
        }
        if (url.startsWith("http://") && !url.startsWith("http://localhost")) {
            dangerScore += 0.2f
            reasons.add("Uses unencrypted HTTP connection")
        }
        if (url.length > 200) {
            dangerScore += 0.1f
            reasons.add("Unusually long URL (common in phishing)")
        }
        if (url.any { it.code > 127 }) {
            dangerScore += 0.3f
            reasons.add("URL contains non-standard characters (possible homograph attack)")
        }
        val verdict = when {
            dangerScore >= 0.5f -> "DANGEROUS"
            dangerScore >= 0.2f -> "SUSPICIOUS"
            else -> "SAFE"
        }
        return ScamClassification(
            verdict, dangerScore.coerceIn(0.3f, 0.9f),
            reasons.ifEmpty { listOf("No known threats detected in URL") },
            verdict == "SUSPICIOUS"
        )
    }

    fun classifyPhoneNumber(number: String): ScamClassification {
        val cleaned = number.replace("[^+\\d]".toRegex(), "")
        val reasons = mutableListOf<String>()
        var dangerScore = 0f
        for (pattern in scamNumberPatterns) {
            if (pattern.containsMatchIn(cleaned)) {
                dangerScore += 0.5f
                reasons.add("Number matches known scam/premium-rate pattern")
            }
        }
        if (cleaned.length in 1..5) {
            dangerScore += 0.2f
            reasons.add("Very short number (possibly spoofed)")
        }
        val verdict = when {
            dangerScore >= 0.5f -> "DANGEROUS"
            dangerScore >= 0.2f -> "SUSPICIOUS"
            else -> "UNKNOWN"
        }
        return ScamClassification(
            verdict, dangerScore.coerceIn(0.2f, 0.85f),
            reasons.ifEmpty { listOf("Number not in known scam databases") },
            true
        )
    }
}
