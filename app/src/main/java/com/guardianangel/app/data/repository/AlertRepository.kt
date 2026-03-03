package com.guardianangel.app.data.repository

import android.util.Log
import com.google.gson.Gson
import com.guardianangel.app.BuildConfig
import com.guardianangel.app.data.ai.OnDeviceAIService
import com.guardianangel.app.data.datastore.UserPreferences
import com.guardianangel.app.data.local.dao.AlertDao
import com.guardianangel.app.data.local.dao.BlockedNumberDao
import com.guardianangel.app.data.local.dao.ScamRuleDao
import com.guardianangel.app.data.local.entity.AlertEntity
import com.guardianangel.app.data.local.entity.BlockedNumberEntity
import com.guardianangel.app.data.remote.ClaudeApiService
import com.guardianangel.app.data.remote.ScamIntelligenceApiService
import com.guardianangel.app.data.remote.ScamReportDto
import com.guardianangel.app.data.remote.model.ClaudeMessage
import com.guardianangel.app.data.remote.model.ClaudeRequest
import com.guardianangel.app.data.remote.model.SmsAnalysisResult
import com.guardianangel.app.util.extractJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlertRepository"

private const val SMS_SYSTEM_PROMPT =
    """You are a scam detection AI protecting a senior citizen. Analyze the given SMS for scam indicators and respond ONLY with valid JSON."""

private const val SMS_ANALYSIS_TEMPLATE =
    """Analyze this SMS for scam indicators. Sender: %s. Message: %s

Respond with JSON only:
{
  "risk_level": "SAFE" | "WARNING" | "SCAM",
  "confidence": 0.0-1.0,
  "reason": "one sentence explanation",
  "action": "what the senior should do"
}

Scam indicators: urgency/threats, requests for personal info or money, suspicious links, impersonating government/banks/Medicare, prize/lottery claims, unusual grammar."""

