package com.safeharborsecurity.app.service

import android.content.Context
import android.media.AudioManager
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import com.safeharborsecurity.app.data.datastore.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature 52: Announces incoming calls from saved contacts by name.
 * Uses Android TTS on STREAM_RING so it plays at ringtone volume.
 */
@Singleton
class TrustedCallerAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }
    }

    suspend fun announceCallerIfKnown(phoneNumber: String) {
        val enabled = userPreferences.isCallerAnnouncementsEnabled.firstOrNull() ?: true
        if (!enabled) return

        val contactName = lookupContactName(phoneNumber) ?: return

        if (!ttsReady) initialize()

        val params = android.os.Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_RING)
        }
        tts?.speak(
            "Call from $contactName",
            TextToSpeech.QUEUE_FLUSH,
            params,
            "caller_announce"
        )
    }

    private fun lookupContactName(phoneNumber: String): String? {
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(phoneNumber)
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
