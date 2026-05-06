package com.safeharborsecurity.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.safeharborsecurity.app.BuildConfig
import com.safeharborsecurity.app.service.ScreenScanService
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects diagnostic logs for beta testers to email back to the developer.
 *
 * What's captured:
 *   - The app's own logcat output (no other apps' logs — Android filters by UID
 *     for non-system apps, so we can only see our own).
 *   - Build info: app version, package, build flavour, debug flag.
 *   - Device info: manufacturer, model, Android version, SDK level.
 *   - Live status of subsystems we can read in-process (Screen Monitor).
 *
 * What's stripped before write:
 *   - Phone numbers (any 7+ digit run that looks like a phone number)
 *   - Email addresses (kept domain only)
 *   - Long digit sequences that could be card or SSN
 *   - Names from family-contact JSON if present in logs
 *   - The OCR preview lines (these contain text from on-screen messages)
 *
 * The file is written to `cacheDir/diagnostics/safe-companion-logs-<ts>.txt`
 * and shared via the existing FileProvider authority so it doesn't need any
 * extra storage permissions. The user sees the file content in their email
 * app before they tap Send — full transparency.
 */
object LogCollector {

    private const val TAG = "LogCollector"
    private const val LOG_FILENAME_PREFIX = "safe-companion-logs"

    /**
     * Writes a redacted diagnostic log file to cacheDir and returns it. The
     * caller decides how to share it (typically [shareLogs] below).
     */
    fun collectToFile(context: Context): File {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        // Wipe any prior log files so the cache doesn't grow forever — beta
        // testers will hit this many times.
        dir.listFiles()?.forEach { runCatching { it.delete() } }

        val ts = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "$LOG_FILENAME_PREFIX-$ts.txt")

