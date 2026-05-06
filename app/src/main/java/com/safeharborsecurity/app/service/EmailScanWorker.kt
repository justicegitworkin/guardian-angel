package com.safeharborsecurity.app.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.repository.AlertRepository
import com.safeharborsecurity.app.util.GmailAuthManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Silent Guardian Path 2: periodic background Gmail scan.
 *
 * Runs every 6 hours when the user has Gmail connected. Pulls recent emails,
 * runs each through the existing analyzeEmail pipeline, and (if the user is
 * in Silent Guardian mode AND verdict is high-confidence scam) auto-applies
 * the SafeCompanion/Quarantined label and removes from INBOX.
 *
 * This is the difference between "Gmail auto-quarantine works on initial
 * connect" and "Gmail auto-quarantine is a real always-on feature." Without
 * this worker, the user would have to manually re-sync from Settings to get
 * new quarantines.
 */
@HiltWorker
class EmailScanWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val userPreferences: UserPreferences,
    private val gmailAuthManager: GmailAuthManager,
    private val alertRepository: AlertRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "EmailScanWorker"
        private const val WORK_TAG = "email_scan_worker"

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val work = PeriodicWorkRequestBuilder<EmailScanWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }
    }

    override suspend fun doWork(): Result {
        val account = GoogleSignIn.getLastSignedInAccount(appContext)
        if (account == null || account.email.isNullOrBlank()) {
            Log.d(TAG, "No Gmail account signed in — skipping scan")
            return Result.success()
        }

        val apiKey = userPreferences.apiKey.first()
        if (apiKey.isBlank()) {
            Log.d(TAG, "No Anthropic key — skipping cloud-based email scan")
            return Result.success()
        }

        val isSilentGuardian = userPreferences.operatingMode.first() == "SILENT_GUARDIAN"

        val fetchResult = gmailAuthManager.fetchRecentEmails(account, maxResults = 30)
        val emails = fetchResult.getOrNull() ?: run {
            Log.w(TAG, "Failed to fetch emails: ${fetchResult.exceptionOrNull()?.message}")
            return Result.retry()
        }

        if (emails.isEmpty()) {
            Log.d(TAG, "No new emails to scan")
            return Result.success()
        }

        var scanned = 0
        var quarantined = 0
        for (msg in emails) {
            try {
                val alertResult = alertRepository.analyzeEmail(
                    apiKey = apiKey,
                    sender = msg.sender,
                    subject = msg.subject,
                    body = msg.snippet
                )
                alertResult.onSuccess { alert ->
                    val verdict = alert.riskLevel.uppercase()
                    if (isSilentGuardian && verdict in listOf("DANGEROUS", "HIGH", "SCAM")) {
                        val q = gmailAuthManager.quarantineEmail(account, msg.id)
                        if (q.isSuccess) quarantined++
                    }
                }
                scanned++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to scan email ${msg.id}", e)
            }
        }
        Log.d(TAG, "EmailScanWorker: scanned=$scanned quarantined=$quarantined silent=$isSilentGuardian")
        return Result.success()
    }
}
