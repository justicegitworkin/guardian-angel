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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "EL_DEBUG"
private const val API_BASE = "https://api.elevenlabs.io/v1"

data class ElevenLabsVoiceConfig(
    val voiceId: String,
    val stability: Float = 0.75f,
    val similarityBoost: Float = 0.75f,
    val style: Float = 0.35f
)

@Singleton
class ElevenLabsTTSManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient
) {
    private var mediaPlayer: MediaPlayer? = null

    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true

    // Persona -> ElevenLabs voice mapping (verified voice IDs).
    //
    // James used to be "Josh" (TxGEqnHWrfWFTfGW9XjX), but ElevenLabs retired
    // that voice in 2024 and the API now returns an error for it — which is
    // why James was falling all the way through to Android system TTS and
    // sounding robotic. Reassigning to "Arnold" per user request (James is
    // now the default persona; user liked the previous George voice).
    //
    // George moved to "Daniel" (calm British male) so the two personas stay
    // audibly distinct in the picker.
    val voiceMap = mapOf(
        "GRACE" to ElevenLabsVoiceConfig("EXAVITQu4vr4xnSDxMaL", 0.75f, 0.75f, 0.35f),   // Sarah
        "JAMES" to ElevenLabsVoiceConfig("VR6AewLTigWG4xSOukaG", 0.85f, 0.75f, 0.20f),   // Arnold (was Josh — Josh retired)
        "SOPHIE" to ElevenLabsVoiceConfig("jBpfuIE2acCO8z3wKNLl", 0.65f, 0.75f, 0.50f),   // Gigi (most expressive)
        "GEORGE" to ElevenLabsVoiceConfig("onwK4e9ZLuTAKqWW03F9", 0.85f, 0.75f, 0.15f)    // Daniel (was Arnold — Arnold moved to James)
    )

    suspend fun speak(
        text: String,
        apiKey: String,
        personaId: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Check B — ElevenLabsTTSManager.speak() called.
            // Part B1: Do NOT log key prefixes/length — leaks key entropy in logcat.
            Log.d(TAG, "Check B: ElevenLabsTTSManager.speak() CALLED | persona=$personaId | keyPresent=${apiKey.isNotBlank()}")

            val config = voiceMap[personaId] ?: voiceMap["GRACE"]!!
            val cleanText = preprocessText(text)
            Log.d(TAG, "Check B: voiceId=${config.voiceId} | textLen=${cleanText.length}")

            val body = JSONObject().apply {
                put("text", cleanText)
                put("model_id", "eleven_turbo_v2")
                put("voice_settings", JSONObject().apply {
                    put("stability", config.stability)
                    put("similarity_boost", config.similarityBoost)
                    put("style", config.style)
                })
            }

            val url = "$API_BASE/text-to-speech/${config.voiceId}"
            Log.d(TAG, "Check D: HTTP POST to $url")

            val request = Request.Builder()
                .url(url)
                .addHeader("xi-api-key", apiKey.trim())
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "audio/mpeg")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            Log.d(TAG, "Check D: HTTP response code=${response.code} | isSuccessful=${response.isSuccessful} | bodyNull=${response.body == null}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                response.close()
                Log.e(TAG, "Check D: FAILED — code=${response.code} body=$errorBody")
                throw Exception("ElevenLabs API error ${response.code}: $errorBody")
            }

            // Save audio to temp file
            val audioFile = File(appContext.cacheDir, "elevenlabs_audio.mp3")
            response.body?.byteStream()?.use { input ->
                FileOutputStream(audioFile).use { output ->
                    input.copyTo(output)
                }
            }
            response.close()

            val fileSize = if (audioFile.exists()) audioFile.length() else 0L
            Log.d(TAG, "Check E: Audio file written | path=${audioFile.absolutePath} | size=${fileSize} bytes")

            if (!audioFile.exists() || fileSize == 0L) {
                Log.e(TAG, "Check E: FAILED — audio file is empty or missing")
                throw Exception("Audio file is empty")
            }

            Log.d(TAG, "Check E: Got ${fileSize} bytes — playing with MediaPlayer")

            // Play audio
            withContext(Dispatchers.Main) {
                onStart()
                playAudioFile(audioFile, onDone)
            }
        }
    }

    /** Source B + B2: Smooth volume fade in over 200ms, capped at 0.75 to reduce mic pickup. */
    private fun fadeIn(player: MediaPlayer, durationMs: Long = 200L) {
        player.setVolume(0f, 0f)
        val steps = 10
        val stepDuration = durationMs / steps
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        for (i in 1..steps) {
            handler.postDelayed({
                try {
                    val vol = (i.toFloat() / steps) * MAX_PLAYBACK_VOLUME
                    player.setVolume(vol, vol)
                } catch (_: Exception) {}
            }, stepDuration * i)
        }
    }

    private companion object {
        // B2: cap voice playback at 75% so the agent's audio is less likely to be picked
        // up by the mic during voice mode.
        const val MAX_PLAYBACK_VOLUME = 0.75f
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
                    setOnPreparedListener { mp ->
                        Log.d(TAG, "Check E: MediaPlayer PREPARED — starting playback")
                        // Source B: Fade in to avoid abrupt start
                        fadeIn(mp)
                        mp.start()
                    }
                    setOnCompletionListener {
                        Log.d(TAG, "Check E: MediaPlayer COMPLETED — ElevenLabs audio finished")
                        onDone()
                        if (cont.isActive) cont.resume(Unit)
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "Check E: MediaPlayer ERROR — what=$what extra=$extra")
                        onDone()
                        if (cont.isActive) cont.resume(Unit)
                        true
                    }
                    prepare()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Check E: MediaPlayer EXCEPTION", e)
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

    fun release() {
        stop()
    }

    private fun preprocessText(text: String): String {
        var result = text
        // Remove markdown — no regex, just simple string replacements
        result = result.replace("**", "")
        result = result.replace("__", "")
        result = result.replace("###", "")
        result = result.replace("##", "")
        result = result.replace("#", "")
        result = result.replace("`", "")
        // Remove bullet markers at start of lines
        result = result.replace("\n- ", "\n")
        result = result.replace("\n• ", "\n")
        result = result.replace("\n* ", "\n")
        // Abbreviations to spoken form
        result = result.replace("SMS", "text message")
        result = result.replace("URL", "web address")
        result = result.replace("e.g.", "for example")
        result = result.replace("i.e.", "that is")
        result = result.replace("etc.", "and so on")
        // Escape quotes for JSON safety
        result = result.replace("\"", "'")
        // Flatten newlines to spaces
        result = result.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
        // Collapse multiple spaces with a while loop
        while (result.contains("  ")) {
            result = result.replace("  ", " ")
        }
        return result.trim()
    }
}
