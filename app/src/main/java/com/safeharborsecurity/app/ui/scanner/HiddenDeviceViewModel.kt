package com.safeharborsecurity.app.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.model.*
import com.safeharborsecurity.app.data.repository.HiddenDeviceScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HiddenDeviceViewModel @Inject constructor(
    private val scanRepository: HiddenDeviceScanRepository
) : ViewModel() {

    val scanReport: StateFlow<RoomScanReport> = scanRepository.scanReport
    val currentMethod: StateFlow<ScanMethod?> = scanRepository.currentMethod
    val scanProgress: StateFlow<String> = scanRepository.scanProgress

    private val _capabilities = MutableStateFlow<List<ScanCapability>>(emptyList())
    val capabilities: StateFlow<List<ScanCapability>> = _capabilities.asStateFlow()

    private val _mirrorResult = MutableStateFlow(MirrorCheckResult.NOT_STARTED)
    val mirrorResult: StateFlow<MirrorCheckResult> = _mirrorResult.asStateFlow()

    init {
        refreshCapabilities()
    }

    fun refreshCapabilities() {
        _capabilities.value = scanRepository.getCapabilities()
    }

    fun startScan() {
        refreshCapabilities()
        scanRepository.startFullScan(viewModelScope)
    }

    fun cancelScan() {
        scanRepository.cancelScan()
    }

    fun setMirrorResult(result: MirrorCheckResult) {
        _mirrorResult.value = result
    }

    fun resetMirrorCheck() {
        _mirrorResult.value = MirrorCheckResult.NOT_STARTED
    }

    fun isCapabilityAvailable(method: ScanMethod): Boolean {
        return _capabilities.value.any { it.type == method && it.isAvailable }
    }

    fun hasPermissionForScan(method: ScanMethod): Boolean {
        return _capabilities.value.any { it.type == method && it.permissionGranted }
    }

    override fun onCleared() {
        super.onCleared()
        scanRepository.cancelScan()
    }
}
