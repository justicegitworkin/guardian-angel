package com.guardianangel.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.guardianangel.app.data.datastore.UserPreferences
import com.guardianangel.app.data.local.dao.ScamRuleDao
import com.guardianangel.app.data.local.entity.ScamRuleEntity
import com.guardianangel.app.data.remote.ScamIntelligenceApiService
import com.guardianangel.app.data.remote.ScamReportDto
import com.guardianangel.app.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class ScamIntelligenceSync @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scamRuleDao: ScamRuleDao,
    private val apiService: ScamIntelligenceApiService,
    private val userPreferences: UserPreferences,
    private val notificationHelper: NotificationHelper,
    private val gson: Gson
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "scam_intelligence_sync"
    }

    override suspend fun doWork(): Result {
        val serverUrl = userPreferences.scamIntelServerUrl.first().trim()
        if (serverUrl.isBlank()) return Result.success()   // feature not configured

        val lastSync  = userPreferences.scamIntelLastSync.first()
        val base      = serverUrl.trimEnd('/')

        return try {
            val response = apiService.getScamRules("$base/scam-rules", lastSync)
            if (!response.isSuccessful) return Result.retry()

            val rules = response.body()?.rules
            if (rules.isNullOrEmpty()) {
                userPreferences.setScamIntelLastSync(System.currentTimeMillis())
                return Result.success()
            }

            // Map DTOs → entities
            val entities = rules.map { dto ->
                ScamRuleEntity(
                    id                   = dto.id,
                    scamType             = dto.scamType,
                    keyPhrases           = gson.toJson(dto.keyPhrases),
                    urgencyIndicators    = gson.toJson(dto.urgencyIndicators),
                    impersonationTargets = gson.toJson(dto.impersonationTargets),
                    plainEnglishWarning  = dto.plainEnglishWarning,
                    severity             = dto.severity,
                    createdAt            = dto.createdAt
                )
            }
            scamRuleDao.insertAll(entities)

            // Remove stale rules (older than 30 days)
            scamRuleDao.deleteOlderThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))

            // Update last-sync timestamp
            userPreferences.setScamIntelLastSync(System.currentTimeMillis())

            // Notify user if any HIGH/CRITICAL rules arrived and notifications are enabled
            val newHighCritical = entities.count { it.severity in listOf("HIGH", "CRITICAL") }
            if (newHighCritical > 0 && userPreferences.scamIntelNotifications.first()) {
                notificationHelper.showScamIntelUpdate(newHighCritical)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
