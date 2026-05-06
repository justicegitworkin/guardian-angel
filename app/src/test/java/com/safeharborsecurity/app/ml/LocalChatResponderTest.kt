package com.safeharborsecurity.app.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Tests for LocalChatResponder — the offline brain. When the user has no
 * Anthropic key (or no network), this is what answers their questions.
 * It must:
 *   - Match the right intent for common scam topics
 *   - Use the persona's opening phrase
 *   - Fall back gracefully when nothing matches
 *   - Never tell the user "this IS a scam" without their info
 */
class LocalChatResponderTest {

    private lateinit var responder: LocalChatResponder

    @Before
    fun setup() {
        responder = LocalChatResponder()
    }

    // ─── Intent matching ───────────────────────────────────────────

    @Test
    fun `IRS question matches irs_scam intent`() {
        val result = responder.respond(
            "Someone called saying they were from the IRS and I owe taxes",
            "GRACE"
        )
        assertThat(result.matchedIntent).isEqualTo("irs_scam")
    }

    @Test
    fun `gift card question matches gift_card intent`() {
        val result = responder.respond(
            "They told me to buy iTunes gift cards to pay them",
            "JAMES"
        )
        assertThat(result.matchedIntent).isEqualTo("gift_card")
    }

    @Test
    fun `bank text question matches bank_text intent`() {
        val result = responder.respond(
            "I got a text saying my bank account is suspended",
            "SOPHIE"
        )
        assertThat(result.matchedIntent).isEqualTo("bank_text")
    }

    @Test
    fun `grandparent emergency matches grandparent_scam intent`() {
        val result = responder.respond(
            "My grandson called saying he's in jail and needs money",
            "GEORGE"
        )
        assertThat(result.matchedIntent).isEqualTo("grandparent_scam")
    }

    @Test
    fun `tech support call matches tech_support intent`() {
        val result = responder.respond(
            "Microsoft called me about a virus on my computer",
            "GRACE"
        )
        assertThat(result.matchedIntent).isEqualTo("tech_support")
    }

    @Test
    fun `romance scam matches romance intent`() {
        val result = responder.respond(
            "I met someone online and they're asking me to send money",
            "JAMES"
        )
        assertThat(result.matchedIntent).isEqualTo("romance")
    }

    @Test
    fun `package scam text matches package intent`() {
        val result = responder.respond(
            "USPS texted me they couldn't deliver my package",
            "SOPHIE"
        )
        assertThat(result.matchedIntent).isEqualTo("package")
    }

    @Test
    fun `lottery winner matches lottery intent`() {
        val result = responder.respond(
            "I got an email saying I won the lottery and need to claim my prize",
            "GEORGE"
        )
        assertThat(result.matchedIntent).isEqualTo("lottery")
    }

    @Test
    fun `app explanation question matches what_is_safe_companion intent`() {
        val result = responder.respond("What do you do for me?", "GRACE")
        assertThat(result.matchedIntent).isEqualTo("what_is_safe_companion")
    }

    @Test
    fun `victim message matches i_was_scammed intent`() {
        val result = responder.respond("I was scammed and I sent money", "JAMES")
        assertThat(result.matchedIntent).isEqualTo("i_was_scammed")
    }

    @Test
    fun `thanks matches thanks_goodbye intent`() {
        val result = responder.respond("Thank you, goodbye", "SOPHIE")
        assertThat(result.matchedIntent).isEqualTo("thanks_goodbye")
    }

    // ─── Persona prefixes ───────────────────────────────────────────

    @Test
    fun `Grace uses Hello dear prefix`() {
        val result = responder.respond("What is the IRS scam", "GRACE")
        assertThat(result.text).startsWith("Hello dear.")
    }

    @Test
    fun `James uses Good question prefix`() {
        val result = responder.respond("What is the IRS scam", "JAMES")
        assertThat(result.text).startsWith("Good question.")
    }

    @Test
    fun `Sophie uses Great question prefix`() {
        val result = responder.respond("What is the IRS scam", "SOPHIE")
        assertThat(result.text).startsWith("Great question")
    }

    @Test
    fun `George uses Take your time prefix`() {
        val result = responder.respond("What is the IRS scam", "GEORGE")
        assertThat(result.text).startsWith("Take your time.")
    }

    // ─── Fallback behavior ──────────────────────────────────────────

    @Test
    fun `unknown question falls back gracefully`() {
        val result = responder.respond(
            "What is the meaning of life and the universe",
            "GRACE"
        )
        assertThat(result.matchedIntent).isNull()
        assertThat(result.text).contains("Is This Safe")
    }

    @Test
    fun `blank input falls back gracefully`() {
        val result = responder.respond("", "GRACE")
        assertThat(result.matchedIntent).isNull()
        assertThat(result.confidence).isEqualTo(0f)
    }

    @Test
    fun `only whitespace falls back gracefully`() {
        val result = responder.respond("   \n\t   ", "JAMES")
        assertThat(result.matchedIntent).isNull()
    }

    // ─── Safety guardrails ──────────────────────────────────────────

    @Test
    fun `responses never make definitive scam accusations`() {
        // Heuristic guard: the canned responses must not say "this IS a scam"
        // about the user's specific situation. They should say "this LOOKS
        // like a scam" or "almost always a scam".
        val intents = listOf(
            "IRS called me", "gift cards", "bank account suspended",
            "grandson in jail", "Microsoft virus", "lottery winner"
        )
        intents.forEach { input ->
            val r = responder.respond(input, "GRACE")
            // The string "this is a scam" without softening would be a red flag
            val problematic = Regex("\\bthis is a scam\\b", RegexOption.IGNORE_CASE)
            assertThat(problematic.containsMatchIn(r.text)).isFalse()
        }
    }

    @Test
    fun `confidence is bounded`() {
        val r = responder.respond("IRS gift card grandson lottery", "GRACE")
        assertThat(r.confidence).isAtMost(1f)
        assertThat(r.confidence).isAtLeast(0f)
    }
}
