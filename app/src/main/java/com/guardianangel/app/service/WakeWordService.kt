package com.guardianangel.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.guardianangel.app.MainActivity
import com.guardianangel.app.R
import com.guardianangel.app.data.datastore.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WakeWordService : Service() {

    @Inject lateinit var prefs: UserPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var porcupine: ai.picovoice.porcupine.Porcupine? = null
    private var audioRecord: AudioRecord? = null
    private var tts: TextToSpeech? = null

    companion object {
        private const val TAG = "WakeWordService"
        private const val NOTIF_CHANNEL_ID = "wake_word_channel"
        private const val NOTIF_ID = 1002
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        scope.launch { startWakeWordLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        stopAudioCapture()
        porcupine?.delete()
        porcupine = null
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private suspend fun startWakeWordLoop() {
        val accessKey = prefs.porcupineAccessKey.first().trim()
        if (accessKey.isEmpty()) {
            Log.w(TAG, "No Porcupine access key set — wake word service idle.")
            return
        }

        try {
            porcupine = buildPorcupine(accessKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Porcupine: ${e.message}")
            return
        }

        val p = porcupine ?: return
        val frameLength = p.frameLength
        val sampleRate = p.sampleRate

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(frameLength * 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord = record
        record.startRecording()

        val frame = ShortArray(frameLength)
        try {
            while (scope.isActive) {
                val read = record.read(frame, 0, frame.size)
                if (read == frame.size) {
                    val result = p.process(frame)
                    if (result >= 0) {
                        withContext(Dispatchers.Main) { onWakeWordDetected() }
                        delay(4_000)
                    }
                }
            }
        } finally {
            stopAudioCapture()
        }
    }

    private fun buildPorcupine(accessKey: String): ai.picovoice.porcupine.Porcupine {
        val ppnAsset = "hey-guardian_en_android.ppn"
        val assets = applicationContext.assets.list("") ?: emptyArray()

        return if (ppnAsset in assets) {
            ai.picovoice.porcupine.Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath("$ppnAsset")
                .setSensitivity(0.7f)
                .build(applicationContext)
        } else {
            Log.w(TAG, "Custom .ppn not found — falling back to built-in keyword 'PORCUPINE'")
            ai.picovoice.porcupine.Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeyword(ai.picovoice.porcupine.Porcupine.BuiltInKeyword.PORCUPINE)
                .setSensitivity(0.7f)
                .build(applicationContext)
        }
    }

    private fun onWakeWordDetected() {
        Log.d(TAG, "Wake word detected!")
        tts?.speak(
            "Guardian Angel here, how can I help?",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "wake_response"
        )
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("guardianangel://open?context=")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(launchIntent)
    }

    private fun stopAudioCapture() {
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.wake_word_notif_title))
            .setContentText(getString(R.string.wake_word_notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            getString(R.string.channel_wake_word_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_wake_word_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
