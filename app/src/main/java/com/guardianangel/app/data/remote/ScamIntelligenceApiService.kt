package com.guardianangel.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

// ── Response models ───────────────────────────────────────────────────────────

data class ScamRuleDto(
    @SerializedName("id")                    val id: Long,
    @SerializedName("scam_type")             val scamType: String,
    @SerializedName("key_phrases")           val keyPhrases: List<String> = emptyList(),
    @SerializedName("urgency_indicators")    val urgencyIndicators: List<String> = emptyList(),
    @SerializedName("impersonation_targets") val impersonationTargets: List<String> = emptyList(),
    @SerializedName("plain_english_warning") val plainEnglishWarning: String,
    @SerializedName("severity")              val severity: String = "MEDIUM",
    @SerializedName("created_at")            val createdAt: Long
)

data class ScamRulesResponse(
    @SerializedName("rules") val rules: List<ScamRuleDto> = emptyList()
)

data class ScamReportDto(
    @SerializedName("type")      val type: String,      // SMS_SCAM | SMS_WARNING | CALL_SCREENED | CALL_SCAM
    @SerializedName("severity")  val severity: String = "MEDIUM",
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface ScamIntelligenceApiService {

    /**
     * Fetch all rules created after [since] ms.
     * [url] is the full endpoint URL, e.g. "https://myserver.railway.app/scam-rules".
     * @Query("since") is appended automatically by Retrofit.
     */
    @GET
    suspend fun getScamRules(
        @Url url: String,
        @Query("since") since: Long
    ): Response<ScamRulesResponse>

    /** Anonymous scam event report from the app. */
    @POST
    suspend fun reportScam(
        @Url url: String,
        @Body report: ScamReportDto
    ): Response<Unit>
}
