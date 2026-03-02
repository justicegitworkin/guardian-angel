package com.guardianangel.app.data.repository

import com.google.gson.Gson
import com.guardianangel.app.data.local.dao.CallLogDao
import com.guardianangel.app.data.local.entity.CallLogEntity
import com.guardianangel.app.data.remote.ClaudeApiService
import com.guardianangel.app.data.remote.model.CallAnalysisResult
import com.guardianangel.app.data.remote.model.ClaudeMessage
import com.guardianangel.app.data.remote.model.ClaudeRequest
import com.guardianangel.app.util.extractJson
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val CALL_ANALYSIS_PROMPT = """You are monitoring a phone call to protect a senior citizen from scams.
Analyze this call transcript chunk and respond ONLY with valid JSON:
{
  "risk_level": "SAFE" | "SUSPICIOUS" | "SCAM",
  "warning": null or a short warning to show the senior,
  "recommendation": "CONTINUE" | "HANGUP" | "ASK_GUARDIAN"
}
Look for: requests for money/gift cards/wire transfers, threats of arrest/legal action,
impersonating IRS/SSA/Medicare/police, requests for SSN/passwords/bank info, high-pressure urgency."""

@Singleton
class CallRepository @Inject constructor(
    private val callLogDao: CallLogDao,
    private val claudeApi: ClaudeApiService,
    private val gson: Gson
) {
    fun getAllCallLogs(): Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()
    suspend fun getCallLogById(id: Long): CallLogEntity? = callLogDao.getCallLogById(id)
    suspend fun insertCallLog(log: CallLogEntity): Long = callLogDao.insertCallLog(log)
    suspend fun updateCallLog(log: CallLogEntity) = callLogDao.updateCallLog(log)
    suspend fun blockNumber(number: String) = callLogDao.blockNumber(number)

    suspend fun analyzeCallChunk(apiKey: String, transcript: String): Result<CallAnalysisResult> {
        return runCatching {
            val request = ClaudeRequest(
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = "Call transcript:\n$transcript\n\nAnalyze this for scam risk."
                    )
                ),
                system = CALL_ANALYSIS_PROMPT,
                maxTokens = 256
            )

            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            if (!response.isSuccessful) throw Exception("API error: ${response.code()}")

            val rawText = response.body()?.text ?: throw Exception("Empty response")
            gson.fromJson(extractJson(rawText), CallAnalysisResult::class.java)
        }
    }
}
