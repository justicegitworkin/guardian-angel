package com.guardianangel.app.data.ai

import android.content.Context
import android.util.Log
import com.guardianangel.app.BuildConfig
import com.guardianangel.app.data.remote.model.SmsAnalysisResult
import com.guardianangel.app.util.extractJson
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Google's Gemini Nano on-device AI (play-services-mlkit-genai).
 *
 * Device requirements: Pixel 8 / 8a / 8 Pro or newer with Android 14+
 * and the AI Core module installed via Play Store.
 *
 * Falls back gracefully — callers receive null and should fall back to
 * the Claude cloud API in that case.
 */
@Singleton
class OnDeviceAIService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val tag = "OnDeviceAI"

    enum class DeviceStatus { CHECKING, SUPPORTED, NOT_SUPPORTED }

    @Volatile private var deviceStatus: DeviceStatus = DeviceStatus.CHECKING

    // Lazily initialised — avoids hard crash on incompatible devices
    private var inferenceSession: Any? = null

    /**
     * Returns the current device compatibility status.
     * Must be called from a coroutine (checks Play Services on IO thread).
     */
    suspend fun checkCompatibility(): DeviceStatus = withContext(Dispatchers.IO) {
        if (deviceStatus != DeviceStatus.CHECKING) return@withContext deviceStatus
        deviceStatus = try {
            val featureClass = Class.forName(
                "com.google.android.gms.mlkit.genai.common.FeatureStatus"
            )
            val clientClass = Class.forName(
                "com.google.android.gms.mlkit.genai.text.TextGeneration"
            )
            val getClient = clientClass.getMethod("getClient", Context::class.java)
            val client = getClient.invoke(null, context)
            val isAvailable = client?.javaClass
                ?.getMethod("isFeatureAvailable")
                ?.invoke(client)

            // isFeatureAvailable returns a Task<FeatureStatus>; check synchronously
            // via reflection on the result object
            if (BuildConfig.DEBUG) Log.d(tag, "Feature check result: $isAvailable")

            // If we reach here without an exception the library is present
            DeviceStatus.SUPPORTED
        } catch (e: ClassNotFoundException) {
            if (BuildConfig.DEBUG) Log.d(tag, "Gemini Nano not available: ${e.message}")
            DeviceStatus.NOT_SUPPORTED
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(tag, "Gemini Nano check failed: ${e.message}")
            DeviceStatus.NOT_SUPPORTED
        }
        deviceStatus
    }

    /**
     * Analyzes a message for scam risk using on-device Gemini Nano.
     * Returns null if the device is incompatible or if inference fails.
     */
    suspend fun analyzeTextForScam(text: String): SmsAnalysisResult? = withContext(Dispatchers.IO) {
        if (deviceStatus == DeviceStatus.NOT_SUPPORTED) return@withContext null
        try {
            val prompt = """You are a scam detector. Analyze this message and respond with JSON only:
{"risk_level":"SAFE|WARNING|SCAM","confidence":0.0-1.0,"reason":"one sentence","action":"what to do"}
Message: $text"""

            val rawResponse = runInference(prompt) ?: return@withContext null
            gson.fromJson(extractJson(rawResponse), SmsAnalysisResult::class.java)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "On-device scam analysis failed: ${e.message}")
            null
        }
    }

    /**
     * Sends a chat message to the on-device model.
     * Returns null if unavailable.
     */
    suspend fun chat(message: String, conversationContext: String): String? =
        withContext(Dispatchers.IO) {
            if (deviceStatus == DeviceStatus.NOT_SUPPORTED) return@withContext null
            try {
                val prompt = buildString {
                    append("You are Guardian Angel, a warm AI companion for a senior citizen.\n")
                    if (conversationContext.isNotBlank()) {
                        append("Recent conversation:\n$conversationContext\n\n")
                    }
                    append("User: $message\nGuardian Angel:")
                }
                runInference(prompt)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(tag, "On-device chat failed: ${e.message}")
                null
            }
        }

    /**
     * Low-level inference call via reflection.
     * Uses the InferenceSession / Prompt API from play-services-mlkit-genai.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun runInference(prompt: String): String? {
        return try {
            val session = getOrCreateSession() ?: return null
            // Call session.runInference(prompt) via reflection
            val runMethod = session.javaClass.getMethod("runInference", String::class.java)
            runMethod.invoke(session, prompt) as? String
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "runInference error: ${e.message}")
            null
        }
    }

    private fun getOrCreateSession(): Any? {
        if (inferenceSession != null) return inferenceSession
        return try {
            val clientClass = Class.forName(
                "com.google.android.gms.mlkit.genai.text.TextGeneration"
            )
            val getClient = clientClass.getMethod("getClient", Context::class.java)
            val client = getClient.invoke(null, context)
            val createSession = client?.javaClass?.getMethod("createSession")
            inferenceSession = createSession?.invoke(client)
            inferenceSession
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(tag, "Session creation failed: ${e.message}")
            deviceStatus = DeviceStatus.NOT_SUPPORTED
            null
        }
    }

    fun close() {
        try {
            inferenceSession?.javaClass?.getMethod("close")?.invoke(inferenceSession)
        } catch (_: Exception) {}
        inferenceSession = null
    }
}
