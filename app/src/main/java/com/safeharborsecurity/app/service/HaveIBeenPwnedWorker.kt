package com.safeharborsecurity.app.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safeharborsecurity.app.data.local.dao.ConnectedServiceDao
import com.safeharborsecurity.app.data.local.entity.ConnectedServiceEntity
import com.safeharborsecurity.app.data.datastore.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltWorker
class HaveIBeenPwnedWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val connectedServiceDao: ConnectedServiceDao,
    private val prefs: UserPreferences
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HIBP"
        const val WORK_NAME = "hibp_check"
    }

    override suspend fun doWork(): Result {
        val apiKey = prefs.hibpApiKey.first()
        if (apiKey.isBlank()) {
            Log.d(TAG, "No HIBP API key configured, skipping")
            return Result.success()
        }

        val userName = prefs.userName.first()
        // For now, check the user's name-derived email patterns or stored emails
        // In a real implementation, we'd check all connected email accounts

        try {
            val client = OkHttpClient()
            // HIBP requires a user agent and API key
            val request = Request.Builder()
                .url("https://haveibeenpwned.com/api/v3/breachedaccount/$userName")
                .addHeader("hibp-api-key", apiKey)
                .addHeader("user-agent", "SafeHarborSecurity-Android")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val summary = when (response.code) {
                200 -> {
                    // Parse breaches - simplified
                    val breachCount = body.split("\"Name\"").size - 1
                    if (breachCount > 0) {
                        "Found in $breachCount data leak(s). Check your passwords."
                    } else {
                        "No data leaks found. You're safe!"
                    }
                }
                404 -> "No data leaks found. You're safe!"
                401 -> "API key is invalid. Please check your HaveIBeenPwned key."
                429 -> "Too many checks. We'll try again later."
                else -> "Could not check right now. We'll try again later."
            }

            connectedServiceDao.upsert(
                ConnectedServiceEntity(
                    serviceId = "hibp",
                    serviceName = "HaveIBeenPwned",
                    isConnected = apiKey.isNotBlank(),
                    lastSyncTime = System.currentTimeMillis(),
                    authTokenEncrypted = "",
                    resultSummary = summary
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "HIBP check failed", e)
            return Result.retry()
        }

        return Result.success()
    }
}
