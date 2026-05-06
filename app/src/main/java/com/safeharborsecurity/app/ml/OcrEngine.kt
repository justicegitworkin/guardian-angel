package com.safeharborsecurity.app.ml

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Item 2 — On-device text recognition. Wraps ML Kit's Latin text recognizer
 * (a downloadable on-device model, ~10 MB, fully offline after first install).
 *
 * We deliberately use the bundled latin-only model to keep APK size down and
 * because Safe Companion's primary audience is English-speaking older adults.
 * If you need Cyrillic / Devanagari / etc. later, add the corresponding
 * recognizer artifacts (com.google.mlkit:text-recognition-{chinese,korean,...}).
 */
@Singleton
class OcrEngine @Inject constructor() {

    companion object {
        private const val TAG = "OcrEngine"
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extract every line of text the model can find on the bitmap, joined by
     * newlines. Returns empty string on failure rather than throwing — OCR
     * misses are routine and we don't want them to crash the scan loop.
     *
     * The bitmap is NOT recycled here. The caller owns the bitmap lifecycle.
     */
    suspend fun extractText(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result.text)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "OCR failed: ${e.message}")
                if (cont.isActive) cont.resume("")
            }
    }

    fun release() {
        try { recognizer.close() } catch (_: Exception) {}
    }
}