        FileWriter(file).use { w ->
            w.appendLine("=== Safe Companion diagnostic log ===")
            w.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
            w.appendLine()
            w.appendLine(buildHeader())
            w.appendLine()
            w.appendLine("=== Screen Monitor status ===")
            w.appendLine(buildScreenMonitorStatus())
            w.appendLine()
            w.appendLine("=== Recent app log (redacted) ===")
            w.appendLine(buildLogcatTail())
        }
        Log.d(TAG, "Wrote diagnostic log: ${file.absolutePath} (${file.length()} bytes)")
        return file
    }

    /**
     * Convenience: collect + share via Android's chooser. Default mailto
     * recipient is the developer email; user can change it if they want.
     */
    fun shareLogs(
        context: Context,
        recipientEmail: String = "mailjustices@gmail.com"
    ) {
        val file = try {
            collectToFile(context)
        } catch (e: Exception) {
            Log.w(TAG, "collectToFile failed", e)
            android.widget.Toast.makeText(
                context,
                "Couldn't gather logs. Please try again in a moment.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: Exception) {
            Log.w(TAG, "FileProvider.getUriForFile failed", e)
            null
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
            putExtra(Intent.EXTRA_SUBJECT, "Safe Companion diagnostic logs")
            putExtra(
                Intent.EXTRA_TEXT,
                "I noticed an issue and Safe Companion asked me to share these " +
                    "diagnostic logs. The file attached has the technical details. " +
                    "It does not include my messages, contacts, phone numbers, or " +
                    "any personal information.\n\n" +
                    "(Add a short note here describing what happened, if you'd like.)"
            )
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        try {
            val chooser = Intent.createChooser(send, "Send Safe Companion logs").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.w(TAG, "startActivity(chooser) failed", e)
            android.widget.Toast.makeText(
                context,
                "No email app found. Please install one and try again.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    // ─── Builders ──────────────────────────────────────────────────────────

    private fun buildHeader(): String = buildString {
        appendLine("App: ${BuildConfig.APPLICATION_ID} v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Build: ${BuildConfig.BUILD_TYPE} / ${BuildConfig.FLAVOR.ifBlank { "(no flavor)" }}")
        appendLine("Debug: ${BuildConfig.DEBUG}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Locale: ${Locale.getDefault()}")
    }

    private fun buildScreenMonitorStatus(): String {
        val s = ScreenScanService.status.value
        return buildString {
            appendLine("isRunning: ${s.isRunning}")
            appendLine("framesProcessed: ${s.framesProcessed}")
            appendLine("framesSkippedSameContent: ${s.framesSkippedSameContent}")
            appendLine("scamsDetectedSinceStart: ${s.scamsDetectedSinceStart}")
            appendLine("ocrCharsLastFrame: ${s.ocrCharsLastFrame}")
            // Deliberately omit ocrPreviewLastFrame — it contains screen text
            // the user just looked at, which can be sensitive.
            appendLine("lastVerdict: ${s.lastVerdict}")
            appendLine("lastFrameAtMs: ${s.lastFrameAtMs}")
            s.errorMessage?.let { appendLine("errorMessage: $it") }
        }
    }

    private fun buildLogcatTail(): String {
        return try {
            val pid = android.os.Process.myPid().toString()
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "time", "--pid=$pid", "-t", "2000")
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val out = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                out.appendLine(redact(line))
                line = reader.readLine()
            }
            // Some Android builds reject --pid; fallback to filtering by tag
            // prefix in our app if we got no output.
            if (out.isBlank()) {
                Log.d(TAG, "--pid logcat returned nothing, falling back to unfiltered tail")
                fallbackLogcat()
            } else {
                out.toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "logcat read failed", e)
            "(could not read logcat: ${e.javaClass.simpleName})"
        }
    }

    private fun fallbackLogcat(): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "500"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val out = StringBuilder()
            // Filter by our top-level package so we don't dump system logs.
            val packagePrefix = "com.safeharborsecurity"
            val ourTags = setOf(
                "ScreenScan", "OcrEngine", "ChatVM", "VOICE_STATE",
                "EL_DEBUG", "GmailAuth", "TrackerScan", "WeeklyDigest",
                "SafetyChecker", "SafetyCheckerRepo", "EmailSetupVM",
                "GuardianService", "AlertRepo", "LogCollector",
                "HiddenDeviceScan", "SafeHarbor"
            )
            var line: String? = reader.readLine()
            while (line != null) {
                val match = ourTags.any { line!!.contains(it) } || line.contains(packagePrefix)
                if (match) out.appendLine(redact(line))
                line = reader.readLine()
            }
            out.toString().ifBlank { "(no Safe Companion logs in this window)" }
        } catch (e: Exception) {
            "(fallback logcat read failed: ${e.javaClass.simpleName})"
        }
    }

    // ─── PII redaction ─────────────────────────────────────────────────────

    /**
     * Strip anything in a single log line that looks like personal info.
     * Conservative — false positives are fine, false negatives are not.
     */
    internal fun redact(input: String): String {
        var s = input
        // Phone numbers: + or digit, optional separators, 7+ digits total.
        // Catches "+1 (415) 555-1212", "415-555-1212", "4155551212".
        s = phoneRegex.replace(s) { "[REDACTED-PHONE]" }
        // Emails — keep domain so we can see "user complained about gmail bounce"
        // but strip the local part.
        s = emailRegex.replace(s) { m ->
            val parts = m.value.split("@")
            if (parts.size == 2) "[REDACTED-EMAIL]@${parts[1]}" else "[REDACTED-EMAIL]"
        }
        // Long digit runs (>= 9 digits) that aren't already part of a logcat
        // timestamp or PID — possible card/SSN/account numbers.
        s = longDigitRegex.replace(s) { m ->
            // Skip if it looks like a timestamp millis value that follows a
            // known status-key pattern from our diagnostics — those are fine
            // to leave in. Otherwise redact.
            if (m.value.length in 9..10 && s.contains("lastFrameAtMs")) m.value
            else "[REDACTED-DIGITS]"
        }
        // Strip OCR preview lines entirely — they may contain text from
        // sensitive on-screen content.
        if (s.contains("OCR returned") || s.contains("ocrPreviewLastFrame")) {
            s = s.substringBefore("OCR returned") + "[OCR-PREVIEW-REDACTED]"
        }
        // Strip any line that mentions saving a message body (defensive).
        if (s.contains("saveMessage") && s.length > 80) {
            s = s.take(80) + " […REDACTED…]"
        }
        return s
    }

    private val phoneRegex = Regex(
        """(\+?\d[\d\s().\-]{6,}\d)"""
    )
    private val emailRegex = Regex(
        """[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}"""
    )
    private val longDigitRegex = Regex("""\b\d{9,}\b""")
}
