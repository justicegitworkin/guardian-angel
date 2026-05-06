package com.safeharborsecurity.app.util

import com.safeharborsecurity.app.data.local.entity.AlertEntity

/**
 * Part D3: First-run sample alerts so testers can see the UI without waiting
 * for real scam messages. Insert at most once — gated by a DataStore flag.
 *
 * Each alert is tagged in `reason` with a "[SAMPLE]" prefix so callers can
 * filter or remove them when real alerts arrive.
 */
object SampleDataSeeder {

    const val SAMPLE_PREFIX = "[SAMPLE] "

    fun sampleAlerts(): List<AlertEntity> {
        val now = System.currentTimeMillis()
        // [SAMPLE] is prefixed to BOTH sender and content so it shows up on
        // the home-screen recent-alerts row at a glance — testers told us it
        // wasn't obvious these were demo data when only the reason field had
        // the prefix.
        return listOf(
            AlertEntity(
                type = "SMS",
                sender = SAMPLE_PREFIX + "+1 800 555 0199",
                content = SAMPLE_PREFIX + "URGENT: Your bank account has been compromised! Click here immediately to verify: http://bank-secure-verify.tk/login",
                riskLevel = "SCAM",
                confidence = 0.92f,
                reason = SAMPLE_PREFIX + "Uses urgency tactics and a suspicious URL on a free TLD (.tk). Real banks never send links like this. (This is a built-in example, not a real message.)",
                action = "Do not click the link. Delete this message. If unsure, call your bank using the number on the back of your card.",
                timestamp = now - 3_600_000
            ),
            AlertEntity(
                type = "SMS",
                sender = SAMPLE_PREFIX + "+1 720 555 9876",
                content = SAMPLE_PREFIX + "Hi Grandma, I lost my phone and this is my new number. Can you send me $500 for an emergency? I'll pay you back.",
                riskLevel = "WARNING",
                confidence = 0.78f,
                reason = SAMPLE_PREFIX + "Common 'grandparent scam' pattern. Verify by calling the family member's known number. (This is a built-in example, not a real message.)",
                action = "Do not send money. Call your grandchild on their normal number first, or ask a question only they would know.",
                timestamp = now - 7_200_000
            ),
            AlertEntity(
                type = "SMS",
                sender = SAMPLE_PREFIX + "+1 214 555 0000",
                content = SAMPLE_PREFIX + "Your prescription is ready for pickup at CVS. Reply STOP to opt out.",
                riskLevel = "SAFE",
                confidence = 0.85f,
                reason = SAMPLE_PREFIX + "Looks like a normal pharmacy notification. No suspicious links or pressure language. (This is a built-in example, not a real message.)",
                action = "No action needed.",
                timestamp = now - 1_800_000
            )
        )
    }
}
