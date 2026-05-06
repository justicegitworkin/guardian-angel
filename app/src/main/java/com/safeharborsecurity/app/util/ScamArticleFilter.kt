package com.safeharborsecurity.app.util

import android.util.Log

/**
 * Two-tier filter that decides whether an RSS article is genuinely about
 * scams aimed at consumers (the kind of content the user wants in their
 * "What scammers are up to" feed) versus general news that happens to
 * contain a scam-adjacent word like "fake" or "stolen."
 *
 * Tier 1 — STRONG keywords: any of these alone make the article pass.
 *   These are unambiguous: "phishing", "smishing", "grandparent scam",
 *   "robocall", "wire fraud", etc. There's no non-scam interpretation.
 *
 * Tier 2 — WEAK keywords: words like "fake", "stolen", "hack", "trick"
 *   that get used in lots of non-scam contexts. These pass ONLY when the
 *   article also has a CONTEXT keyword (money, phone call, email, victim,
 *   etc.) signalling it's about consumer fraud, not a political AI image
 *   or a sports cheating story.
 *
 * EXCLUDES — hard rejects regardless of keyword matches. Catches political
 *   content (Trump/Biden/etc.), celebrity gossip, AI-image controversies,
 *   policy/legislative news, monthly complaint statistics. Beta testers
 *   shouldn't see "Trump posts fake AI image of a senator" in the scam
 *   feed during testing — it's a distraction at best and politically
 *   loaded at worst.
 */
object ScamArticleFilter {

    /** Tier 1: any of these alone is enough. Unambiguous scam vocabulary. */
    private val STRONG_KEYWORDS = listOf(
        "scam", "scammer", "scamming",
        "phishing", "smishing", "vishing", "spoofing",
        "robocall", "robocalls", "spam call", "spam calls",
        "fraud", "fraudster", "fraudulent",
        // Specific scam types
        "grandparent scam", "romance scam", "tech support scam",
        "irs scam", "social security scam", "medicare scam",
        "charity scam", "lottery scam", "sweepstakes scam",
        "imposter scam", "imposter fraud", "impersonation scam",
        "puppy scam", "rental scam", "moving scam",
        "utility scam", "utility shutoff",
        "jury duty scam", "warrant scam", "arrest scam",
        "tax scam", "stimulus scam", "irs imposter",
        "investment fraud", "ponzi", "pyramid scheme",
        "advance fee", "money mule", "skimming",
        "fake check", "counterfeit check", "fake invoice",
        "ransomware", "malware", "spyware", "credential stuffing",
        "data breach", "credential theft", "identity theft",
        "elder fraud", "senior fraud", "elder scam", "senior scam",
        "consumer fraud", "consumer alert", "consumer warning",
        "wire fraud", "card skimming", "atm skimming",
        "deepfake voice", "voice cloning scam", "ai voice scam",
        "gift card scam", "crypto scam", "bitcoin scam",
        "venmo scam", "zelle scam", "cash app scam", "paypal scam",
        "online dating scam", "sextortion", "blackmail scam",
        // Phrases that almost always describe a scam
        "phishing email", "phishing text", "phishing attack", "phishing campaign",
        "spoofed website", "spoofed number", "fake website",
        "package delivery scam", "usps scam", "fedex scam", "ups scam",
        "fake amazon", "amazon impersonation",
        "account takeover", "compromised account",
        "dark web", "stolen credentials",
        // Broader cyber-fraud vocabulary so Krebs / Hacker News pass through
        "cybercrime", "cybercriminal", "cyber fraud", "cyber attack",
        "business email compromise", "bec scam",
        "extortion", "ransom demand"
    )

    /** Tier 2: ambiguous on their own. Need CONTEXT_KEYWORDS to qualify. */
    private val WEAK_KEYWORDS = listOf(
        "fake", "stolen", "hack", "hacked", "hacker", "breach", "trick",
        "deceptive", "mislead", "deceiv", "exploit", "impersonat",
        "warning", "alert", "caution", "beware",
        "victim", "swindle", "con artist"
    )

