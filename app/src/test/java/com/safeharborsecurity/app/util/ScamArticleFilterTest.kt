package com.safeharborsecurity.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for ScamArticleFilter — the gate between RSS feeds and the user's
 * news view. Two-tier filter (STRONG keywords pass alone; WEAK keywords
 * need CONTEXT). Robolectric is required because the production code
 * calls android.util.Log, which is no-op'd by Robolectric on the JVM.
 *
 * Bug history these tests guard against:
 *  - News feed pulled only one article (filter too tight) → check that
 *    legitimate scam-news headlines from FTC/FBI/AARP/Krebs all pass.
 *  - "Trump posts fake AI image" appeared in feed (political leakage)
 *    → check that political content is rejected.
 *  - Procedural press releases ("FTC announces fraud crackdown") were
 *    being rejected by overly broad regulatory exclude → check those
 *    pass while rule-making procedural noise still gets rejected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])  // Stable Robolectric SDK to avoid CI network fetches.
class ScamArticleFilterTest {

    // ─── STRONG keyword passes (Tier 1) ─────────────────────────────

    @Test
    fun `obvious scam article passes`() {
        val passes = ScamArticleFilter.isScamRelevant(
            "New phishing scam targets elderly Medicare beneficiaries",
            "Scammers are calling seniors pretending to be from Medicare..."
        )
        assertThat(passes).isTrue()
    }

    @Test
    fun `grandparent scam article passes`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "Grandparent scam: Fake calls cost seniors thousands",
            "An elderly woman lost \$5,000..."
        )).isTrue()
    }

    @Test
    fun `tech support scam article passes`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "Tech support scam impersonates Microsoft",
            "Beware of pop-ups claiming your computer has a virus."
        )).isTrue()
    }

    @Test
    fun `gift card scam article passes`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "Gift card scam costs woman \$2,000",
            "She was told to buy iTunes cards to pay her tax bill."
        )).isTrue()
    }

    @Test
    fun `Krebs phishing campaign article passes`() {
        // Without the STRONG_KEYWORDS widening, this used to be rejected
        // because "phishing campaign" wasn't a strong keyword.
        assertThat(ScamArticleFilter.isScamRelevant(
            "New phishing campaign hits Office 365 users",
            "Attackers are using lookalike domains to harvest credentials."
        )).isTrue()
    }

    @Test
    fun `Venmo scam article passes`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "Venmo scam targets sellers on Facebook Marketplace",
            "Scammers send fake confirmation emails."
        )).isTrue()
    }

    @Test
    fun `package delivery scam article passes`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "USPS scam texts spike during holiday season",
            "Fake delivery messages link to credential-stealing sites."
        )).isTrue()
    }

    // ─── WEAK keyword + CONTEXT passes (Tier 2) ─────────────────────

    @Test
    fun `fake check + elder context passes`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "Fake check sent to elderly victim",
            "An 82-year-old grandmother received a counterfeit cashier's check..."
        )).isTrue()
    }

    @Test
    fun `hack + bank account context passes`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "Hackers drain bank account in seconds",
            "After clicking the link, the victim's bank account was emptied."
        )).isTrue()
    }

    // ─── Excludes (political / celebrity / procedural) ──────────────

    @Test
    fun `political AI image article rejected`() {
        // The exact thing that triggered the user's bug report.
        assertThat(ScamArticleFilter.isScamRelevant(
            "Trump posts fake AI image of senator",
            "The president shared a doctored photo on social media."
        )).isFalse()
    }

    @Test
    fun `Biden political article rejected`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "Biden warns of election misinformation",
            "The administration is concerned about deepfakes targeting voters."
        )).isFalse()
    }

    @Test
    fun `celebrity gossip article rejected`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "Taylor Swift fake AI photos go viral",
            "Fans are upset about deepfake images."
        )).isFalse()
    }

    @Test
    fun `procedural rulemaking article rejected`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "FTC proposes new rule on data brokers",
            "The agency is seeking public comment."
        )).isFalse()
    }

    // ─── BUT: actionable agency news still passes ──────────────────

    @Test
    fun `FTC sues fraud ring article passes`() {
        // After loosening the regulatory exclude — this WAS being rejected
        // because the old regex matched "FTC announces|releases|sues|wins".
        assertThat(ScamArticleFilter.isScamRelevant(
            "FTC sues massive elder fraud ring",
            "Defendants targeted seniors with fake sweepstakes."
        )).isTrue()
    }

    @Test
    fun `FBI warns of new scam article passes`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "FBI warns of new scam targeting Zelle users",
            "Criminals are impersonating bank fraud departments."
        )).isTrue()
    }

    // ─── Edge cases ────────────────────────────────────────────────

    @Test
    fun `null inputs return false`() {
        assertThat(ScamArticleFilter.isScamRelevant(null, null)).isFalse()
    }

    @Test
    fun `blank inputs return false`() {
        assertThat(ScamArticleFilter.isScamRelevant("", "")).isFalse()
    }

    @Test
    fun `weak keyword without context rejected`() {
        // "fake AI image" alone should not pass — no scam context.
        assertThat(ScamArticleFilter.isScamRelevant(
            "Designer creates fake AI image for art project",
            "An artist explores deepfake aesthetics."
        )).isFalse()
    }

    @Test
    fun `sports cheating article rejected`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "NBA referee in match-fixing scandal",
            "Investigators allege game manipulation."
        )).isFalse()
    }

    @Test
    fun `case insensitivity works`() {
        assertThat(ScamArticleFilter.isScamRelevant(
            "PHISHING ATTACK ON LOCAL CREDIT UNION",
            "Customers lost money."
        )).isTrue()
    }
}
