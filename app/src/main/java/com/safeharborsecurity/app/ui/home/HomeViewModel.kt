package com.safeharborsecurity.app.ui.home

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.dao.CheckInDao
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.data.local.entity.CheckInEntity
import com.safeharborsecurity.app.data.local.entity.NewsArticleEntity
import com.safeharborsecurity.app.data.remote.model.FamilyContact
import com.safeharborsecurity.app.data.repository.AlertRepository
import com.safeharborsecurity.app.data.repository.NewsRepository
import com.safeharborsecurity.app.data.repository.PointsRepository
import com.safeharborsecurity.app.data.repository.WifiSecurityRepository
import com.safeharborsecurity.app.data.repository.WifiStatus
import com.safeharborsecurity.app.service.PrivacyMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val isSmsShieldOn: Boolean = true,
    val isCallShieldOn: Boolean = true,
    val isEmailShieldOn: Boolean = true,
    val isListeningShieldOn: Boolean = false,
    val recentAlerts: List<AlertEntity> = emptyList(),
    val scamsBlockedThisMonth: Int = 0,
    val hasCheckedInToday: Boolean = false,
    val lastCheckInDate: String = "",
    val isNotificationListenerEnabled: Boolean = false,
    val lastScanTimestamp: Long = 0L,
    val isSimpleMode: Boolean = false,
    val primaryFamilyContactName: String? = null,
    val primaryFamilyContactPhone: String? = null,
    val dailyTip: String = "",
    /** Item 4 (option a): true when there's an unread weekly digest waiting. */
    val hasFreshWeeklyDigest: Boolean = false,
    /**
     * Screen monitor health for the home-screen status banner.
     *   - true  → user opted in AND the foreground service is running
     *   - false → user opted in BUT the service is dead (consent token lost,
     *             usually after the app was closed from Recents). Show a
     *             "tap to re-enable" banner.
     *   - null  → user hasn't opted in, no banner.
     */
    val screenMonitorOptedIn: Boolean = false,
    val screenMonitorActuallyRunning: Boolean = false
) {
    val screenMonitorNeedsReactivation: Boolean
        get() = screenMonitorOptedIn && !screenMonitorActuallyRunning
    val allShieldsOn: Boolean get() = isSmsShieldOn && isCallShieldOn && isEmailShieldOn
    val isScanningActive: Boolean get() = isNotificationListenerEnabled && lastScanTimestamp > 0L
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val alertRepository: AlertRepository,
    private val checkInDao: CheckInDao,
    private val wifiSecurityRepository: WifiSecurityRepository,
    private val newsRepository: NewsRepository,
    private val pointsRepository: PointsRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val today: String get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private val _dailyTip = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        prefs.userName,
        prefs.isSmsShieldEnabled,
        prefs.isCallShieldEnabled,
        prefs.isEmailShieldEnabled,
        prefs.isListeningShieldEnabled
    ) { name, sms, call, email, listening ->
        HomeUiState(
            userName = name,
            isSmsShieldOn = sms,
            isCallShieldOn = call,
            isEmailShieldOn = email,
            isListeningShieldOn = listening
        )
    }.combine(prefs.isSimpleMode) { state, simple ->
        state.copy(isSimpleMode = simple)
    }.combine(alertRepository.getRecentAlerts(10)) { state, alerts ->
        state.copy(recentAlerts = alerts)
    }.combine(prefs.lastCheckInDate) { state, lastDate ->
        state.copy(
            hasCheckedInToday = lastDate == today,
            lastCheckInDate = lastDate
        )
    }.combine(prefs.lastScanTimestamp) { state, scanTs ->
        val listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(appContext)
            .contains(appContext.packageName)
        state.copy(
            isNotificationListenerEnabled = listenerEnabled,
            lastScanTimestamp = scanTs
        )
    }.combine(prefs.familyContactsJson) { state, familyJson ->
        val contacts = try {
            Gson().fromJson<List<FamilyContact>>(familyJson, object : TypeToken<List<FamilyContact>>() {}.type)
                ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val primary = contacts.firstOrNull()
        state.copy(
            primaryFamilyContactName = primary?.nickname,
            primaryFamilyContactPhone = primary?.number
        )
    }.combine(_dailyTip) { state, tip ->
        state.copy(dailyTip = tip)
    }.combine(
        // Item 4 (option a): card visible iff opted-in AND there's a digest
        // generated more recently than the last dismissal.
        combine(
            prefs.isWeeklyDigestEnabled,
            prefs.weeklyDigestGeneratedAt,
            prefs.weeklyDigestDismissedAt
        ) { enabled, gen, dismissed -> enabled && gen > 0L && gen > dismissed }
    ) { state, fresh ->
        state.copy(hasFreshWeeklyDigest = fresh)
    }.combine(
        // Screen-monitor health: combine the user's opt-in flag (a stable
        // SharedPreferences mirror written when consent was granted) with the
        // service's live status flow. If the user opted in but the service
        // isn't running, the home-screen banner prompts them to re-enable.
        com.safeharborsecurity.app.service.ScreenScanService.status
    ) { state, screenStatus ->
        val optedIn = appContext.getSharedPreferences("safeharbor_runtime", 0)
            .getBoolean("screen_monitor_active", false)
        state.copy(
            screenMonitorOptedIn = optedIn,
            screenMonitorActuallyRunning = screenStatus.isRunning
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val wifiStatus: StateFlow<WifiStatus> = wifiSecurityRepository.wifiStatus

    val newsArticles: StateFlow<List<NewsArticleEntity>> = newsRepository.getRecentArticles(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun getNewsSourceColor(source: String): Long = newsRepository.getSourceColor(source)

    /** Wipes every saved news article. Next sync repopulates from RSS feeds. */
    fun clearAllNews() {
        viewModelScope.launch { newsRepository.clearAll() }
    }

    fun markNewsRead(id: String) {
        viewModelScope.launch { newsRepository.markAsRead(id) }
    }

    init {
        refreshScamCount()
        viewModelScope.launch { wifiSecurityRepository.checkCurrentWifi() }
        loadDailyTip()
    }

    private fun loadDailyTip() {
        viewModelScope.launch {
            val lastDate = prefs.lastTipDate.first()
            val todayDate = today
            val dismissed = prefs.isTipDismissedToday.first()
            if (lastDate == todayDate && dismissed) {
                _dailyTip.value = ""
                return@launch
            }
            try {
                val json = appContext.assets.open("safety_tips.json").bufferedReader().readText()
                val tips = Gson().fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
                if (tips.isNullOrEmpty()) return@launch

                val lastIndex = prefs.lastTipIndex.first()
                val nextIndex = if (lastDate != todayDate) (lastIndex + 1) % tips.size else lastIndex
                val tip = tips[nextIndex]["tip"] as? String ?: return@launch

                if (lastDate != todayDate) {
                    prefs.setLastTipDate(todayDate)
                    prefs.setLastTipIndex(nextIndex)
                    prefs.setTipDismissedToday(false)
                }
                _dailyTip.value = tip
            } catch (_: Exception) {
                _dailyTip.value = ""
            }
        }
    }

    private fun refreshScamCount() {
        viewModelScope.launch {
            val count = alertRepository.countScamsThisMonth()
            _scamsThisMonth.value = count
        }
    }

    private val _scamsThisMonth = MutableStateFlow(0)
    val scamsThisMonth: StateFlow<Int> = _scamsThisMonth.asStateFlow()

    fun setSmsShield(enabled: Boolean) { viewModelScope.launch { prefs.setSmsShield(enabled) } }
    fun setCallShield(enabled: Boolean) { viewModelScope.launch { prefs.setCallShield(enabled) } }
    fun setEmailShield(enabled: Boolean) { viewModelScope.launch { prefs.setEmailShield(enabled) } }
    fun markAlertRead(alertId: Long) { viewModelScope.launch { alertRepository.markAsRead(alertId) } }

    /** Clear-All for the home-screen messages list. Wipes every saved alert.
     *  The Flow that feeds recentAlerts will emit an empty list automatically. */
    fun clearAllAlerts() { viewModelScope.launch { alertRepository.deleteAllAlerts() } }

    fun setListeningShield(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setListeningShield(enabled)
            val intent = Intent(appContext, PrivacyMonitorService::class.java)
            if (enabled) {
                appContext.startForegroundService(intent)
                // 5 points for turning Listening Shield on. Capped at one
                // award per day so toggling on/off doesn't farm points.
                pointsRepository.awardPoints(
                    eventType = "LISTENING_SHIELD_ON",
                    points = 5,
                    description = "Turned on Listening Shield",
                    maxPerDay = 1
                )
            } else {
                appContext.stopService(intent)
            }
        }
    }

    fun toggleSimpleMode() {
        viewModelScope.launch {
            val current = uiState.value.isSimpleMode
            prefs.setSimpleMode(!current)
        }
    }

    fun dismissDailyTip() {
        viewModelScope.launch {
            prefs.setTipDismissedToday(true)
            _dailyTip.value = ""
        }
    }

    fun rateTip(helpful: Boolean) {
        // Future: adjust tip selection based on feedback
        viewModelScope.launch {
            // Reward engagement with the daily tip — 5 points, capped at one
            // award per day so users can't farm by repeatedly tapping.
            pointsRepository.awardPoints(
                eventType = "DAILY_TIP_RATED",
                points = 5,
                description = if (helpful) "Said this tip was helpful" else "Rated today's tip",
                maxPerDay = 1
            )
        }
        dismissDailyTip()
    }

    fun checkIn() {
        viewModelScope.launch {
            val todayDate = today
            val already = checkInDao.countForDate(todayDate) > 0
            if (!already) {
                checkInDao.insert(CheckInEntity(checkType = "MANUAL", date = todayDate))
            }
            prefs.setLastCheckInDate(todayDate)
        }
    }
}
