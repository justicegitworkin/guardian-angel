package com.safeharborsecurity.app.data.repository

import com.google.gson.Gson
import com.safeharborsecurity.app.data.local.dao.AlertDao
import com.safeharborsecurity.app.data.local.dao.BlockedNumberDao
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.data.local.entity.BlockedNumberEntity
import com.safeharborsecurity.app.data.remote.ClaudeApiService
import com.safeharborsecurity.app.data.remote.model.ClaudeMessage
import com.safeharborsecurity.app.data.remote.model.ClaudeRequest
import com.safeharborsecurity.app.data.remote.model.SmsAnalysisResult
import com.safeharborsecurity.app.util.extractJson
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val SMS_SYSTEM_PROMPT = """You are a scam detection AI protecting a senior citizen. Analyze the given SMS for scam indicators and respond ONLY with valid JSON."""

private const val SMS_ANALYSIS_TEMPLATE = """Analyze this SMS for scam indicators. Sender: %s. Message: %s

Respond with JSON only:
{
  "risk_level": "SAFE" | "WARNING" | "SCAM",
  "confidence": 0.0-1.0,
  "reason": "one sentence explanation",
  "action": "what the senior should do"
}

Scam indicators: urgency/threats, requests for personal info or money, suspicious links, impersonating government/banks/Medicare, prize/lottery claims, unusual grammar."""

private const val SOCIAL_SYSTEM_PROMPT = """You are a scam detection AI protecting a senior citizen. Analyze the given social media message or post for scam, fraud, and manipulation indicators and respond ONLY with valid JSON."""

private const val SOCIAL_ANALYSIS_TEMPLATE = """Analyze this social media message for scam indicators. Platform/App: %s. Content: %s

Respond with JSON only:
{
  "risk_level": "SAFE" | "WARNING" | "SCAM",
  "confidence": 0.0-1.0,
  "reason": "one sentence explanation",
  "action": "what the senior should do"
}

Social media scam indicators:
- Facebook Marketplace: overpayment scams, shipping fraud, fake escrow services, too-good-to-be-true pricing, requests to move off-platform for payment, fake payment screenshots
- Craigslist: advance fee fraud, rental scams, job scams, fake tickets
- WhatsApp: "Hi Mum/Dad" family impersonation, crypto investment groups, fake prizes, chain messages
- Romance scams: love-bombing, requests for money or gift cards, refusal to video chat, sob stories
- Fake charity and political donation scams
- Fake giveaways, sweepstakes, and "you've won" messages
- Phishing links disguised as social media login pages
- Impersonation of friends or family members
- Crypto and investment scams promising guaranteed returns
- Requests for personal information, passwords, or verification codes"""

private const val EMAIL_SYSTEM_PROMPT = """You are a scam detection AI protecting a senior citizen. Analyze the given email for scam, phishing, and fraud indicators and respond ONLY with valid JSON."""

private const val EMAIL_ANALYSIS_TEMPLATE = """Analyze this email for scam/phishing indicators. Sender: %s. Subject: %s. Body: %s

Respond with JSON only:
{
  "risk_level": "SAFE" | "WARNING" | "SCAM",
  "confidence": 0.0-1.0,
  "reason": "one sentence explanation",
  "action": "what the senior should do"
}

Scam indicators: fake bank/IRS/Medicare/SSA alerts, phishing links, urgency/threats, requests for personal info or passwords, suspicious sender addresses, prize/lottery/inheritance claims, fake invoices, unusual grammar, impersonation of known companies."""

