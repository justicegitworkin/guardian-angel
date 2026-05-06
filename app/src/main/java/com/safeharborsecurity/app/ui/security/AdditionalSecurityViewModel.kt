package com.safeharborsecurity.app.ui.security

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.dao.ConnectedServiceDao
import com.safeharborsecurity.app.data.local.entity.ConnectedServiceEntity
import com.safeharborsecurity.app.service.HaveIBeenPwnedWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class AdditionalSecurityUiState(
    val services: List<ConnectedServiceEntity> = emptyList(),
    val hibpApiKey: String = "",
    val isChecking: Boolean = false
)

@HiltViewModel
class AdditionalSecurityViewModel @Inject constructor(
    private val connectedServiceDao: ConnectedServiceDao,
    private val prefs: UserPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val uiState: StateFlow<AdditionalSecurityUiState> = combine(
        connectedServiceDao.getAll(),
        prefs.hibpApiKey
    ) { services, apiKey ->
        AdditionalSecurityUiState(
            services = services,
            hibpApiKey = apiKey
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdditionalSecurityUiState())

    fun setHibpApiKey(key: String) {
        viewModelScope.launch {
            prefs.setHibpApiKey(key)
        }
    }

    fun checkNow() {
        viewModelScope.launch {
            val workRequest = OneTimeWorkRequestBuilder<HaveIBeenPwnedWorker>()
                .build()
            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(
                    HaveIBeenPwnedWorker.WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }
    }

    fun schedulePeriodicCheck() {
        val workRequest = PeriodicWorkRequestBuilder<HaveIBeenPwnedWorker>(
            7, TimeUnit.DAYS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(appContext)
            .enqueueUniquePeriodicWork(
                HaveIBeenPwnedWorker.WORK_NAME + "_periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }

    fun getHibpService(): ConnectedServiceEntity? {
        return uiState.value.services.find { it.serviceId == "hibp" }
    }
}
