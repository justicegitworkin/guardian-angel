package com.safeharborsecurity.app.data.repository

import com.google.gson.Gson
import com.safeharborsecurity.app.data.local.dao.SafetyCheckResultDao
import com.safeharborsecurity.app.data.local.entity.SafetyCheckResultEntity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.safeharborsecurity.app.data.remote.ClaudeApiService
import java.io.ByteArrayOutputStream
import com.safeharborsecurity.app.data.remote.model.ClaudeMessage
import com.safeharborsecurity.app.data.remote.model.ClaudeRequest
import com.safeharborsecurity.app.data.remote.model.ImageSource
import com.safeharborsecurity.app.data.remote.model.MessageContentBlock
import com.safeharborsecurity.app.util.UrlResolverUtil
import com.safeharborsecurity.app.util.extractJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class AiImageAnalysis(
    val isLikelyAiGenerated: Boolean = false,
    val confidence: Float = 0f,
    val indicators: List<String> = emptyList(),
    val explanation: String = ""
)

data class SafetyVerdict(
    val verdict: String = "SAFE",         // SAFE | SUSPICIOUS | DANGEROUS
    val summary: String = "",
    val details: String = "",
    val whatToDoNext: String = "",
    val contentType: String = "TEXT",
    val containsText: Boolean = false,
    val textContent: String = "",
    val containsQrCode: Boolean = false,
    val aiImageAnalysis: AiImageAnalysis? = null
)

private const val SAFETY_SYSTEM_PROMPT = """You are Safe Companion, a scam detection AI protecting senior citizens. Analyze the content below for scam indicators, phishing, fraud, or safety risks. Respond ONLY with valid JSON."""

private const val TEXT_ANALYSIS_TEMPLATE = """Analyze this text/email for scam indicators:

---
%s
---

Respond with JSON only:
{
  "verdict": "SAFE" | "SUSPICIOUS" | "DANGEROUS",
  "summary": "one plain-English sentence for a senior",
  "details": "what specifically was found (2-3 sentences)",
  "what_to_do_next": "clear action the senior should take"
}

Scam indicators: urgency/threats, requests for personal info or money, suspicious links, impersonating government/banks, prize/lottery claims, gift card requests, unusual grammar, too-good-to-be-true offers."""

private const val IMAGE_ANALYSIS_PROMPT = """Analyze this image for scam indicators, phishing, fraud, or anything suspicious. This photo was taken by or shared with a senior citizen who wants to know if what they see is safe.

Also determine if this image appears to be AI-generated or computer-created.

Respond with JSON only:
{
  "verdict": "SAFE" | "SUSPICIOUS" | "DANGEROUS",
  "summary": "one plain-English sentence for a senior",
  "details": "what specifically was found (2-3 sentences)",
  "what_to_do_next": "clear action the senior should take",
  "contains_text": true/false,
  "text_content": "any text visible in the image, or empty string",
  "contains_qr_code": true/false,
  "ai_image_analysis": {
    "is_likely_ai_generated": true/false,
    "confidence": 0.0-1.0,
    "indicators": ["list of specific indicators found"],
    "explanation": "one plain-English sentence explaining why this looks real or AI-generated"
  }
}

Scam indicators: fake emails/letters, phishing websites on screens, gift card requests, fake prize/lottery notices, suspicious QR codes, impersonation of banks/government, unusual payment requests, forged documents.

AI detection indicators: distorted hands/fingers/faces, inconsistent lighting/shadows, background incoherence, garbled/nonsensical text in image, AI watermarks, plastic-looking skin, unnatural eyes, distorted geometry, too-perfect symmetry."""

private const val URL_ANALYSIS_TEMPLATE = """Analyze this URL for safety:

Original URL: %s
Final URL after redirects: %s
Redirect count: %d
Page title: %s
Domain: %s

Important context: jw.org is the official Jehovah's Witnesses website and is completely safe and legitimate.

Respond with JSON only:
{
  "verdict": "SAFE" | "SUSPICIOUS" | "DANGEROUS",
  "summary": "one plain-English sentence for a senior",
  "details": "what specifically was found (2-3 sentences)",
  "what_to_do_next": "clear action the senior should take"
}

Check for: phishing domains, URL shortener chains, domain impersonation (typosquatting), known scam domains, suspicious redirects, domain legitimacy, whether this is a known safe site, and what category the site falls into (news, shopping, banking, social media, religious, government, etc)."""

