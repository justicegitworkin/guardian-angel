package com.safeharborsecurity.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.entity.CallLogEntity
import com.safeharborsecurity.app.data.repository.AlertRepository
import com.safeharborsecurity.app.data.repository.CallRepository
import com.safeharborsecurity.app.ml.OnDeviceScamClassifier
import com.safeharborsecurity.app.util.NumberLookupUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class SafeHarborCallScreeningService : CallScreeningService() {

    @Inject lateinit var callRepository: CallRepository
    @Inject lateinit var alertRepository: AlertRepository
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var localClassifier: OnDeviceScamClassifier

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

                // Operating mode — Silent Guardian auto-declines confirmed
                // scams without the user ever seeing the call. Watch and Warn
                // (default) lets the call ring with an on-screen overlay so
                // the user makes the decision.
                val isSilentGuardian = userPreferences.operatingMode.first() == "SILENT_GUARDIAN"

                // Check local blocklist first — reject immediately regardless
                // of operating mode (the user explicitly blocked these).
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

                // Run number lookup against contacts and scam patterns
                val numberSafety = NumberLookupUtil.lookupNumber(applicationContext, number)

                // Known contacts — let through as safe
                if (numberSafety == NumberLookupUtil.NumberSafety.KNOWN_SAFE) {
                    callRepository.insertCallLog(
                        CallLogEntity(
                            callerNumber = number,
                            riskLevel = "SAFE",
                            summary = "Known contact — call allowed"
                        )
                    )
                    respondToCall(callDetails, buildResponse(reject = false))
                    return@launch
                }

                // Likely scam from number pattern database
                if (numberSafety == NumberLookupUtil.NumberSafety.LIKELY_SCAM) {
                    callRepository.insertCallLog(
                        CallLogEntity(
                            callerNumber = number,
                            riskLevel = "SCAM",
                            summary = if (isSilentGuardian) "Auto-declined — matches known scam patterns"
                                     else "Number matches known scam patterns",
                            isBlocked = isSilentGuardian
                        )
                    )
                    if (isSilentGuardian) {
                        // Silent Guardian: silently reject, skip system call log
                        // so the user doesn't get a missed-call notification.
                        respondToCall(callDetails, buildResponse(reject = true, skipCallLog = true))
                    } else {
                        startOverlayService(number, "SCAM", "This number matches known scam patterns. Be very careful.")
                        respondToCall(callDetails, buildResponse(reject = false))
                    }
                    return@launch
                }

                // Check heuristics for obvious robocall patterns
                if (isObviousSpam(number)) {
                    callRepository.insertCallLog(
                        CallLogEntity(
                            callerNumber = number,
                            riskLevel = "SUSPICIOUS",
                            summary = if (isSilentGuardian) "Auto-declined — robocall heuristics"
                                     else "Flagged by robocall heuristics",
                            isBlocked = isSilentGuardian
                        )
                    )
                    if (isSilentGuardian) {
                        respondToCall(callDetails, buildResponse(reject = true, skipCallLog = true))
                    } else {
                        startOverlayService(number, "SUSPICIOUS", "This number matches robocall patterns.")
                        respondToCall(callDetails, buildResponse(reject = false))
                    }
                    return@launch
                }

                // Part A4: On-device classifier for premium-rate / known scam patterns
                val localVerdict = localClassifier.classifyPhoneNumber(number)
                if (localVerdict.verdict == "DANGEROUS" || localVerdict.verdict == "SUSPICIOUS") {
                    val isDangerous = localVerdict.verdict == "DANGEROUS"
                    callRepository.insertCallLog(
                        CallLogEntity(
                            callerNumber = number,
                            riskLevel = if (isDangerous) "SCAM" else "SUSPICIOUS",
                            // Silent Guardian only auto-declines on DANGEROUS
                            // (high-confidence). SUSPICIOUS still rings with
                            // a warning even in Silent Guardian — too noisy
                            // to silence borderline cases.
                            summary = if (isSilentGuardian && isDangerous)
                                "Auto-declined: ${localVerdict.reasons.joinToString("; ")}"
                            else
                                localVerdict.reasons.joinToString("; "),
                            isBlocked = isSilentGuardian && isDangerous
                        )
                    )
                    if (isSilentGuardian && isDangerous) {
                        respondToCall(callDetails, buildResponse(reject = true, skipCallLog = true))
                    } else {
                        startOverlayService(number, localVerdict.verdict, localVerdict.reasons.joinToString("; "))
                        respondToCall(callDetails, buildResponse(reject = false))
                    }
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
                // On any error, let call through — fail open so we never
                // accidentally reject legitimate calls.
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