@Singleton
class AlertRepository @Inject constructor(
    private val alertDao: AlertDao,
    private val blockedNumberDao: BlockedNumberDao,
    private val scamRuleDao: ScamRuleDao,
    private val claudeApi: ClaudeApiService,
    private val scamIntelApi: ScamIntelligenceApiService,
    private val onDeviceAI: OnDeviceAIService,
    private val userPreferences: UserPreferences,
    private val gson: Gson
) {
    fun getAllAlerts(): Flow<List<AlertEntity>> = alertDao.getAllAlerts()
    fun getRecentAlerts(limit: Int = 10): Flow<List<AlertEntity>> = alertDao.getRecentAlerts(limit)
    fun getAlertsByType(type: String): Flow<List<AlertEntity>> = alertDao.getAlertsByType(type)
    fun getAlertsByRisk(riskLevel: String): Flow<List<AlertEntity>> = alertDao.getAlertsByRisk(riskLevel)

    suspend fun getAlertById(id: Long): AlertEntity? = alertDao.getAlertById(id)

    suspend fun countScamsThisMonth(): Int {
        val oneMonthAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        return alertDao.countScamsBlocked(oneMonthAgo)
    }

    suspend fun isBlocked(number: String): Boolean = blockedNumberDao.isBlocked(number)

    suspend fun blockNumber(number: String, reason: String = "") {
        blockedNumberDao.blockNumber(BlockedNumberEntity(number = number, reason = reason))
        alertDao.blockBySender(number)
    }

    suspend fun markAsRead(id: Long) = alertDao.markAsRead(id)

    suspend fun deleteAll() = alertDao.deleteAll()

    /**
     * Analyzes an SMS for scam risk.
     *
     * Privacy routing:
     *  - "ON"   → on-device only; if unavailable, returns a safe fallback (no cloud call)
     *  - "AUTO" → tries on-device first, falls back to cloud
     *  - "OFF"  → cloud only
     *
     * The raw SMS body is NEVER written to the database — only the risk assessment result.
     */
    suspend fun analyzeSms(
        apiKey: String,
        sender: String,
        body: String
    ): Result<AlertEntity> {
        // ── Log only in debug builds; never log SMS content in release ─────
        if (BuildConfig.DEBUG) Log.d(TAG, "Analyzing SMS from $sender (${body.length} chars)")

        val privacyMode = userPreferences.privacyMode.first()

        // Try on-device AI
        val onDeviceResult: SmsAnalysisResult? = when (privacyMode) {
            "ON", "AUTO" -> {
                onDeviceAI.checkCompatibility()
                onDeviceAI.analyzeTextForScam(body)
            }
            else -> null
        }

        // If privacy mode is ON and on-device failed, return a safe no-network fallback
        if (privacyMode == "ON" && onDeviceResult == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Privacy ON: on-device unavailable, skipping cloud")
            val fallback = AlertEntity(
                type = "SMS",
                sender = sender,
                riskLevel = "SAFE",
                confidence = 0f,
                reason = "On-device analysis unavailable on this device",
                action = "Guardian could not analyze this message — Private Mode is ON"
            )
            return Result.success(fallback.copy(id = alertDao.insertAlert(fallback)))
        }

        // Use on-device result if available
        if (onDeviceResult != null) {
            val entity = AlertEntity(
                type = "SMS",
                sender = sender,
                riskLevel = onDeviceResult.riskLevel.uppercase(),
                confidence = onDeviceResult.confidence,
                reason = onDeviceResult.reason,
                action = onDeviceResult.action
            )
            val id = alertDao.insertAlert(entity)
            if (entity.riskLevel != "SAFE") userPreferences.recordSmsAlert()
            return Result.success(entity.copy(id = id))
        }

        // Fall back to Claude cloud API
        return runCatching {
            // Inject latest HIGH/CRITICAL intelligence rules into the system prompt
            val sevenDaysAgo  = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            val activeRules   = scamRuleDao.getRecentHighCritical(sevenDaysAgo, limit = 10)
            val systemPrompt  = if (activeRules.isEmpty()) {
                SMS_SYSTEM_PROMPT
            } else {
                buildString {
                    append(SMS_SYSTEM_PROMPT)
                    append("\n\nCurrent active scam alerts from FBI/FTC/CISA (apply these to improve detection):\n")
                    activeRules.forEach { rule ->
                        append("• [${rule.severity}] ${rule.scamType}: ${rule.plainEnglishWarning}\n")
                    }
                }
            }

            val prompt  = SMS_ANALYSIS_TEMPLATE.format(sender, body)
            val request = ClaudeRequest(
                messages  = listOf(ClaudeMessage(role = "user", content = prompt)),
                system    = systemPrompt,
                maxTokens = 256
            )
            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            if (!response.isSuccessful) throw Exception("API error: ${response.code()}")

            val rawText = response.body()?.text ?: throw Exception("Empty response")
            val result  = gson.fromJson(extractJson(rawText), SmsAnalysisResult::class.java)

            // Increment cloud message counter (date-keyed, resets daily)
            val today = LocalDate.now().toString()
            userPreferences.incrementCloudMessageCount(today)

            // Store analysis result ONLY — never the original SMS body
            val entity = AlertEntity(
                type      = "SMS",
                sender    = sender,
                riskLevel = result.riskLevel.uppercase(),
                confidence= result.confidence,
                reason    = result.reason,
                action    = result.action
            )
            val id = alertDao.insertAlert(entity)
            if (entity.riskLevel != "SAFE") {
                userPreferences.recordSmsAlert()
                // Anonymous telemetry to the server (fire-and-forget)
                val serverUrl = userPreferences.scamIntelServerUrl.first().trim()
                if (serverUrl.isNotBlank()) {
                    runCatching {
                        scamIntelApi.reportScam(
                            "${serverUrl.trimEnd('/')}/scam-report",
                            ScamReportDto(
                                type     = if (entity.riskLevel == "SCAM") "SMS_SCAM" else "SMS_WARNING",
                                severity = entity.riskLevel
                            )
                        )
                    }
                }
            }
            entity.copy(id = id)
        }
    }
}
