package com.safeharborsecurity.app.service

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.entity.RemediationKnowledgeEntity
import com.safeharborsecurity.app.data.remote.ClaudeApiService
import com.safeharborsecurity.app.data.remote.model.ClaudeMessage
import com.safeharborsecurity.app.data.remote.model.ClaudeRequest
import com.safeharborsecurity.app.data.repository.RemediationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class RemediationSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val remediationRepository: RemediationRepository,
    private val claudeApiService: ClaudeApiService,
    private val userPreferences: UserPreferences,
    private val gson: Gson
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "RemediationSync"
        const val WORK_NAME = "remediation_sync"

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<RemediationSyncWorker>(
                7, TimeUnit.DAYS,
                1, TimeUnit.DAYS
            ).setConstraints(constraints)
             .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
             .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<RemediationSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_manual",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val apiKey = userPreferences.apiKey.first()
            if (apiKey.isBlank()) {
                Log.w(TAG, "No API key configured, skipping sync")
                return Result.retry()
            }

            val currentRecords = remediationRepository.getAllOnce()
            val recordsSummary = currentRecords.map { record ->
                mapOf(
                    "packageNamePattern" to record.packageNamePattern,
                    "appDisplayName" to record.appDisplayName,
                    "sourceVersion" to record.sourceVersion,
                    "howToInstructions" to record.howToInstructions,
                    "canToggleDirectly" to record.canToggleDirectly,
                    "settingsIntentAction" to (record.settingsIntentAction ?: ""),
                    "settingsIntentPackage" to (record.settingsIntentPackage ?: "")
                )
            }

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val androidVersion = Build.VERSION.SDK_INT

            val prompt = """You are a security knowledge base for the Safe Companion Android app.
Here is our current list of app remediation records:
${gson.toJson(recordsSummary)}

Today's date is $today. The device runs Android API $androidVersion.
The app targets Android 8.0 (API 26) through Android 15 (API 35).

For each record, confirm if the instructions are still accurate for current app versions.
Flag any that need updating and provide revised howToInstructions.
Also suggest any NEW packages we should add that are known to use microphone
or conversation data for advertising purposes as of today.

Respond ONLY in JSON as an array of objects with these fields:
- packageNamePattern (String)
- appDisplayName (String)
- androidMinVersion (Int)
- androidMaxVersion (Int, use ${Int.MAX_VALUE} for no upper bound)
- canToggleDirectly (Boolean)
- settingsIntentAction (String or null)
- settingsIntentPackage (String or null)
- howToInstructions (String, plain English step-by-step)
- learnMoreUrl (String or null)
- sourceVersion (Int, increment if changed)

Include ALL records (existing + new). Do not include any text outside the JSON array."""

            val request = ClaudeRequest(
                model = "claude-haiku-4-5-20251001",
                maxTokens = 4096,
                messages = listOf(ClaudeMessage(role = "user", content = prompt)),
                system = "You are a security knowledge base assistant. Respond only with valid JSON."
            )

            val response = claudeApiService.sendMessage(apiKey, request)
            if (!response.isSuccessful) {
                Log.e(TAG, "API call failed: ${response.code()}")
                return Result.retry()
            }

            val responseText = response.body()?.text ?: return Result.retry()

            // Parse JSON array from response — strip any markdown fences
            val jsonText = responseText
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val type = object : TypeToken<List<RemediationRecord>>() {}.type
            val records: List<RemediationRecord> = try {
                gson.fromJson(jsonText, type)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse response JSON", e)
                return Result.retry()
            }

            val now = System.currentTimeMillis()
            val entities = records.map { record ->
                // Find existing entity to preserve ID
                val existing = remediationRepository.findForPackage(record.packageNamePattern)
                RemediationKnowledgeEntity(
                    id = existing?.id ?: 0,
                    packageNamePattern = record.packageNamePattern,
                    appDisplayName = record.appDisplayName,
                    androidMinVersion = record.androidMinVersion,
                    androidMaxVersion = record.androidMaxVersion,
                    canToggleDirectly = record.canToggleDirectly,
                    settingsIntentAction = record.settingsIntentAction,
                    settingsIntentPackage = record.settingsIntentPackage,
                    howToInstructions = record.howToInstructions,
                    learnMoreUrl = record.learnMoreUrl,
                    lastVerified = now,
                    sourceVersion = record.sourceVersion
                )
            }

            remediationRepository.upsertAll(entities)
            userPreferences.setRemediationLastSync(now)

            Log.i(TAG, "Synced ${entities.size} remediation records")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    /** Intermediate data class for JSON parsing. */
    private data class RemediationRecord(
        val packageNamePattern: String = "",
        val appDisplayName: String = "",
        val androidMinVersion: Int = 26,
        val androidMaxVersion: Int = Int.MAX_VALUE,
        val canToggleDirectly: Boolean = false,
        val settingsIntentAction: String? = null,
        val settingsIntentPackage: String? = null,
        val howToInstructions: String = "",
        val learnMoreUrl: String? = null,
        val sourceVersion: Int = 1
    )
}
