package com.safeharborsecurity.app.data.remote.model

import com.google.gson.annotations.SerializedName

// ── Request ──────────────────────────────────────────────────────────────────

data class ClaudeRequest(
    val model: String = "claude-haiku-4-5-20251001",
    @SerializedName("max_tokens") val maxTokens: Int = 1024,
    val messages: List<ClaudeMessage>,
    val system: String? = null
)

data class ClaudeMessage(
    val role: String,   // "user" | "assistant"
    val content: Any    // String for text-only, List<MessageContentBlock> for multimodal
)

// Content blocks for multimodal messages (vision)
data class MessageContentBlock(
    val type: String,              // "text" | "image"
    val text: String? = null,      // for type="text"
    val source: ImageSource? = null // for type="image"
)

data class ImageSource(
    val type: String = "base64",
    @SerializedName("media_type") val mediaType: String,
    val data: String
)

// ── Response ─────────────────────────────────────────────────────────────────

data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @SerializedName("stop_reason") val stopReason: String?,
    val usage: Usage
) {
    val text: String get() = content.firstOrNull { it.type == "text" }?.text ?: ""
}

data class ContentBlock(
    val type: String,
    val text: String
)

data class Usage(
    @SerializedName("input_tokens") val inputTokens: Int,
    @SerializedName("output_tokens") val outputTokens: Int
)

// ── Domain models returned from analysis ─────────────────────────────────────

data class SmsAnalysisResult(
    @SerializedName("risk_level") val riskLevel: String = "SAFE",
    val confidence: Float = 0f,
    val reason: String = "",
    val action: String = ""
)

data class CallAnalysisResult(
    @SerializedName("risk_level") val riskLevel: String = "SAFE",
    val warning: String? = null,
    val recommendation: String = "CONTINUE"
)

data class FamilyContact(
    val nickname: String,
    val number: String,
    /** Optional — used by Item 4 (option c) to CC family on the weekly Gmail
     *  digest. Existing contacts saved before this field was added will
     *  deserialise with email = null, no migration needed. */
    val email: String? = null
)
