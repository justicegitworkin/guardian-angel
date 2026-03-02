package com.guardianangel.app.data.repository

import com.guardianangel.app.data.local.dao.MessageDao
import com.guardianangel.app.data.local.entity.MessageEntity
import com.guardianangel.app.data.remote.ClaudeApiService
import com.guardianangel.app.data.remote.model.ClaudeMessage
import com.guardianangel.app.data.remote.model.ClaudeRequest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val GUARDIAN_SYSTEM_PROMPT = """You are Guardian Angel, a warm, patient, and protective AI companion for senior citizens. Your job is to help them stay safe from scams, answer their questions simply and clearly, and be a friendly presence. Always:
- Use simple, clear language — no jargon
- Be warm, reassuring, and never condescending
- If they describe something suspicious, explain clearly why it might be a scam and what to do
- Keep responses concise — 2-4 sentences max unless more detail is needed
- Always end with encouragement or an offer to help further"""

private const val CONTEXT_WINDOW = 20  // max messages sent to the API

@Singleton
class ChatRepository @Inject constructor(
    private val claudeApi: ClaudeApiService,
    private val messageDao: MessageDao
) {
    fun getMessages(sessionId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForSession(sessionId)

    suspend fun saveMessage(content: String, isFromUser: Boolean, sessionId: String): Long =
        messageDao.insertMessage(MessageEntity(content = content, isFromUser = isFromUser, sessionId = sessionId))

    suspend fun sendToGuardian(apiKey: String, userMessage: String, sessionId: String): Result<String> {
        return runCatching {
            // Fetch only the last CONTEXT_WINDOW messages at the DB level — avoids loading
            // the full unbounded history into memory.
            val history = messageDao.getRecentMessagesForSession(sessionId, CONTEXT_WINDOW)
                .reversed()   // DAO returns DESC; we need ASC for the API
                .map { ClaudeMessage(role = if (it.isFromUser) "user" else "assistant", content = it.content) }

            val messages = history + ClaudeMessage(role = "user", content = userMessage)
            callGuardianApi(apiKey, messages)
        }
    }

    /**
     * Sends a message using an in-memory context list — nothing is read from or written to Room.
     * Used when "Save conversation history" is OFF (privacy-first mode).
     */
    suspend fun sendInMemory(
        apiKey: String,
        userMessage: String,
        memoryContext: List<ClaudeMessage>
    ): Result<String> {
        return runCatching {
            val messages = memoryContext.takeLast(CONTEXT_WINDOW) +
                ClaudeMessage(role = "user", content = userMessage)
            callGuardianApi(apiKey, messages)
        }
    }

    private suspend fun callGuardianApi(apiKey: String, messages: List<ClaudeMessage>): String {
        val response = claudeApi.sendMessage(
            apiKey = apiKey,
            request = ClaudeRequest(messages = messages, system = GUARDIAN_SYSTEM_PROMPT)
        )
        return if (response.isSuccessful) {
            response.body()?.text ?: throw Exception("Empty response from Guardian")
        } else {
            when (response.code()) {
                401 -> throw Exception("Invalid API key")
                429 -> throw Exception("Too many requests")
                else -> throw Exception("Guardian error: ${response.code()}")
            }
        }
    }

    suspend fun clearSession(sessionId: String) = messageDao.clearSession(sessionId)
}
