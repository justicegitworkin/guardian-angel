package com.safeharborsecurity.app.ui.privacy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.entity.RemediationKnowledgeEntity
import com.safeharborsecurity.app.data.repository.PrivacyMonitorRepository
import com.safeharborsecurity.app.data.repository.PrivacyScanResult
import com.safeharborsecurity.app.data.repository.PrivacyThreat
import com.safeharborsecurity.app.data.repository.RemediationRepository
import com.safeharborsecurity.app.service.PrivacyMonitorService
import com.safeharborsecurity.app.service.RemediationSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PrivacyUiState(
    val isShieldEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val lastScanResult: PrivacyScanResult? = null,
    val isServiceRunning: Boolean = false,
    val remediationKnowledge: List<RemediationKnowledgeEntity> = emptyList(),
    val remediationLastSync: Long = 0L
)

@HiltViewModel
class PrivacyMonitorViewModel @Inject constructor(
    private val privacyRepository: PrivacyMonitorRepository,
    private val remediationRepository: RemediationRepository,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)

    val uiState: StateFlow<PrivacyUiState> = combine(
        userPreferences.isListeningShieldEnabled,
        _isScanning,
        PrivacyMonitorService.lastScanResult,
        PrivacyMonitorService.isRunning
    ) { enabled, scanning, scanResult, running ->
        PrivacyUiState(
            isShieldEnabled = enabled,
            isScanning = scanning,
            lastScanResult = scanResult,
            isServiceRunning = running
        )
    }.combine(remediationRepository.getAll()) { state, knowledge ->
        state.copy(remediationKnowledge = knowledge)
    }.combine(userPreferences.remediationLastSync) { state, lastSync ->
        state.copy(remediationLastSync = lastSync)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    fun setShieldEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setListeningShield(enabled)
            if (enabled) {
                startService()
                runManualScan()
            } else {
                stopService()
            }
        }
    }

    fun runManualScan() {
        viewModelScope.launch {
            _isScanning.value = true
            val result = withContext(Dispatchers.IO) {
                privacyRepository.performFullScan()
            }
            PrivacyMonitorService._lastScanResult.value = result
            _isScanning.value = false
        }
    }

    fun refreshKnowledgeBase() {
        RemediationSyncWorker.enqueueOneTimeSync(appContext)
    }

    fun openAppSettings(threat: PrivacyThreat) {
        val intent = when {
            threat.settingsAction == Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> {
                Intent(threat.settingsAction).apply {
                    data = Uri.parse("package:${threat.packageName}")
                }
            }
            threat.settingsAction != null -> {
                Intent(threat.settingsAction)
            }
            else -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${threat.packageName}")
                }
            }
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            appContext.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            appContext.startActivity(fallback)
        }
    }

    fun openRemediationSettings(remediation: RemediationKnowledgeEntity) {
        val action = remediation.settingsIntentAction ?: Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val intent = if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS ||
            action == "android.settings.APPLICATION_DETAILS_SETTINGS") {
            Intent(action).apply {
                data = Uri.parse("package:${remediation.settingsIntentPackage ?: remediation.packageNamePattern}")
            }
        } else {
            Intent(action)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            appContext.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to app details
            try {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${remediation.settingsIntentPackage ?: remediation.packageNamePattern}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                appContext.startActivity(fallback)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                appContext.startActivity(fallback)
            }
        }
    }

    fun findRemediationForPackage(packageName: String): RemediationKnowledgeEntity? {
        val knowledge = uiState.value.remediationKnowledge
        return knowledge.firstOrNull { remediation ->
            packageName.contains(remediation.packageNamePattern) ||
                    remediation.packageNamePattern.contains(packageName)
        }
    }

    private fun startService() {
        val intent = Intent(appContext, PrivacyMonitorService::class.java)
        appContext.startForegroundService(intent)
    }

    private fun stopService() {
        val intent = Intent(appContext, PrivacyMonitorService::class.java)
        appContext.stopService(intent)
    }
}
