package com.safeharborsecurity.app.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.safeharborsecurity.app.data.repository.NewsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class NewsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val newsRepository: NewsRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "NewsSyncWorker"
        private const val WORK_NAME = "news_sync"

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<NewsSyncWorker>(
                6, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "News sync scheduled every 6 hours")
        }

        fun syncNow(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<NewsSyncWorker>()
                .setConstraints(constraints)
                .build()

            workManager.enqueue(request)
            Log.d(TAG, "One-time news sync requested")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val count = newsRepository.syncAllFeeds()
            Log.d(TAG, "News sync complete: $count articles processed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "News sync failed: ${e.message}")
            Result.retry()
        }
    }
}
