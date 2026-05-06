package com.safeharborsecurity.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.safeharborsecurity.app.MainActivity
import com.safeharborsecurity.app.R
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.data.repository.AlertRepository
import com.safeharborsecurity.app.ml.OcrEngine
import com.safeharborsecurity.app.ml.OnDeviceScamClassifier
import com.safeharborsecurity.app.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Item 2 — Screen-monitor foreground service.
 *
 * Holds the MediaProjection token granted by the user via the standard system
 * consent dialog ("Safe Companion will start capturing what's displayed on
 * your screen"). On a low-frequency timer (every CAPTURE_INTERVAL_MS) it grabs
 * one frame, runs ML Kit on-device OCR over it, throws the bitmap away, and
 * pipes the extracted text through OnDeviceScamClassifier.
 *
 * Why this exists: it replaces both NotificationListener-based SMS scanning
 * and UsageStats-based payment-app detection. Both of those require Android
 * "restricted setting" permissions that are intimidating for older users.
 * MediaProjection's consent dialog is friendly and well-understood.
 *
 * Privacy story: the bitmap is recycled immediately after OCR completes. Only
 * the extracted text + the classification verdict survive in Room. No image
 * data is ever persisted, transmitted, or written to disk.
 */
@AndroidEntryPoint
class ScreenScanService : Service() {

    @Inject lateinit var ocrEngine: OcrEngine
    @Inject lateinit var classifier: OnDeviceScamClassifier
    @Inject lateinit var alertRepository: AlertRepository
    @Inject lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    // Don't re-alert on the same screen content over and over.
    private var lastClassifiedText: String = ""
    private var lastPaymentAlertTimeMs: Long = 0L

    // Cheap pixel-grid fingerprint of the previous capture. Lets us skip the
    // expensive OCR step when the screen hasn't visibly changed since the
    // last sample (e.g., user is just reading the same screen). This is
    // typically the dominant resource saving in real use — far bigger than
    // bumping CAPTURE_INTERVAL_MS would be.
    private var lastFrameFingerprint: Long = 0L

    companion object {
        private const val TAG = "ScreenScan"
        const val CHANNEL_SCREEN_SCAN = "screen_scan_service"
        const val NOTIFICATION_ID = 7778

        const val ACTION_START = "com.safeharborsecurity.app.SCREEN_SCAN_START"
        const val ACTION_STOP = "com.safeharborsecurity.app.SCREEN_SCAN_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        /**
         * Live status the diagnostic UI in Settings reads. Updated from this
         * service on every frame so users (and us during testing) can see
         * exactly where the pipeline is — running, last OCR text, last
         * classifier verdict, count of scams fired. Fixes the "I enabled it
         * but it doesn't seem to do anything" debugging gap.
         */
        data class Status(
            val isRunning: Boolean = false,
            val lastFrameAtMs: Long = 0L,
            val framesProcessed: Int = 0,
            val framesSkippedSameContent: Int = 0,
            val ocrCharsLastFrame: Int = 0,
            val ocrPreviewLastFrame: String = "",
            val lastVerdict: String = "",
            val scamsDetectedSinceStart: Int = 0,
            val errorMessage: String? = null
        )

        private val _status = kotlinx.coroutines.flow.MutableStateFlow(Status())
        val status: kotlinx.coroutines.flow.StateFlow<Status> = _status

        internal fun publishStatus(update: (Status) -> Status) {
            _status.value = update(_status.value)
        }

        /**
         * True while *any* Safe Companion activity is in the foreground.
         * Set by SafeHarborApp's ProcessLifecycleOwner observer. Read here
         * to skip OCR entirely while we're looking at our own UI — testers
         * reported that chatting with Grace about scam patterns triggered
         * the screen-monitor alert because the OCR'd chat content contained
         * scam keywords. Frames captured while our own app is foregrounded
         * are now dropped before they reach the classifier.
         */
        @Volatile var ourAppForegrounded: Boolean = false
            internal set

        // 8 seconds is the sweet spot: a reading user typically spends 5-30s
        // on a single SMS or payment-app screen, so 8s catches the moment
        // they're paying attention. Shorter intervals (4s) burn extra CPU
        // unnecessarily; longer intervals (30s+) miss fast interactions where
        // the scam window closes before we ever sample. The bigger win comes
        // from the pixel-fingerprint short-circuit below — most captures
        // during a reading session show an unchanged screen and skip OCR
        // entirely.
        private const val CAPTURE_INTERVAL_MS = 8_000L

        // After a payment-app reminder fires, suppress further reminders for
        // 30 minutes so the user isn't nagged the whole time they're using
        // Venmo, Cash App, etc.
        private const val PAYMENT_COOLDOWN_MS = 30L * 60_000L

        fun startWithProjection(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenScanService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenScanService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundWithType()
    }

    /**
     * Android 14+ requires startForeground() to be called with an explicit
     * foregroundServiceType matching the manifest declaration. The 2-arg form
     * silently fails (or throws ForegroundServiceTypeNotAllowedException on
     * some OEMs), which manifests as "the user grants MediaProjection but
     * nothing actually happens" — exactly what the test scam-SMS bug looks
     * like.
     */
    private fun startForegroundWithType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "ScreenScanService entered foreground (sdk=${Build.VERSION.SDK_INT})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stop requested (user tapped 'Turn Off' or dismissed)")
                // Clear the runtime flag the onboarding screen reads, so the
                // step shows as "needs to be re-granted" if the user reopens
                // setup. The SharedPreferences mirror is the source of truth
                // for whether the screen monitor is active.
                getSharedPreferences("safeharbor_runtime", 0)
                    .edit().putBoolean("screen_monitor_active", false).apply()
                tearDownCapture()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (data != null) {
                    setupCapture(resultCode, data)
                } else {
                    Log.w(TAG, "Start intent missing projection data — stopping")
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun setupCapture(resultCode: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            mpm.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.w(TAG, "getMediaProjection threw — user revoked or stale token", e)
            stopSelf()
            return
        }
        if (projection == null) {
            Log.w(TAG, "getMediaProjection returned null")
            stopSelf()
            return
        }
        mediaProjection = projection

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped — tearing down")
                tearDownCapture()
                stopSelf()
            }
        }, null)

        val (width, height, density) = resolveDisplaySize()
        // Half resolution: OCR doesn't need pixel-perfect input and this halves
        // both memory and processing time per frame.
        val captureWidth = width / 2
        val captureHeight = height / 2

        imageReader = ImageReader.newInstance(
            captureWidth, captureHeight, PixelFormat.RGBA_8888, 2
        )
        virtualDisplay = projection.createVirtualDisplay(
            "SafeCompanionScan",
            captureWidth, captureHeight, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, null
        )

        captureJob?.cancel()
        captureJob = scope.launch {
            // Brief warmup so the VirtualDisplay has a frame to deliver.
            delay(800)
            publishStatus { it.copy(isRunning = true, errorMessage = null) }
            while (isActive) {
                runCatching { processOneFrame() }
                    .onFailure {
                        Log.w(TAG, "Frame processing error", it)
                        publishStatus { st -> st.copy(errorMessage = it.message) }
                    }
                delay(CAPTURE_INTERVAL_MS)
            }
        }
        Log.d(TAG, "Capture loop started (${captureWidth}x${captureHeight})")
    }

    private fun resolveDisplaySize(): Triple<Int, Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
    }

    private suspend fun processOneFrame() {
        // Don't scan when Safe Companion itself is the foreground app —
        // otherwise we OCR our own UI (chat with Grace, alert detail
        // screens, sample alerts) and trigger nested scam alerts on
        // text we put on the screen ourselves. Drain the latest frame
        // so the ImageReader doesn't back up, but skip everything else.
        if (ourAppForegrounded) {
            try { imageReader?.acquireLatestImage()?.close() } catch (_: Exception) {}
            return
        }

        val image = imageReader?.acquireLatestImage() ?: return
        var bitmap: Bitmap? = null
        try {
            bitmap = imageToBitmap(image)
        } finally {
            try { image.close() } catch (_: Exception) {}
        }
        if (bitmap == null) return

        // Fast change-detection: sample 100 pixels in a 10×10 grid and hash
        // them. If the fingerprint matches the previous frame, the screen is
        // visibly the same and we can skip the (expensive) OCR step. Costs
        // microseconds, saves ~250ms of CPU per skipped frame. In typical
        // reading sessions this short-circuits 80%+ of captures.
        val fingerprint = bitmapFingerprint(bitmap)
        if (fingerprint == lastFrameFingerprint && lastFrameFingerprint != 0L) {
            try { bitmap.recycle() } catch (_: Exception) {}
            publishStatus { it.copy(framesSkippedSameContent = it.framesSkippedSameContent + 1) }
            return
        }
        lastFrameFingerprint = fingerprint

        val ocrText = try {
            ocrEngine.extractText(bitmap)
        } finally {
            // Privacy guarantee: throw the picture away the moment we're done.
            try { bitmap.recycle() } catch (_: Exception) {}
        }

        Log.d(TAG, "OCR returned ${ocrText.length} chars: \"${ocrText.take(80).replace("\n", " ")}…\"")
        publishStatus {
            it.copy(
                lastFrameAtMs = System.currentTimeMillis(),
                framesProcessed = it.framesProcessed + 1,
                ocrCharsLastFrame = ocrText.length,
                ocrPreviewLastFrame = ocrText.take(120).replace("\n", " ")
            )
        }
        if (ocrText.length < 20) return  // not enough content to be a message
        if (ocrText == lastClassifiedText) return  // unchanged screen — skip

        // Avoid feeding our own UI back into the classifier. Safe Companion's
        // own copy uses these phrases prominently.
        if (looksLikeOurOwnUi(ocrText)) {
            lastClassifiedText = ocrText
            return
        }

        lastClassifiedText = ocrText

        // Payment app reminder (replaces the old PaymentAppMonitor flow).
        maybeFirePaymentReminder(ocrText)

        // SMS / message scam detection — feed OCR'd text through the existing
        // local classifier. Cheap call, mostly regex/keyword matching.
        val classification = classifier.classifyText(ocrText)
        Log.d(TAG, "Classified: verdict=${classification.verdict} conf=${classification.confidence}")
        publishStatus { it.copy(lastVerdict = "${classification.verdict} (${(classification.confidence * 100).toInt()}%)") }
        if (classification.verdict == "DANGEROUS" || classification.verdict == "SUSPICIOUS") {
            publishStatus { it.copy(scamsDetectedSinceStart = it.scamsDetectedSinceStart + 1) }
            persistAlert(ocrText, classification.verdict, classification.confidence,
                classification.reasons.firstOrNull() ?: "Possible scam detected on screen")
        }
    }

    private fun maybeFirePaymentReminder(ocrText: String) {
        val now = System.currentTimeMillis()
        if (now - lastPaymentAlertTimeMs < PAYMENT_COOLDOWN_MS) return
        if (!looksLikePaymentApp(ocrText)) return
        lastPaymentAlertTimeMs = now
        try {
            notificationHelper.showPaymentReminder()
        } catch (e: Exception) {
            Log.w(TAG, "showPaymentSafetyReminder threw", e)
        }
    }

    private fun persistAlert(ocrText: String, verdict: String, confidence: Float, reason: String) {
        // Keep the saved content short — we don't want a full screen of OCR'd
        // text in our database, and definitely not bank balances etc. Trim to
        // first 200 characters which is enough to be a useful alert preview.
        val preview = ocrText.take(200)
        val mappedRisk = when (verdict) {
            "DANGEROUS" -> "SCAM"
            "SUSPICIOUS" -> "WARNING"
            else -> "SAFE"
        }
        val alert = AlertEntity(
            type = "SCREEN_SMS",
            sender = "(detected on screen)",
            content = preview,
            riskLevel = mappedRisk,
            confidence = confidence,
            reason = reason,
            action = "Open Safe Companion to review"
        )
        scope.launch {
            try {
                // Save the alert and grab the row id back from Room so the
                // notification can deep-link to the right detail screen.
                val newId = alertRepository.insertLocal(alert)
                val savedAlert = alert.copy(id = newId)
                Log.d(TAG, "Screen scam detected: verdict=$verdict reason=\"$reason\" id=$newId")
                // Switch to main thread for the notification post — the
                // NotificationHelper grab-attention path posts a full-screen
                // intent which prefers the main looper.
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    try { notificationHelper.showSmsAlert(savedAlert) } catch (e: Exception) {
                        Log.w(TAG, "showSmsAlert threw", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist screen alert", e)
            }
        }
    }

    /**
     * Coarse perceptual fingerprint of a bitmap: 100 pixels sampled in a 10×10
     * grid, hashed into a single Long. Two bitmaps with the same fingerprint
     * are visually nearly-identical for our purposes (same screen, no scroll,
     * no animation). Costs microseconds — far cheaper than OCR.
     *
     * Not cryptographic, not collision-proof; doesn't need to be. Worst-case
     * collision means we re-OCR an unchanged screen, which is the original
     * behaviour anyway.
     */
    private fun bitmapFingerprint(bitmap: Bitmap): Long {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 10 || h < 10) return 0L
        var hash = 1125899906842597L  // arbitrary large prime seed
        val gridSize = 10
        val xStep = w / gridSize
        val yStep = h / gridSize
        for (i in 0 until gridSize) {
            val x = (i * xStep).coerceAtMost(w - 1)
            for (j in 0 until gridSize) {
                val y = (j * yStep).coerceAtMost(h - 1)
                val pixel = bitmap.getPixel(x, y)
                hash = hash * 31 + pixel.toLong()
            }
        }
        return hash
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "imageToBitmap failed", e)
            null
        }
    }

    private fun looksLikeOurOwnUi(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("safe companion") ||
            lower.contains("is this safe?") ||
            lower.contains("connect safe companion") ||
            lower.contains("safety checker")
    }

    private fun looksLikePaymentApp(text: String): Boolean {
        val lower = text.lowercase()
        // Match the display copy that payment apps put prominently on screen.
        // Combined keyword (an app name) + transaction word (Send/Pay/Request/$).
        val appHits = listOf(
            "venmo", "cash app", "zelle", "paypal", "google pay",
            "samsung pay", "apple pay"
        ).any { lower.contains(it) }
        if (!appHits) return false
        val txHits = listOf(
            "send money", "pay $", "request $", "send $",
            "send payment", "transfer to", "confirm payment"
        ).any { lower.contains(it) }
        // Or just the bare app name combined with a dollar amount, which is
        // basically every Venmo screen.
        val hasMoney = Regex("""\$\s?\d""").containsMatchIn(text)
        return txHits || hasMoney
    }

    private fun tearDownCapture() {
        captureJob?.cancel()
        captureJob = null
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        publishStatus { it.copy(isRunning = false) }
    }

    override fun onDestroy() {
        tearDownCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SCREEN_SCAN,
                "Screen Monitor",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Safe Companion is watching your screen for scams"
                setShowBadge(false)
            }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val tap = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPi = PendingIntent.getActivity(
            this, 0, tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // One-tap "Turn Off" action. Sends ACTION_STOP to this same service,
        // which tears the MediaProjection token down and stopSelf()s. Required
        // by good Android-citizenship UX (and Google Play surveillance policy)
        // — the user must always have an obvious off switch in the persistent
        // notification, not buried in Settings.
        val stopIntent = Intent(this, ScreenScanService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_SCREEN_SCAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Safe Companion is reading your screen for scams")
            .setContentText("Pictures stay on this phone. Tap Turn Off to stop.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Safe Companion looks at the words on your screen using its " +
                        "own brain, then throws the picture away. Nothing is sent " +
                        "anywhere. Tap Turn Off to stop at any time."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(tapPi)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification,
                    "Turn Off",
                    stopPi
                ).build()
            )
            .build()
    }
}
