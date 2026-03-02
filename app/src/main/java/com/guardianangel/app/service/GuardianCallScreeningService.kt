package com.guardianangel.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.guardianangel.app.data.datastore.UserPreferences
import com.guardianangel.app.data.local.entity.CallLogEntity
import com.guardianangel.app.data.repository.AlertRepository
import com.guardianangel.app.data.repository.CallRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class GuardianCallScreeningService : CallScreeningService() {

    @Inject lateinit var callRepository: CallRepository
    @Inject lateinit var alertRepository: AlertRepository
    @Inject lateinit var userPreferences: UserPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: run {
            respondToCall(callDetails, buildResponse(reject = false))
            return
        }

        scope.launch {
            try {
                val callShieldOn = userPreferences.isCallShieldEnabled.first()
                if (!callShieldOn) {
                    respondToCall(callDetails, buildResponse(reject = false))
                    return@launch
                }

                // Record analytics — this call is being screened
                userPreferences.recordCallScreened()

                // Check local blocklist first — reject immediately
                if (alertRepository.isBlocked(number)) {
                    callRepository.insertCallLog(
                        CallLogEntity(
                            callerNumber = number,
                            riskLevel = "SCAM",
                            summary = "Blocked number — automatically rejected",
                            isBlocked = true
                        )
                    )
                    respondToCall(callDetails, buildResponse(reject = true, skipCallLog = true))
                    return@launch
                }

                // Check heuristics for obvious robocall patterns
                if (isObviousSpam(number)) {
                    callRepository.insertCallLog(
                        CallLogEntity(
                            callerNumber = number,
                            riskLevel = "SUSPICIOUS",
                            summary = "Flagged by robocall heuristics"
                        )
                    )
                    // Start overlay service to let user decide
                    startOverlayService(number, "SUSPICIOUS", "This number matches robocall patterns.")
                    respondToCall(callDetails, buildResponse(reject = false))
                    return@launch
                }

                // Let call through; log it for review
                callRepository.insertCallLog(
                    CallLogEntity(
                        callerNumber = number,
                        riskLevel = "UNKNOWN",
                        summary = "Screened — no immediate threat detected"
                    )
                )

                respondToCall(callDetails, buildResponse(reject = false))

            } catch (e: Exception) {
                // On any error, let call through
                respondToCall(callDetails, buildResponse(reject = false))
            }
        }
    }

    private fun buildResponse(reject: Boolean, skipCallLog: Boolean = false): CallResponse {
        return CallResponse.Builder()
            .setDisallowCall(reject)
            .setRejectCall(reject)
            .setSilenceCall(false)
            .setSkipCallLog(skipCallLog)
            .build()
    }

    private fun isObviousSpam(number: String): Boolean {
        val clean = number.replace("[^0-9]".toRegex(), "")
        return when {
            clean.startsWith("900") -> true          // 900 numbers
            clean.startsWith("1900") -> true
            clean.length < 7 -> true                 // Too short
            clean.all { it == clean[0] } -> true     // All same digit (e.g. 1111111111)
            else -> false
        }
    }

    private fun startOverlayService(number: String, risk: String, warning: String) {
        val intent = android.content.Intent(this, CallOverlayService::class.java).apply {
            putExtra(CallOverlayService.EXTRA_NUMBER, number)
            putExtra(CallOverlayService.EXTRA_RISK, risk)
            putExtra(CallOverlayService.EXTRA_WARNING, warning)
        }
        startForegroundService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
