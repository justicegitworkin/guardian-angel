package com.safeharborsecurity.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.safeharborsecurity.app.service.AppCheckWorker
import java.util.concurrent.TimeUnit

class PackageInstallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PackageInstall"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return // Skip updates

        val packageName = intent.data?.schemeSpecificPart ?: return
        Log.d(TAG, "New app installed: $packageName")

        // Schedule a one-time check after a brief delay
        val workRequest = OneTimeWorkRequestBuilder<AppCheckWorker>()
            .setInputData(workDataOf("packageName" to packageName))
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
