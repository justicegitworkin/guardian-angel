package com.safeharborsecurity.app.ui.messages

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.local.dao.ScamTipDao
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.data.local.entity.ScamTipEntity
import com.safeharborsecurity.app.data.repository.AlertRepository
import com.safeharborsecurity.app.data.repository.EmailAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MessageFilter { ALL, SCAMS, WARNINGS, SAFE }
// SOCIAL tab dropped — nothing in the codebase produces "SOCIAL" alerts and
// no detector path is feeding it. If we ever add a social-media-specific
// scanner, re-add the value and a corresponding alerts query.
enum class MessageTab { TEXTS, EMAILS }

data class MessagesUiState(
    val alerts: List<AlertEntity> = emptyList(),
    val emailAlerts: List<AlertEntity> = emptyList(),
    val filter: MessageFilter = MessageFilter.ALL,
    val tab: MessageTab = MessageTab.TEXTS,
    val recentTips: List<ScamTipEntity> = emptyList(),
    val isNotificationListenerEnabled: Boolean = false,
    val emailAccountCount: Int = 0
) {
    val activeAlerts: List<AlertEntity> get() = when (tab) {
        MessageTab.TEXTS -> alerts
        MessageTab.EMAILS -> emailAlerts
    }

    val filtered: List<AlertEntity> get() = when (filter) {
        MessageFilter.ALL -> activeAlerts
        MessageFilter.SCAMS -> activeAlerts.filter { it.riskLevel == "SCAM" }
        MessageFilter.WARNINGS -> activeAlerts.filter { it.riskLevel == "WARNING" }
        MessageFilter.SAFE -> activeAlerts.filter { it.riskLevel == "SAFE" }
    }
}

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val scamTipDao: ScamTipDao,
    private val emailAccountRepository: EmailAccountRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _filter = MutableStateFlow(MessageFilter.ALL)
    private val _tab = MutableStateFlow(MessageTab.TEXTS)

    val uiState: StateFlow<MessagesUiState> = combine(
        combine(
            // Texts tab now combines legacy SMS alerts (from the disabled
            // NotificationListener path) with SCREEN_SMS alerts produced by
            // the screen-monitor pipeline. To the user there's no meaningful
            // difference — both are "scams in your messages" — so they
            // belong in the same list.
            alertRepository.getAlertsByTypes(listOf("SMS", "SCREEN_SMS")),
            alertRepository.getAlertsByType("EMAIL"),
            emailAccountRepository.getAccounts()
        ) { texts, email, emailAccounts -> arrayOf(texts, email, emailAccounts) },
        _filter,
        _tab,
        scamTipDao.getRecent(10)
    ) { alertArrays, filter, tab, tips ->
        @Suppress("UNCHECKED_CAST")
        val textAlerts = alertArrays[0] as List<AlertEntity>
        @Suppress("UNCHECKED_CAST")
        val emailAlerts = alertArrays[1] as List<AlertEntity>
        val emailAccountCount = (alertArrays[2] as List<*>).size
        val listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(appContext)
            .contains(appContext.packageName)
        MessagesUiState(
            alerts = textAlerts,
            emailAlerts = emailAlerts,
            filter = filter,
            tab = tab,
            recentTips = tips,
            isNotificationListenerEnabled = listenerEnabled,
            emailAccountCount = emailAccountCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessagesUiState())

    fun setFilter(filter: MessageFilter) { _filter.value = filter }
    fun setTab(tab: MessageTab) {
        _tab.value = tab
        _filter.value = MessageFilter.ALL
    }

    fun blockSender(sender: String) {
        viewModelScope.launch {
            alertRepository.blockNumber(sender, reason = "Manually blocked by user")
        }
    }

    fun markAsRead(alertId: Long) {
        viewModelScope.launch { alertRepository.markAsRead(alertId) }
    }

    fun markTipAsRead(tipId: Long) {
        viewModelScope.launch { scamTipDao.markAsRead(tipId) }
    }
}
