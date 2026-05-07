package com.safeharborsecurity.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.safeharborsecurity.app.service.CheckInWorker
import com.safeharborsecurity.app.service.NewsSyncWorker
import com.safeharborsecurity.app.service.RemediationSyncWorker
import com.safeharborsecurity.app.service.ScamTipWorker
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.repository.AlertRepository
import com.safeharborsecurity.app.security.IntegrityChecker
import com.safeharborsecurity.app.service.ScamPatternSyncWorker
import com.safeharborsecurity.app.service.EmailScanWorker
import com.safeharborsecurity.app.service.WeeklyDigestWorker
import com.safeharborsecurity.app.service.WeeklyEmailSummaryWorker
import com.safeharborsecurity.app.service.WeeklyReportWorker
import com.safeharborsecurity.app.util.SampleDataSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SafeHarborApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var alertRepository: AlertRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val TAG = "SafeHarbor"
        const val CHANNEL_ALERTS = "safeharbor_alerts"
        const val CHANNEL_CALL = "safeharbor_call"
        const val CHANNEL_PRIVACY = "safeharbor_privacy"
        const val CHANNEL_CHECKIN = "safeharbor_checkin"
        const val CHANNEL_REPORT = "safeharbor_report"
        const val CHANNEL_SCAM_TIP = "safeharbor_scam_tip"
        const val CHANNEL_PAYMENT = "safeharbor_payment"

        /**
         * Part C2: Set true if the startup integrity check finds HIGH risk
         * (root + emulator + tampered, etc.). ViewModels can read this and
         * downgrade to on-device-only analysis to keep cloud API keys safe.
         */
        @Volatile var integrityRiskHigh: Boolean = false
            private set

        @Volatile var lastIntegrityReport: IntegrityChecker.IntegrityReport? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        createNotificationChannels()
        runIntegrityCheck()
        RemediationSyncWorker.enqueuePeriodicSync(this)
        CheckInWorker.enqueueDaily(this)
        WeeklyReportWorker.enqueueWeekly(this)
        WeeklyEmailSummaryWorker.enqueueWeekly(this)
        WeeklyDigestWorker.enqueueWeekly(this)
        EmailScanWorker.enqueuePeriodic(this)
        ScamPatternSyncWorker.enqueueWeekly(this)
        ScamTipWorker.enqueueWeekly(this)
        // Schedule the recurring 6-hour news sync, plus fire one immediate
        // sync on every launch so users see fresh stories without having to
        // open the news screen and tap refresh first.
        val workManager = androidx.work.WorkManager.getInstance(this)
        NewsSyncWorker.schedule(workManager)
        NewsSyncWorker.syncNow(workManager)
        seedSampleDataIfFirstRun()
        seedBakedKeysFromBuildConfig()
        wireOwnAppForegroundFlag()
    }

    /**
     * Track whether *any* Safe Companion activity is currently in the
     * foreground, and mirror that into ScreenScanService.ourAppForegrounded.
     * The screen-scan loop reads that flag to skip OCR while the user is
     * looking at our own UI — preventing the bug where chatting with Grace
     * about a scam triggered a screen-monitor alert about that very chat.
     *
     * ProcessLifecycleOwner aggregates all the app's activities into a
     * single ON_START / ON_STOP signal at the process level, so we don't
     * have to register per-activity callbacks.
     */
    private fun wireOwnAppForegroundFlag() {
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                    com.safeharborsecurity.app.service.ScreenScanService
                        .ourAppForegrounded = true
                    Log.d(TAG, "App foregrounded — pausing screen-scan OCR")
                }
                override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                    com.safeharborsecurity.app.service.ScreenScanService
                        .ourAppForegrounded = false
                    Log.d(TAG, "App backgrounded — resuming screen-scan OCR")
                }
            }
        )
    }

    /**
     * If the build embeds an Anthropic key (via `safe.companion.anthropic.api.key`
     * in local.properties → BuildConfig.DEFAULT_ANTHROPIC_API_KEY), copy it into
     * DataStore on every launch where DataStore is empty. This handles the case
     * where the user already finished onboarding before the key was baked in,
     * so the onboarding flow won't trigger again — but the saved key was empty.
     *
     * Never overwrites a non-blank user-entered key.
     */
    /**
     * Sync DataStore with whatever keys are baked into BuildConfig from
     * local.properties. Runs every cold start.
     *
     * IMPORTANT: This *overwrites* the saved key when a baked key is present
     * and they differ. That's deliberate — the bake-in is a developer/tester
     * convenience and the assumption is "the build is the source of truth
     * for the key." If a tester rotates the key in local.properties, the
     * rebuilt APK should pick up the new key on next launch without them
     * having to manually clear app data.
     *
     * If the baked key is blank, we leave the saved key alone — that's the
     * "user-entered their own key" path.
     */
    private fun seedBakedKeysFromBuildConfig() {
        val bakedAnthropic = BuildConfig.DEFAULT_ANTHROPIC_API_KEY
        if (bakedAnthropic.isNotBlank()) {
            appScope.launch {
                try {
                    val current = userPreferences.apiKey.first()
                    if (current != bakedAnthropic) {
                        userPreferences.setApiKey(bakedAnthropic)
                        Log.d(TAG, "Synced Anthropic API key from BuildConfig (was ${if (current.isBlank()) "blank" else "different"})")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync BuildConfig Anthropic key", e)
                }
            }
        }

        val bakedEl = BuildConfig.DEFAULT_ELEVENLABS_API_KEY
        if (bakedEl.isNotBlank()) {
            appScope.launch {
                try {
                    val current = userPreferences.elevenLabsApiKey.first()
                    if (current != bakedEl) {
                        userPreferences.setElevenLabsApiKey(bakedEl)
                        Log.d(TAG, "Synced ElevenLabs API key from BuildConfig (was ${if (current.isBlank()) "blank" else "different"})")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync BuildConfig ElevenLabs key", e)
                }
            }
        }
    }

    /**
     * Part D3: On the very first launch (and only that launch), insert a few
     * representative AlertEntity rows so testers see DANGEROUS / WARNING / SAFE
     * cards on the home screen instead of an empty state.
     */
    private fun seedSampleDataIfFirstRun() {
        appScope.launch {
            val alreadySeeded = userPreferences.isSampleDataSeeded.first()
            if (alreadySeeded) return@launch
            try {
                SampleDataSeeder.sampleAlerts().forEach { alertRepository.insertLocal(it) }
                userPreferences.setSampleDataSeeded(true)
                Log.d(TAG, "Sample alert data seeded (${SampleDataSeeder.sampleAlerts().size} entries)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to seed sample data", e)
            }
        }
    }

    private fun runIntegrityCheck() {
        try {
            val report = IntegrityChecker.check(this)
            lastIntegrityReport = report
            integrityRiskHigh = report.riskLevel == IntegrityChecker.RiskLevel.HIGH
            if (integrityRiskHigh) {
                Log.w(TAG, "Integrity HIGH risk: rooted=${report.isRooted} " +
                    "emulator=${report.isEmulator} debuggable=${report.isDebuggable} " +
                    "debugger=${report.isDebuggerAttached} tampered=${report.isTampered}")
            } else {
                Log.d(TAG, "Integrity check passed (level=${report.riskLevel})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Integrity check threw — defaulting to LOW risk", e)
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Force-recreate the Listening Shield channel so existing testers
            // who already had it created at IMPORTANCE_LOW get the new MIN
            // behaviour. Android keeps user-set importance after the first
            // creation, so the only reliable way to push down the noise
            // level is to delete + recreate.
            try { manager.deleteNotificationChannel(CHANNEL_PRIVACY) } catch (_: Exception) {}
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    getString(R.string.channel_alerts_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = getString(R.string.channel_alerts_description)
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_CALL,
                    getString(R.string.channel_call_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = getString(R.string.channel_call_description)
                },
                NotificationChannel(
                    CHANNEL_PRIVACY,
                    "Listening Shield",
                    // IMPORTANCE_MIN: notification is required by Android for
                    // any foreground service, but we want it as quiet as
                    // legally possible. MIN means: no sound, no vibration,
                    // no heads-up, and on most launchers just a small icon
                    // in the status bar. Tester complained that turning the
                    // shield on every time fired a useless pop-up — this
                    // suppresses it.
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Listening Shield background monitoring"
                    setShowBadge(false)
                    setSound(null, null)
                    enableLights(false)
                    enableVibration(false)
                },
                NotificationChannel(
                    CHANNEL_CHECKIN,
                    "Daily Check-In",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Daily check-in reminders"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_REPORT,
                    "Weekly Report",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Weekly security report card"
                },
                NotificationChannel(
                    CHANNEL_SCAM_TIP,
                    "Scam Tips",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Weekly scam awareness tips"
                },
                NotificationChannel(
                    CHANNEL_PAYMENT,
                    "Payment Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Safety reminders when opening payment apps"
                }
            )
            manager.createNotificationChannels(channels)
        }
    }
}
