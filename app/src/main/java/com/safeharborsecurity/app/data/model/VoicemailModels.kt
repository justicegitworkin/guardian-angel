package com.safeharborsecurity.app.data.model

enum class VoicemailStage {
    METHOD_SELECT,
    LISTEN_LIVE,
    MANUAL_TEXT,
    ANALYSING,
    RESULT
}

enum class VoicemailMethod {
    LISTEN_LIVE,
    MANUAL_TEXT
}

data class VoicemailScanResult(
    val verdict: String = "",       // SAFE, SUSPICIOUS, DANGEROUS
    val confidence: Int = 0,
    val summary: String = "",
    val explanation: String = "",
    val scamType: String = "",
    val redFlags: List<String> = emptyList(),
    val recommendedAction: String = "",
    val transcriptUsed: String = ""
)
