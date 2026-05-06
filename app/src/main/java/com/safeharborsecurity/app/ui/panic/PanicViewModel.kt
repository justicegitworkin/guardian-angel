package com.safeharborsecurity.app.ui.panic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.dao.PanicEventDao
import com.safeharborsecurity.app.data.local.entity.PanicEventEntity
import com.safeharborsecurity.app.data.remote.model.FamilyContact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PanicUiState(
    val userName: String = "",
    val bankPhoneNumber: String = "",
    val familyContactNumbers: List<String> = emptyList()
)

@HiltViewModel
class PanicViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val panicEventDao: PanicEventDao,
    private val gson: Gson
) : ViewModel() {

    private val familyType = object : TypeToken<List<FamilyContact>>() {}.type

    val uiState: StateFlow<PanicUiState> = combine(
        prefs.userName,
        prefs.bankPhoneNumber,
        prefs.familyContactsJson
    ) { name, bank, familyJson ->
        val contacts = runCatching {
            gson.fromJson<List<FamilyContact>>(familyJson, familyType) ?: emptyList()
        }.getOrDefault(emptyList())

        PanicUiState(
            userName = name,
            bankPhoneNumber = bank,
            familyContactNumbers = contacts.map { it.number }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanicUiState())

    fun recordPanicEvent() {
        viewModelScope.launch {
            panicEventDao.insert(PanicEventEntity())
        }
    }
}
