package com.guardianangel.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.guardianangel.app.data.datastore.UserPreferences
import com.guardianangel.app.data.local.entity.AlertEntity
import com.guardianangel.app.data.remote.model.FamilyContact
import com.guardianangel.app.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val isSmsShieldOn: Boolean = true,
    val isCallShieldOn: Boolean = true,
    val isEmailShieldOn: Boolean = true,
    val recentAlerts: List<AlertEntity> = emptyList(),
    val scamsBlockedThisMonth: Int = 0,
    val familyContacts: List<FamilyContact> = emptyList()
) {
    val allShieldsOn: Boolean get() = isSmsShieldOn && isCallShieldOn && isEmailShieldOn
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val alertRepository: AlertRepository,
    private val gson: Gson
) : ViewModel() {

    private val familyType = object : TypeToken<List<FamilyContact>>() {}.type

    private data class HomeShields(
        val name: String, val sms: Boolean, val call: Boolean, val email: Boolean
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            prefs.userName,
            prefs.isSmsShieldEnabled,
            prefs.isCallShieldEnabled,
            prefs.isEmailShieldEnabled
        ) { name, sms, call, email -> HomeShields(name, sms, call, email) },
        alertRepository.getRecentAlerts(10),
        prefs.familyContactsJson
    ) { shields, alerts, familyJson ->
        val contacts = runCatching {
            gson.fromJson<List<FamilyContact>>(familyJson, familyType) ?: emptyList()
        }.getOrDefault(emptyList())
        HomeUiState(
            userName = shields.name,
            isSmsShieldOn = shields.sms,
            isCallShieldOn = shields.call,
            isEmailShieldOn = shields.email,
            recentAlerts = alerts,
            familyContacts = contacts
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refreshScamCount()
    }

    private fun refreshScamCount() {
        viewModelScope.launch {
            val count = alertRepository.countScamsThisMonth()
            // Merge count into the current state snapshot
            uiState.value // trigger collection; count update emitted via a dedicated MutableStateFlow below
            _scamsThisMonth.value = count
        }
    }

    private val _scamsThisMonth = MutableStateFlow(0)

    // Expose the count as a separate simple flow so the UI can read it without coupling into the
    // main combine (which re-emits on every pref change and would re-query the DB unnecessarily).
    val scamsThisMonth: StateFlow<Int> = _scamsThisMonth.asStateFlow()

    fun setSmsShield(enabled: Boolean) { viewModelScope.launch { prefs.setSmsShield(enabled) } }
    fun setCallShield(enabled: Boolean) { viewModelScope.launch { prefs.setCallShield(enabled) } }
    fun setEmailShield(enabled: Boolean) { viewModelScope.launch { prefs.setEmailShield(enabled) } }
    fun markAlertRead(alertId: Long) { viewModelScope.launch { alertRepository.markAsRead(alertId) } }
    fun recordCallFriendTap() { viewModelScope.launch { prefs.recordCallFriendTap() } }
}