    /** Context that qualifies a WEAK keyword as scam-relevant. */
    private val CONTEXT_KEYWORDS = listOf(
        // Communication channels
        "phone call", "phone scam", "call from",
        "text message", "text from", "sms",
        "email scam", "email from",
        // Money / financial
        "wire transfer", "wire money", "send money", "wire $", "$$",
        "credit card", "debit card", "bank account", "savings account",
        "wallet", "venmo", "zelle", "cash app", "paypal", "western union",
        "money order", "check fraud", "payment", "refund",
        // Targets
        "elderly", "elder", "senior citizen", "retiree", "pensioner",
        "consumer", "victim", "fell for",
        // Identifiers stolen
        "social security number", "ssn", "credit score",
        "password stolen", "account hijack", "account takeover",
        // Common scam delivery
        "click the link", "click here", "verify account", "suspended",
        "package delivery", "irs", "medicare", "social security"
    )

    /** Hard rejects, evaluated against title only. */
    private val EXCLUDE_PATTERNS = listOf(
        // Political — the AI deepfake/fake-image controversy that triggered
        // the user's report. Tester safety: nothing political in the feed.
        Regex(
            "\\b(trump|biden|harris|obama|vance|kamala|kennedy|musk)\\b",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "\\b(election|elections|gop|democrat|republican|congress|senator|congressman|congresswoman|congressperson)\\b",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "\\b(supreme court|doj|department of justice|prosecut|indict|impeach)\\b",
            RegexOption.IGNORE_CASE
        ),
        // Celebrity gossip — usually has "fake" / "stolen" without scam context
        Regex(
            "\\b(kanye|taylor swift|swiftie|kardashian|beyonce|drake|kendrick)\\b",
            RegexOption.IGNORE_CASE
        ),
        // AI image / deepfake controversy NOT about voice scams
        Regex(
            "ai (image|photo|picture|video|art) (?!.*(scam|fraud|phish|voice clon))",
            RegexOption.IGNORE_CASE
        ),
        // Sports cheating, doping, match-fixing
        Regex(
            "\\b(doping|match.fixing|cheating scandal|nba|nfl|mlb|fifa)\\b",
            RegexOption.IGNORE_CASE
        ),
        // Regulatory housekeeping — rule-makings, comment periods, policy
        // updates. We still want "FTC sues fraud ring" / "FBI warns of new
        // scam" through, so we narrowed this to procedural verbs only.
        Regex(
            "^(FTC|FBI|BBB|AARP|FCC|DOJ|CFPB)\\s+(proposes|finalizes|seeks comment|publishes report|updates rule|issues rule)",
            RegexOption.IGNORE_CASE
        ),
        Regex("\\bannual report\\b", RegexOption.IGNORE_CASE),
        Regex("\\brulemaking\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(job opening|hiring|career|now hiring)\\b", RegexOption.IGNORE_CASE),
        Regex(
            "\\b(budget|appropriation|legislation|bill passed|tax reform)\\b",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "(January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{4}\\s+complaint",
            RegexOption.IGNORE_CASE
        )
    )

    private const val TAG = "ScamArticleFilter"

    fun isScamRelevant(title: String?, description: String?): Boolean {
        val safeTitle = title.orEmpty()
        val safeDescription = description.orEmpty()
        val combined = "$safeTitle $safeDescription".lowercase()
        if (combined.isBlank()) return false

        // Hard excludes evaluated against title — kill politicized content
        // before any keyword scoring happens.
        val excluded = EXCLUDE_PATTERNS.firstOrNull { it.containsMatchIn(safeTitle) }
        if (excluded != null) {
            Log.d(TAG, "REJECT (exclude=${excluded.pattern}): $safeTitle")
            return false
        }

        // Tier 1: a STRONG keyword anywhere in title or description is enough.
        val strongHit = STRONG_KEYWORDS.firstOrNull { combined.contains(it) }
        if (strongHit != null) {
            Log.d(TAG, "ACCEPT (strong=$strongHit): $safeTitle")
            return true
        }

        // Tier 2: WEAK keywords pass only with a CONTEXT keyword in the same
        // article. "fake AI image" → no context → reject. "fake check sent
        // to elderly victim" → context (check + elderly + victim) → pass.
        val weakHit = WEAK_KEYWORDS.firstOrNull { combined.contains(it) }
        val contextHit = CONTEXT_KEYWORDS.firstOrNull { combined.contains(it) }
        if (weakHit != null && contextHit != null) {
            Log.d(TAG, "ACCEPT (weak=$weakHit + context=$contextHit): $safeTitle")
            return true
        }
        Log.v(
            TAG,
            "REJECT (no match; weak=${weakHit ?: "-"} ctx=${contextHit ?: "-"}): $safeTitle"
        )
        return false
    }
}
