package com.safeharborsecurity.app.data.model

data class QrAnalysisResult(
    val rawValue: String,
    val qrType: QrType,
    val instantWarning: String? = null,
    val needsClaudeAnalysis: Boolean = false
)
