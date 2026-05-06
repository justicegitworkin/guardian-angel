package com.safeharborsecurity.app.util

import android.content.Context
import android.provider.ContactsContract

/**
 * Simple local number lookup for badging incoming calls/texts.
 * Checks against device contacts and common scam number patterns.
 */
object NumberLookupUtil {

    enum class NumberSafety(val label: String, val emoji: String) {
        KNOWN_SAFE("Known contact", "🟢"),
        UNKNOWN("Unknown number", "🟡"),
        LIKELY_SCAM("Likely scam number", "🔴")
    }

    // Common scam number patterns
    private val SCAM_PATTERNS = listOf(
        Regex("^\\+?1?900\\d{7}$"),              // 900 premium numbers
        Regex("^\\+?1?809\\d{7}$"),              // Dominican Republic scam area code
        Regex("^\\+?1?876\\d{7}$"),              // Jamaica scam area code
        Regex("^\\+?1?284\\d{7}$"),              // British Virgin Islands
        Regex("^\\+?233\\d+$"),                  // Ghana
        Regex("^\\+?234\\d+$"),                  // Nigeria
        Regex("^\\+?44\\d{10}$"),                // UK spoofed
    )

    // Legitimate short codes that are NOT scams
    private val SAFE_SHORT_CODES = setOf(
        "911", "311", "411", "611", "711",
        "72727",  // SCAM reporting
        "7726",   // SPAM reporting
    )

    fun lookupNumber(context: Context, number: String): NumberSafety {
        // Normalize
        val cleaned = number.replace(Regex("[^+\\d]"), "")

        // Check if in contacts
        if (isInContacts(context, number)) return NumberSafety.KNOWN_SAFE

        // Check safe short codes
        if (cleaned in SAFE_SHORT_CODES) return NumberSafety.KNOWN_SAFE

        // Check scam patterns
        if (SCAM_PATTERNS.any { it.matches(cleaned) }) return NumberSafety.LIKELY_SCAM

        // Very short numbers that aren't known short codes are suspicious
        if (cleaned.length < 6 && cleaned.length > 3) return NumberSafety.UNKNOWN

        return NumberSafety.UNKNOWN
    }

    private fun isInContacts(context: Context, number: String): Boolean {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { it.count > 0 } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