@Singleton
class AlertRepository @Inject constructor(
    private val alertDao: AlertDao,
    private val blockedNumberDao: BlockedNumberDao,
    private val claudeApi: ClaudeApiService,
    private val gson: Gson
) {
    fun getAllAlerts(): Flow<List<AlertEntity>> = alertDao.getAllAlerts()
    fun getRecentAlerts(limit: Int = 10): Flow<List<AlertEntity>> = alertDao.getRecentAlerts(limit)
    fun getAlertsByType(type: String): Flow<List<AlertEntity>> = alertDao.getAlertsByType(type)

    fun getAlertsByTypes(types: List<String>): Flow<List<AlertEntity>> = alertDao.getAlertsByTypes(types)

    suspend fun getAlertsByTypeSince(type: String, since: Long): List<AlertEntity> =
        alertDao.getAlertsByTypeSince(type, since)
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

    /** Insert an AlertEntity that was generated locally (e.g. on-device SLM verdict). */
    suspend fun insertLocal(alert: AlertEntity): Long = alertDao.insertAlert(alert)

    suspend fun deleteAlert(alert: AlertEntity) = alertDao.deleteAlert(alert)

    /** Wipes every alert row. Backs the home-screen "Clear All" action. */
    suspend fun deleteAllAlerts() = alertDao.deleteAllAlerts()

    suspend fun updateAlert(alert: AlertEntity) = alertDao.updateAlert(alert)

    suspend fun analyzeSms(
        apiKey: String,
        sender: String,
        body: String
    ): Result<AlertEntity> {
        return runCatching {
            val prompt = SMS_ANALYSIS_TEMPLATE.format(sender, body)
            val request = ClaudeRequest(
                messages = listOf(ClaudeMessage(role = "user", content = prompt)),
                system = SMS_SYSTEM_PROMPT,
                maxTokens = 256
            )

            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            if (!response.isSuccessful) throw Exception("API error: ${response.code()}")

            val rawText = response.body()?.text ?: throw Exception("Empty response")
            val result = gson.fromJson(extractJson(rawText), SmsAnalysisResult::class.java)

            val entity = AlertEntity(
                type = "SMS",
                sender = sender,
                content = body,
                riskLevel = result.riskLevel.uppercase(),
                confidence = result.confidence,
                reason = result.reason,
                action = result.action
            )
            entity.copy(id = alertDao.insertAlert(entity))
        }
    }

    suspend fun analyzeSocial(
        apiKey: String,
        platformName: String,
        content: String
    ): Result<AlertEntity> {
        return runCatching {
            val prompt = SOCIAL_ANALYSIS_TEMPLATE.format(platformName, content)
            val request = ClaudeRequest(
                messages = listOf(ClaudeMessage(role = "user", content = prompt)),
                system = SOCIAL_SYSTEM_PROMPT,
                maxTokens = 256
            )

            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            if (!response.isSuccessful) throw Exception("API error: ${response.code()}")

            val rawText = response.body()?.text ?: throw Exception("Empty response")
            val result = gson.fromJson(extractJson(rawText), SmsAnalysisResult::class.java)

            val entity = AlertEntity(
                type = "SOCIAL",
                sender = platformName,
                content = content,
                riskLevel = result.riskLevel.uppercase(),
                confidence = result.confidence,
                reason = result.reason,
                action = result.action
            )
            entity.copy(id = alertDao.insertAlert(entity))
        }
    }

    suspend fun analyzeEmail(
        apiKey: String,
        sender: String,
        subject: String,
        body: String
    ): Result<AlertEntity> {
        return runCatching {
            val prompt = EMAIL_ANALYSIS_TEMPLATE.format(sender, subject, body)
            val request = ClaudeRequest(
                messages = listOf(ClaudeMessage(role = "user", content = prompt)),
                system = EMAIL_SYSTEM_PROMPT,
                maxTokens = 256
            )

            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            if (!response.isSuccessful) throw Exception("API error: ${response.code()}")

            val rawText = response.body()?.text ?: throw Exception("Empty response")
            val result = gson.fromJson(extractJson(rawText), SmsAnalysisResult::class.java)

            val entity = AlertEntity(
                type = "EMAIL",
                sender = sender,
                content = "Subject: $subject\n$body",
                riskLevel = result.riskLevel.uppercase(),
                confidence = result.confidence,
                reason = result.reason,
                action = result.action
            )
            entity.copy(id = alertDao.insertAlert(entity))
        }
    }
}
