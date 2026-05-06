package com.safeharborsecurity.app.ui.email

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.entity.EmailAccountEntity
import com.safeharborsecurity.app.data.repository.AlertRepository
import com.safeharborsecurity.app.data.repository.EmailAccountRepository
import com.safeharborsecurity.app.util.GmailAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class EmailAccountState {
    object Idle : EmailAccountState()
    object Connecting : EmailAccountState()
    data class Scanning(val message: String) : EmailAccountState()
    data class Connected(val summary: String) : EmailAccountState()
    data class Error(val message: String) : EmailAccountState()
}

data class EmailSetupUiState(
    val accounts: List<EmailAccountEntity> = emptyList(),
    val gmailState: EmailAccountState = EmailAccountState.Idle,
    val isGmailOAuthAvailable: Boolean = false,
    val toastMessage: String? = null,
    val gmailConfigDiagnostics: String? = null
)

private const val TAG = "EmailSetupVM"

@HiltViewModel
class EmailSetupViewModel @Inject constructor(
    private val emailAccountRepository: EmailAccountRepository,
    private val gmailAuthManager: GmailAuthManager,
    private val alertRepository: AlertRepository,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _toastMessage = MutableStateFlow<String?>(null)
    private val _gmailState = MutableStateFlow<EmailAccountState>(EmailAccountState.Idle)
    private val _gmailConfigDiag = MutableStateFlow<String?>(null)

    val isGmailOAuthAvailable: Boolean = gmailAuthManager.isGoogleSignInConfigured()

    val uiState: StateFlow<EmailSetupUiState> = combine(
        emailAccountRepository.getAccounts(),
        _toastMessage,
        _gmailState,
        _gmailConfigDiag
    ) { accounts, toast, gmailState, diag ->
        EmailSetupUiState(
            accounts = accounts,
            gmailState = gmailState,
            isGmailOAuthAvailable = isGmailOAuthAvailable,
            toastMessage = toast,
            gmailConfigDiagnostics = diag
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmailSetupUiState())

    private val apiKey: StateFlow<String> = prefs.apiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun getGmailSignInIntent(): Intent = gmailAuthManager.getSignInIntent()

    fun handleGmailSignInResult(data: Intent?) {
        _gmailState.value = EmailAccountState.Connecting

        val result = gmailAuthManager.handleSignInResult(data)
        result.onSuccess { account ->
            startGmailScan(account)
        }.onFailure { error ->
            _gmailState.value = EmailAccountState.Error(
                error.message ?: "Could not sign in to Google. Please try again."
            )
        }
    }

    private fun startGmailScan(account: GoogleSignInAccount) {
        val email = account.email ?: return
        val displayName = account.displayName ?: account.givenName ?: email.substringBefore("@")

        viewModelScope.launch {
            _gmailState.value = EmailAccountState.Scanning("Connecting to Gmail...")

            // Save account immediately so it appears in the list
            try {
                emailAccountRepository.addAccount(
                    email = email,
                    displayName = displayName,
                    provider = "GMAIL"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save Gmail account", e)
                _gmailState.value = EmailAccountState.Error("Could not save account. Please try again.")
                return@launch
            }

            // Fetch and scan recent emails
            _gmailState.value = EmailAccountState.Scanning("Fetching your recent emails...")

            val fetchResult = gmailAuthManager.fetchRecentEmails(account) { current, total ->
                _gmailState.value = EmailAccountState.Scanning("Checking email $current of $total...")
            }

            fetchResult.onSuccess { emails ->
                if (emails.isEmpty()) {
                    emailAccountRepository.updateSyncStats(email, 0, 0)
                    _gmailState.value = EmailAccountState.Connected("No recent emails found. Safe Companion will scan new emails as they arrive.")
                    return@launch
                }

                // Scan each email through Claude
                val key = apiKey.value
                var scannedCount = 0
                var dangerousCount = 0
                var suspiciousCount = 0
                var quarantinedCount = 0

                // Silent Guardian Path 2: auto-quarantine flagged scams when
                // the user has opted into Silent Guardian mode. Quarantined
                // emails get the SafeCompanion/Quarantined label and are
                // removed from INBOX (not deleted — recoverable from the
                // label).
                val isSilentGuardian = prefs.operatingMode.first() == "SILENT_GUARDIAN"

                for ((index, gmailMsg) in emails.withIndex()) {
                    _gmailState.value = EmailAccountState.Scanning("Scanning email ${index + 1} of ${emails.size}...")

                    if (key.isNotBlank()) {
                        try {
                            val alertResult = alertRepository.analyzeEmail(
                                apiKey = key,
                                sender = gmailMsg.sender,
                                subject = gmailMsg.subject,
                                body = gmailMsg.snippet
                            )
                            alertResult.onSuccess { alert ->
                                val verdict = alert.riskLevel.uppercase()
                                when (verdict) {
                                    "DANGEROUS", "HIGH", "SCAM" -> dangerousCount++
                                    "SUSPICIOUS", "WARNING", "MEDIUM" -> suspiciousCount++
                                }
                                // Auto-quarantine high-confidence scams when
                                // Silent Guardian is on. SUSPICIOUS verdicts
                                // are NOT auto-quarantined — too noisy to
                                // silently move borderline cases.
                                if (isSilentGuardian && verdict in listOf("DANGEROUS", "HIGH", "SCAM")) {
                                    val qResult = gmailAuthManager.quarantineEmail(account, gmailMsg.id)
                                    if (qResult.isSuccess) {
                                        quarantinedCount++
                                        Log.d(TAG, "Auto-quarantined ${gmailMsg.id}")
                                    } else {
                                        Log.w(TAG, "Auto-quarantine failed for ${gmailMsg.id}: ${qResult.exceptionOrNull()?.message}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to scan email ${gmailMsg.id}", e)
                        }
                    }
                    scannedCount++
                }
                if (quarantinedCount > 0) {
                    Log.d(TAG, "Auto-quarantined $quarantinedCount of $dangerousCount detected scams")
                }

                emailAccountRepository.updateSyncStats(email, scannedCount, dangerousCount + suspiciousCount)

                val summary = buildScanSummary(scannedCount, dangerousCount, suspiciousCount, quarantinedCount)
                _gmailState.value = EmailAccountState.Connected(summary)
            }.onFailure { error ->
                // Account was saved, but scan failed — still connected
                emailAccountRepository.updateSyncStats(email, 0, 0)
                _gmailState.value = EmailAccountState.Connected(
                    "Account connected! Could not scan existing emails right now, but new emails will be scanned automatically."
                )
            }
        }
    }

    private fun buildScanSummary(scanned: Int, dangerous: Int, suspicious: Int, quarantined: Int = 0): String {
        return when {
            quarantined > 0 -> "Auto-quarantined $quarantined dangerous email${if (quarantined > 1) "s" else ""} " +
                "to your SafeCompanion/Quarantined label. " +
                "${if (dangerous > quarantined) "${dangerous - quarantined} other dangerous, " else ""}" +
                "${if (suspicious > 0) "$suspicious suspicious — " else ""}" +
                "tap Messages → Emails to review."
            dangerous > 0 -> "Found $dangerous dangerous email${if (dangerous > 1) "s" else ""}! Tap Messages then Emails to review them."
            suspicious > 0 -> "Found $suspicious suspicious email${if (suspicious > 1) "s" else ""}. Tap Messages then Emails to take a look."
            scanned > 0 -> "Checked $scanned email${if (scanned > 1) "s" else ""} — all looking safe!"
            else -> "Account connected! Safe Companion will scan new emails as they arrive."
        }
    }

    fun addManualAccount(email: String, displayName: String, provider: String) {
        val trimmedEmail = email.trim()
        val trimmedName = displayName.trim()

        if (trimmedEmail.isBlank()) {
            _toastMessage.value = "Please enter your email address."
            return
        }
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            _toastMessage.value = "That doesn't look like a valid email address. Please check and try again."
            return
        }
        if (trimmedName.isBlank()) {
            _toastMessage.value = "Please enter your name."
            return
        }

        viewModelScope.launch {
            try {
                emailAccountRepository.addAccount(
                    email = trimmedEmail,
                    displayName = trimmedName,
                    provider = provider
                )
                _toastMessage.value = "Account added! Safe Companion will scan emails from $trimmedEmail."
                Log.d(TAG, "Email account added: $provider / $trimmedEmail")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add email account", e)
                _toastMessage.value = "Something went wrong. Please try again."
            }
        }
    }

    fun removeAccount(email: String) {
        viewModelScope.launch {
            emailAccountRepository.removeAccount(email)
            _gmailState.value = EmailAccountState.Idle
        }
    }

    fun dismissGmailState() {
        _gmailState.value = EmailAccountState.Idle
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun checkGmailConfig() {
        _gmailConfigDiag.value = gmailAuthManager.getConfigDiagnostics()
    }

    fun dismissGmailConfig() {
        _gmailConfigDiag.value = null
    }
}
