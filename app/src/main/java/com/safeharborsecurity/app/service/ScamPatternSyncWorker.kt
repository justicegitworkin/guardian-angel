package com.safeharborsecurity.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Part A5: Pattern auto-update.
 *
 * For now, scam patterns are compiled into the app via [OnDeviceScamClassifier].
 * The architecture supports remote updates: once the cloud fraud-detection API
 * (Phase 10 in CLAUDE.md) is up, this worker will fetch a fresh pattern set
 * from `https://safecompanion-api.azurewebsites.net/api/v1/patterns`, persist
 * it to Room (or DataStore), and the classifier will read the merged set.
 *
 * Right now `doWork()` is a no-op so the WorkManager schedule is in place and
 * the wiring works end-to-end the day the API is ready.
 */
@HiltWorker
class ScamPatternSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_TAG = "scam_pattern_sync"

        fun enqueueWeekly(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val work = PeriodicWorkRequestBuilder<ScamPatternSyncWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG, ExistingPeriodicWorkPolicy.KEEP, work
            )
        }
    }

    override suspend fun doWork(): Result {
        // TODO: When the cloud fraud-detection API is live, fetch the latest
        // pattern set and merge into the on-device store. Until then, succeed
        // immediately so the schedule remains active.
        return Result.success()
    }
}
