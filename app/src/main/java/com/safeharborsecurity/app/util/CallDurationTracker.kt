package com.safeharborsecurity.app.util

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.safeharborsecurity.app.data.model.AlertTrigger
import com.safeharborsecurity.app.data.repository.CallRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Part G2: Tracks the duration of the most recent ringing/active call. If the
 * call's number was previously flagged as suspicious by the call screening
 * service, fires SUSPICIOUS_CALL_1_MIN and SUSPICIOUS_CALL_5_MIN family alerts.
 */
@Singleton
class CallDurationTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callRepository: CallRepository,
    private val familyAlertManager: FamilyAlertManager
) {
    companion object { private const val TAG = "CallDurationTracker" }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executor = Executors.newSingleThreadExecutor()

    private var currentNumber: String? = null
    private var currentRiskLevel: String? = null
    private var callStartedAt: Long = 0
    private var oneMinJob: Job? = null
    private var fiveMinJob: Job? = null

    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: Any? = null  // TelephonyCallback (S+)

    fun start() {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startModern(tm)
        } else {
            startLegacy(tm)
        }
        Log.d(TAG, "CallDurationTracker started")
    }

    @Suppress("DEPRECATION")
    private fun startLegacy(tm: TelephonyManager) {
        legacyListener = object : PhoneStateListener() {
            @Deprecated("Required for API < 31; replaced by TelephonyCallback above.")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleCallState(state, phoneNumber)
            }
        }
        tm.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun startModern(tm: TelephonyManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallState(state, null)
            }
        }
        modernCallback = cb
        try {
            tm.registerTelephonyCallback(executor, cb)
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_PHONE_STATE not granted — call duration tracking disabled")
        }
    }

    private fun handleCallState(state: Int, phoneNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> onCallStarted(phoneNumber)
            TelephonyManager.CALL_STATE_IDLE -> onCallEnded()
            TelephonyManager.CALL_STATE_RINGING -> { /* ignored — wait for OFFHOOK */ }
        }
    }

    private fun onCallStarted(phoneNumber: String?) {
        if (callStartedAt > 0) return  // already in a call
        callStartedAt = System.currentTimeMillis()
        currentNumber = phoneNumber

        scope.launch {
            // Look up the most recent call log entry to get the screened risk level
            val recent = runCatching { callRepository.recentLog(limit = 1) }
                .getOrNull()?.firstOrNull()
            currentRiskLevel = recent?.riskLevel
            val number = phoneNumber ?: recent?.callerNumber

            if (currentRiskLevel != "SCAM" && currentRiskLevel != "SUSPICIOUS") {
                // Not flagged — nothing to do
                return@launch
            }
            Log.d(TAG, "Tracking suspicious call to $number (risk=$currentRiskLevel)")

            // Fire 1-min alert
            oneMinJob = launch {
                delay(60_000)
                if (callStartedAt > 0) {
                    familyAlertManager.triggerAlert(AlertTrigger.SUSPICIOUS_CALL_1_MIN, confidence = 80)
                }
            }
            // Fire 5-min alert
            fiveMinJob = launch {
                delay(5 * 60_000)
                if (callStartedAt > 0) {
                    familyAlertManager.triggerAlert(AlertTrigger.SUSPICIOUS_CALL_5_MIN, confidence = 95)
                }
            }
        }
    }

    private fun onCallEnded() {
        callStartedAt = 0
        currentNumber = null
        currentRiskLevel = null
        oneMinJob?.cancel(); oneMinJob = null
        fiveMinJob?.cancel(); fiveMinJob = null
    }

    fun stop() {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (modernCallback as? TelephonyCallback)?.let { tm?.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                tm?.listen(legacyListener, PhoneStateListener.LISTEN_NONE)
            }
        } catch (_: Exception) {}
        modernCallback = null
        legacyListener = null
    }
}
