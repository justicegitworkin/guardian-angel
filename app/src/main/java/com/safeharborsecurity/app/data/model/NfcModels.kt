package com.safeharborsecurity.app.data.model

data class NfcTagAnalysis(
    val tagId: String,
    val techList: List<String>,
    val payloads: List<NdefPayload>,
    val riskLevel: NfcRiskLevel,
    val summary: String,
    val details: String,
    val recommendation: String
)

data class NdefPayload(
    val type: String,
    val content: String,
    val isUrl: Boolean = false,
    val isSuspicious: Boolean = false
)

enum class NfcRiskLevel(val label: String) {
    SAFE("Safe"),
    CAUTION("Be Careful"),
    DANGEROUS("Dangerous"),
    UNKNOWN("Unknown")
}

data class NfcState(
    val isNfcAvailable: Boolean = false,
    val isNfcEnabled: Boolean = false,
    val lastScannedTag: NfcTagAnalysis? = null,
    val isScanning: Boolean = false
)
