package com.safeharborsecurity.app.ui.appchecker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.model.AlertTrigger
import com.safeharborsecurity.app.data.model.InstalledAppInfo
import com.safeharborsecurity.app.data.repository.AppAnalysisResult
import com.safeharborsecurity.app.data.repository.AppCheckerRepository
import com.safeharborsecurity.app.data.repository.PointsRepository
import com.safeharborsecurity.app.util.FamilyAlertManager
import com.safeharborsecurity.app.util.ScamCoachingManager
import com.safeharborsecurity.app.util.CoachingTip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppDetailUiState(
    val appInfo: InstalledAppInfo? = null,
    val isLoading: Boolean = true,
    val isAnalyzing: Boolean = false,
    val analysisResult: AppAnalysisResult? = null,
    val error: String? = null,
    val coachingTip: CoachingTip? = null
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AppCheckerRepository,
    private val prefs: UserPreferences,
    private val pointsRepository: PointsRepository,
    private val familyAlertManager: FamilyAlertManager,
    private val scamCoachingManager: ScamCoachingManager
) : ViewModel() {

    private val packageName: String = savedStateHandle.get<String>("packageName") ?: ""

    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    init {
        loadAppInfo()
    }

    private fun loadAppInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val info = repository.getAppInfo(packageName)
            _uiState.update { it.copy(appInfo = info, isLoading = false) }
        }
    }

    fun analyzeApp() {
        val app = _uiState.value.appInfo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null, analysisResult = null) }

            val apiKey = prefs.apiKey.first()
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(isAnalyzing = false, error = "Please add your API key in Settings first.") }
                return@launch
            }

            repository.analyzeApp(apiKey, app)
                .onSuccess { result ->
                    _uiState.update { it.copy(isAnalyzing = false, analysisResult = result) }

                    // Award safety points for checking an app
                    pointsRepository.awardPoints(
                        eventType = "APP_CHECKED",
                        points = 30,
                        description = "Checked app: ${app.appName}",
                        maxPerDay = 10
                    )

                    // Family alert for dangerous apps
                    if (result.verdict == "DANGEROUS") {
                        familyAlertManager.triggerAlert(
                            trigger = AlertTrigger.DANGEROUS_URL_VISITED, // Reuse closest trigger
                            confidence = 90
                        )
                    }

                    // Show coaching for suspicious/dangerous
                    if (result.verdict == "SUSPICIOUS" || result.verdict == "DANGEROUS") {
                        val tip = scamCoachingManager.getCoachingForScamType("APP")
                        _uiState.update { it.copy(coachingTip = tip) }
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isAnalyzing = false, error = "Could not check this app right now. Please try again.") }
                }
        }
    }

    fun dismissCoaching() {
        _uiState.update { it.copy(coachingTip = null) }
    }
}
