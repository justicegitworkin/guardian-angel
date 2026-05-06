package com.safeharborsecurity.app.util

/**
 * Part D1: Demo mode lets testers use Safe Companion with NO API keys configured.
 *
 * When the Claude API key is blank, the app:
 *   - uses the on-device SLM classifier for all scam detection (Part A)
 *   - falls back to Google Cloud / Android built-in TTS instead of ElevenLabs
 *   - shows pre-loaded sample alerts so the UI is meaningful immediately
 *
 * The app is fully functional in demo mode — just without cloud AI conversation
 * and premium voice quality. The Settings screen reframes API keys as optional
 * upgrades, not requirements.
 */
object DemoMode {

    /** True when no Claude API key is configured. */
    fun isActive(claudeApiKey: String?): Boolean = claudeApiKey.isNullOrBlank()

    /**
     * Capability matrix. UI can use this to decide what to show/hide and
     * whether to display a "Premium feature — add a key in Settings" hint.
     */
    val capabilities: Map<String, Boolean> = mapOf(
        "scam_detection"      to true,   // On-device SLM
        "sms_scanning"        to true,   // On-device SLM
        "url_checking"        to true,   // On-device pattern matching
        "qr_scanning"         to true,   // ML Kit (no API key)
        "app_checking"        to true,   // On-device
        "room_scanning"       to true,   // All sensors
        "news_feed"           to true,   // RSS feeds
        "family_alerts"       to true,   // Local SmsManager
        "voice_chat"          to false,  // Needs Claude key
        "premium_voice"       to false,  // Needs ElevenLabs key
        "cloud_verification"  to false,  // Needs Claude key
        "image_analysis"      to false   // Needs Claude key (vision)
    )

    fun isCapability(name: String): Boolean = capabilities[name] ?: false
}
