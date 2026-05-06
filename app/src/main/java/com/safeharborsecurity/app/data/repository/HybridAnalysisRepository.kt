package com.safeharborsecurity.app.data.repository

import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.ml.OnDeviceScamClassifier
import com.safeharborsecurity.app.ml.ScamClassification
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Part A3: Hybrid analysis. Tries the on-device classifier first; falls back to
 * Claude (via AlertRepository) only when:
 *   - the local result is ambiguous (needsCloudVerification = true), AND
 *   - the user has a Claude API key configured.
 *
 * Returns a normalised AnalysisResult with `analysisSource` so callers can show
 * "Checked locally" vs "Verified by Safe Companion AI".
 */
data class AnalysisResult(
    val verdict: String,             // "DANGEROUS" | "SUSPICIOUS" | "SAFE" | "UNKNOWN"
    val confidence: Float,
    val reasons: List<String>,
    val analysisSource: String,      // "on_device" | "cloud_ai"
    val cloudAlertEntity: AlertEntity? = null
)

@Singleton
class HybridAnalysisRepository @Inject constructor(
    private val localClassifier: OnDeviceScamClassifier,
    private val alertRepository: AlertRepository,
    private val userPreferences: UserPreferences
) {

    suspend fun analyzeSms(
        sender: String,
        body: String,
        forceCloud: Boolean = false
    ): AnalysisResult {
        val local = localClassifier.classifyText(body, sender)
        if (!forceCloud && !local.needsCloudVerification) return local.toResult()

        val apiKey = userPreferences.apiKey.first()
        if (apiKey.isBlank()) return local.toResult(extra = "Cloud verification unavailable (no API key)")

        return alertRepository.analyzeSms(apiKey, sender, body)
            .map { entity ->
                AnalysisResult(
                    verdict = entity.riskLevel,
                    confidence = entity.confidence,
                    reasons = listOf(entity.reason),
                    analysisSource = "cloud_ai",
                    cloudAlertEntity = entity
                )
            }
            .getOrElse { local.toResult(extra = "Cloud verification failed: ${it.message}") }
    }

    suspend fun analyzeText(
        text: String,
        sender: String? = null,
        forceCloud: Boolean = false
    ): AnalysisResult {
        val local = localClassifier.classifyText(text, sender)
        if (!forceCloud && !local.needsCloudVerification) return local.toResult()
        // Without a sender we don't have a categorical type; just return the local verdict.
        // Cloud-side text analysis is exposed via the SMS/email/social paths above.
        return local.toResult()
    }

    fun analyzeUrl(url: String): AnalysisResult = localClassifier.classifyUrl(url).toResult()

    fun analyzePhoneNumber(number: String): AnalysisResult =
        localClassifier.classifyPhoneNumber(number).toResult()

    private fun ScamClassification.toResult(extra: String? = null): AnalysisResult =
        AnalysisResult(
            verdict = verdict,
            confidence = if (extra != null) confidence * 0.85f else confidence,
            reasons = if (extra != null) reasons + extra else reasons,
            analysisSource = "on_device"
        )
}