@Singleton
class SafetyCheckerRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val claudeApi: ClaudeApiService,
    private val safetyCheckResultDao: SafetyCheckResultDao,
    private val urlResolver: UrlResolverUtil,
    private val gson: Gson
) {
    fun getHistory(): Flow<List<SafetyCheckResultEntity>> = safetyCheckResultDao.getAll()
    fun getRecentHistory(limit: Int = 20): Flow<List<SafetyCheckResultEntity>> = safetyCheckResultDao.getRecent(limit)

    suspend fun analyzeText(apiKey: String, text: String): Result<SafetyVerdict> {
        return runCatching {
            val prompt = TEXT_ANALYSIS_TEMPLATE.format(text)
            val request = ClaudeRequest(
                messages = listOf(ClaudeMessage(role = "user", content = prompt)),
                system = SAFETY_SYSTEM_PROMPT,
                maxTokens = 512
            )
            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            if (!response.isSuccessful) throw Exception("API error: ${response.code()}")

            val rawText = response.body()?.text ?: throw Exception("Empty response")
            val verdict = gson.fromJson(extractJson(rawText), SafetyVerdictJson::class.java)

            val result = SafetyVerdict(
                verdict = verdict.verdict.uppercase(),
                summary = verdict.summary,
                details = verdict.details,
                whatToDoNext = verdict.whatToDoNext,
                contentType = "TEXT"
            )

            // Save to history
            safetyCheckResultDao.insert(
                SafetyCheckResultEntity(
                    contentType = "TEXT",
                    contentPreview = text.take(200),
                    verdict = result.verdict,
                    summary = result.summary,
                    detailJson = gson.toJson(result)
                )
            )
            result
        }
    }

    suspend fun analyzeUrl(apiKey: String, url: String): Result<SafetyVerdict> {
        return runCatching {
            // Normalise first
            val normalised = urlResolver.normaliseUrl(url) ?: url

            // Attempt resolution — failure is NOT fatal, we fall back to the raw URL
            val resolved = urlResolver.resolve(normalised)

            val originalUrl = resolved?.originalUrl ?: normalised
            val finalUrl = resolved?.finalUrl ?: normalised
            val redirectCount = resolved?.redirectCount ?: 0
            val pageTitle = resolved?.pageTitle ?: "unknown"
            val domain = resolved?.domain ?: try {
                java.net.URI(normalised).host ?: normalised
            } catch (_: Exception) { normalised }

            android.util.Log.d("SafetyChecker", "URL analysis: original=$originalUrl final=$finalUrl domain=$domain resolved=${resolved != null}")

            val prompt = URL_ANALYSIS_TEMPLATE.format(
                originalUrl,
                finalUrl,
                redirectCount,
                pageTitle,
                domain
            )
            val request = ClaudeRequest(
                messages = listOf(ClaudeMessage(role = "user", content = prompt)),
                system = SAFETY_SYSTEM_PROMPT,
                maxTokens = 512
            )
            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            if (!response.isSuccessful) throw Exception("API error: ${response.code()}")

            val rawText = response.body()?.text ?: throw Exception("Empty response")
            android.util.Log.d("SafetyChecker", "Claude URL response: $rawText")

            val verdict = gson.fromJson(extractJson(rawText), SafetyVerdictJson::class.java)

            val result = SafetyVerdict(
                verdict = verdict.verdict.uppercase(),
                summary = verdict.summary,
                details = verdict.details,
                whatToDoNext = verdict.whatToDoNext,
                contentType = "URL"
            )

            safetyCheckResultDao.insert(
                SafetyCheckResultEntity(
                    contentType = "URL",
                    contentPreview = url.take(200),
                    verdict = result.verdict,
                    summary = result.summary,
                    detailJson = gson.toJson(result)
                )
            )
            result
        }
    }

    suspend fun analyzeImage(apiKey: String, imageUri: Uri): Result<SafetyVerdict> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Starting image analysis for URI: $imageUri | scheme=${imageUri.scheme} | authority=${imageUri.authority}")

            // Diagnostic: check MIME type
            val mimeType = appContext.contentResolver.getType(imageUri)
            Log.d(TAG, "URI MIME type: $mimeType")

            // --- Two-method image loading with MediaStore fallback ---
            val bitmap = loadBitmapFromUri(imageUri)
                ?: throw Exception("Could not read photo")

            Log.d(TAG, "Decoded bitmap: ${bitmap.width}x${bitmap.height}")

            // Compress as JPEG and base64-encode (always JPEG regardless of source format)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            bitmap.recycle()

            val jpegBytes = outputStream.toByteArray()
            if (jpegBytes.isEmpty()) {
                throw Exception("Photo appears to be empty")
            }

            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            Log.d(TAG, "Base64 length: ${base64.length} chars (~${jpegBytes.size / 1024}KB)")

            // Send to Claude as image/jpeg
            val contentBlocks = listOf(
                MessageContentBlock(
                    type = "image",
                    source = ImageSource(mediaType = "image/jpeg", data = base64)
                ),
                MessageContentBlock(
                    type = "text",
                    text = IMAGE_ANALYSIS_PROMPT
                )
            )

            val request = ClaudeRequest(
                messages = listOf(ClaudeMessage(role = "user", content = contentBlocks)),
                system = SAFETY_SYSTEM_PROMPT,
                maxTokens = 1024
            )
            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            if (!response.isSuccessful) {
                val code = response.code()
                val errBody = try { response.errorBody()?.string()?.take(300) } catch (_: Exception) { null }
                Log.w(TAG, "Image analysis HTTP $code: $errBody")
                // Specific exception messages so the ViewModel can show
                // actionable copy instead of a generic "try again later".
                throw Exception(when (code) {
                    401 -> "API key invalid or rejected"
                    403 -> "API key lacks permission for image analysis"
                    413 -> "Photo too large to analyse"
                    429 -> "Rate limit hit — try again in a minute"
                    in 500..599 -> "Claude is having a problem right now"
                    else -> "API error: $code"
                })
            }

            val rawText = response.body()?.text ?: throw Exception("Empty response")
            val verdict = gson.fromJson(extractJson(rawText), SafetyVerdictJson::class.java)

            val aiAnalysis = verdict.ai_image_analysis?.let {
                AiImageAnalysis(
                    isLikelyAiGenerated = it.is_likely_ai_generated,
                    confidence = it.confidence,
                    indicators = it.indicators,
                    explanation = it.explanation
                )
            }

            val result = SafetyVerdict(
                verdict = verdict.verdict.uppercase(),
                summary = verdict.summary,
                details = verdict.details,
                whatToDoNext = verdict.whatToDoNext,
                contentType = "IMAGE",
                containsText = verdict.contains_text,
                textContent = verdict.text_content,
                containsQrCode = verdict.contains_qr_code,
                aiImageAnalysis = aiAnalysis
            )

            safetyCheckResultDao.insert(
                SafetyCheckResultEntity(
                    contentType = "IMAGE",
                    contentPreview = "Photo check",
                    verdict = result.verdict,
                    summary = result.summary,
                    detailJson = gson.toJson(result)
                )
            )
            result
        }
    }

    /**
     * Two-method image loading: try ContentResolver.openInputStream first,
     * fall back to MediaStore.getBitmap for older/special URIs.
     * Both methods handle HEIC, WebP, and all standard formats via BitmapFactory.
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        // Method 1: ContentResolver with inSampleSize for memory efficiency
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            Log.d(TAG, "Method 1 bounds: ${origWidth}x${origHeight}")

            if (origWidth > 0 && origHeight > 0) {
                val maxDim = 1024
                var sampleSize = 1
                if (origWidth > maxDim || origHeight > maxDim) {
                    val hw = origWidth / 2
                    val hh = origHeight / 2
                    while ((hw / sampleSize) >= maxDim || (hh / sampleSize) >= maxDim) {
                        sampleSize *= 2
                    }
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = appContext.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                }
                if (bitmap != null) {
                    Log.d(TAG, "Method 1 success: ${bitmap.width}x${bitmap.height}, sampleSize=$sampleSize")
                    return bitmap
                }
            }
            Log.w(TAG, "Method 1 failed: bounds=${origWidth}x${origHeight}")
        } catch (e: Exception) {
            Log.w(TAG, "Method 1 exception: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Method 2: MediaStore fallback (handles some content:// URIs that openInputStream misses)
        try {
            @Suppress("DEPRECATION")
            val bitmap = MediaStore.Images.Media.getBitmap(appContext.contentResolver, uri)
            if (bitmap != null) {
                Log.d(TAG, "Method 2 (MediaStore) success: ${bitmap.width}x${bitmap.height}")
                // Scale down if too large
                if (bitmap.width > 1024 || bitmap.height > 1024) {
                    val scale = 1024f / maxOf(bitmap.width, bitmap.height)
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt(),
                        (bitmap.height * scale).toInt(),
                        true
                    )
                    bitmap.recycle()
                    return scaled
                }
                return bitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "Method 2 (MediaStore) exception: ${e.javaClass.simpleName}: ${e.message}")
        }

        Log.e(TAG, "Both image loading methods failed for URI: $uri")
        return null
    }

    companion object {
        private const val TAG = "SafetyChecker"
    }

    suspend fun deleteResult(id: Long) = safetyCheckResultDao.deleteById(id)
}

private data class SafetyVerdictJson(
    val verdict: String = "SAFE",
    val summary: String = "",
    val details: String = "",
    val what_to_do_next: String = "",
    val contains_text: Boolean = false,
    val text_content: String = "",
    val contains_qr_code: Boolean = false,
    val ai_image_analysis: AiImageAnalysisJson? = null
) {
    val whatToDoNext: String get() = what_to_do_next
}

private data class AiImageAnalysisJson(
    val is_likely_ai_generated: Boolean = false,
    val confidence: Float = 0f,
    val indicators: List<String> = emptyList(),
    val explanation: String = ""
)
