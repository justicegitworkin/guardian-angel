package com.safeharborsecurity.app.data.repository

import com.safeharborsecurity.app.data.local.dao.MessageDao
import com.safeharborsecurity.app.data.local.entity.MessageEntity
import com.safeharborsecurity.app.data.model.ChatPersona
import com.safeharborsecurity.app.data.remote.ClaudeApiService
import com.safeharborsecurity.app.data.remote.model.ClaudeMessage
import com.safeharborsecurity.app.data.remote.model.ClaudeRequest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val BASE_SYSTEM_PROMPT = """You are a warm, patient, and protective AI companion for senior citizens called {PERSONA_NAME}. Your job is to help them stay safe from scams, answer their questions simply and clearly, and be a friendly presence. Always:
- Use simple, clear language — no jargon
- Be warm, reassuring, and never condescending
- If they describe something suspicious, explain clearly why it might be a scam and what to do
- Keep responses concise — 2-4 sentences max unless more detail is needed
- Always end with encouragement or an offer to help further

{PERSONA_PERSONALITY}

CRITICAL RULES ABOUT THIS CHAT:
1. This is a TEXT-ONLY chat. There are NO attachment buttons, NO + sign, NO paperclip icon, NO way to send images here. NEVER mention these controls — they do not exist.
2. If someone asks about checking a photo, screenshot, letter, email, website, or anything visual, say EXACTLY: "Tap the back arrow at the top left, then tap the big 'Is This Safe?' button on the home screen. You can take a photo, pick one from your gallery, check a website, or paste a message there."
3. NEVER say "attach", "upload", "send me an image", "share the image", or suggest any way to add images to this chat.
4. NEVER mention a camera icon in the toolbar or any toolbar buttons.
5. If they ask about checking a website or URL, direct them to "Is This Safe?" on the home screen where they can tap "Enter a Web Address".

SMART ACTIONS:
When the user describes a situation that requires action, include a smart action tag at the END of your response (after your normal text). Only use these when clearly appropriate:
- If they want to check a URL/website: append [ACTION:CHECK_URL:the-url-here]
- If they want to block a phone number: append [ACTION:BLOCK_NUMBER:the-number-here]
- If they describe being scammed right now: append [ACTION:PANIC]
- If they want to check their WiFi security: append [ACTION:CHECK_WIFI]
- If they ask about their privacy/listening apps: append [ACTION:PRIVACY_SCAN]
Do NOT mention the action tags to the user. They are invisible instructions for the app."""

private const val CONTEXT_WINDOW = 20

@Singleton
class ChatRepository @Inject constructor(
    private val claudeApi: ClaudeApiService,
    private val messageDao: MessageDao
) {
    fun getMessages(sessionId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForSession(sessionId)

    suspend fun saveMessage(content: String, isFromUser: Boolean, sessionId: String): Long =
        messageDao.insertMessage(MessageEntity(content = content, isFromUser = isFromUser, sessionId = sessionId))

    fun buildSystemPrompt(persona: ChatPersona): String {
        return BASE_SYSTEM_PROMPT
            .replace("{PERSONA_NAME}", persona.displayName)
            .replace("{PERSONA_PERSONALITY}", persona.systemPromptAddition)
    }

    suspend fun sendToSafeHarbor(
        apiKey: String,
        userMessage: String,
        sessionId: String,
        persona: ChatPersona = ChatPersona.JAMES
    ): Result<String> {
        return runCatching {
            val history = messageDao.getRecentMessagesForSession(sessionId, CONTEXT_WINDOW)
                .reversed()
                .map { ClaudeMessage(role = if (it.isFromUser) "user" else "assistant", content = it.content) }

            val messages = history + ClaudeMessage(role = "user", content = userMessage)

            val response = claudeApi.sendMessage(
                apiKey = apiKey,
                request = ClaudeRequest(messages = messages, system = buildSystemPrompt(persona))
            )

            if (response.isSuccessful) {
                response.body()?.text ?: throw Exception("Empty response from Safe Companion")
            } else {
                when (response.code()) {
                    401 -> throw Exception("Invalid API key")
                    429 -> throw Exception("Too many requests")
                    else -> throw Exception("Safe Companion error: ${response.code()}")
                }
            }
        }
    }

    suspend fun clearSession(sessionId: String) = messageDao.clearSession(sessionId)
}
