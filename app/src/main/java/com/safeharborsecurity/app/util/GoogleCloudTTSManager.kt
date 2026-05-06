package com.safeharborsecurity.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "GoogleCloudTTS"
private const val API_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"

data class GoogleNeuralVoiceConfig(
    val name: String,
    val languageCode: String,
    val speakingRate: Double = 0.85,
    val pitch: Double = -1.0
)

@Singleton
class GoogleCloudTTSManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient
) {
    private var mediaPlayer: MediaPlayer? = null

    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true

    val voiceMap = mapOf(
        "GRACE" to GoogleNeuralVoiceConfig("en-US-Neural2-F", "en-US", 0.85, -1.0),
        "JAMES" to GoogleNeuralVoiceConfig("en-US-Neural2-D", "en-US", 0.87, -2.0),
        "SOPHIE" to GoogleNeuralVoiceConfig("en-GB-Neural2-A", "en-GB", 0.90, 0.0),
        "GEORGE" to GoogleNeuralVoiceConfig("en-GB-Neural2-B", "en-GB", 0.75, -3.0)
    )

    suspend fun speak(
        text: String,
        apiKey: String,
        personaId: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val config = voiceMap[personaId] ?: voiceMap["GRACE"]!!
            val ssml = textToSsml(text)

            val body = JSONObject().apply {
                put("input", JSONObject().put("ssml", ssml))
                put("voice", JSONObject().apply {
                    put("languageCode", config.languageCode)
                    put("name", config.name)
                })
                put("audioConfig", JSONObject().apply {
                    put("audioEncoding", "MP3")
                    put("speakingRate", config.speakingRate)
                    put("pitch", config.pitch)
                    put("effectsProfileId", JSONArray().put("handset-class-device"))
                })
            }

            val request = Request.Builder()
                .url("$API_URL?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                response.close()
                throw Exception("Google TTS API error ${response.code}: $errorBody")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            response.close()

            val audioContent = JSONObject(responseBody).getString("audioContent")
            val audioBytes = android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT)

            val audioFile = File(appContext.cacheDir, "google_tts_audio.mp3")
            FileOutputStream(audioFile).use { it.write(audioBytes) }

            withContext(Dispatchers.Main) {
                onStart()
                playAudioFile(audioFile, onDone)
            }
        }
    }

    /** Source B: Smooth volume fade in over 200ms */
    private fun fadeIn(player: MediaPlayer, durationMs: Long = 200L) {
        player.setVolume(0f, 0f)
        val steps = 10
        val stepDuration = durationMs / steps
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        for (i in 1..steps) {
            handler.postDelayed({
                try {
                    val vol = i.toFloat() / steps
                    player.setVolume(vol, vol)
                } catch (_: Exception) {}
            }, stepDuration * i)
        }
    }

    private suspend fun playAudioFile(file: File, onDone: () -> Unit) {
        stop()
        suspendCancellableCoroutine { cont ->
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        onDone()
                        if (cont.isActive) cont.resume(Unit)
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                        onDone()
                        if (cont.isActive) cont.resume(Unit)
                        true
                    }
                    prepare()
                    // Source B: Fade in to avoid abrupt start
                    fadeIn(this)
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play audio", e)
                onDone()
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { stop() }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun release() { stop() }

    private fun textToSsml(text: String): String {
        val cleaned = text
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("\\[(.+?)]\\(.*?\\)"), "$1")
            .replace(Regex("#{1,6}\\s*"), "")
            .replace("SMS", "text message")
            .replace("URL", "website address")
            .replace(Regex("[\\u2600-\\u27BF\\u{1F000}-\\u{1FFFF}]"), "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .trim()

        // Add SSML breaks for natural pauses
        val withPauses = cleaned
            .replace(". ", ".<break time=\"400ms\"/> ")
            .replace("? ", "?<break time=\"400ms\"/> ")
            .replace("! ", "!<break time=\"400ms\"/> ")
            .replace(", ", ",<break time=\"150ms\"/> ")

        return "<speak>$withPauses</speak>"
    }
}
