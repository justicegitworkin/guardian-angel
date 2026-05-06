package com.safeharborsecurity.app.data.model

data class RoomScanReport(
    val wifiResult: ScanMethodResult = ScanMethodResult(ScanMethod.WIFI),
    val bluetoothResult: ScanMethodResult = ScanMethodResult(ScanMethod.BLUETOOTH),
    val magneticResult: ScanMethodResult = ScanMethodResult(ScanMethod.MAGNETIC),
    val ultrasonicResult: ScanMethodResult = ScanMethodResult(ScanMethod.ULTRASONIC),
    val isComplete: Boolean = false
) {
    val hasAnyDetections: Boolean get() =
        allMethods.any { it.detections.isNotEmpty() }

    val allMethods: List<ScanMethodResult> get() =
        listOf(wifiResult, bluetoothResult, magneticResult, ultrasonicResult)
}

data class ScanMethodResult(
    val method: ScanMethod,
    val status: ScanStatus = ScanStatus.NOT_STARTED,
    val detections: List<Detection> = emptyList(),
    val errorMessage: String? = null,
    val wifiNetworks: List<WifiNetworkInfo> = emptyList(),
    val networkDevices: List<NetworkDeviceInfo> = emptyList()
)

/**
 * Part D1: A device discovered on the local network during the WiFi scan.
 */
data class NetworkDeviceInfo(
    val ipAddress: String,
    val macAddress: String,
    val manufacturer: String,        // e.g. "Hikvision", "Wyze", "Unknown"
    val deviceType: DeviceType,      // CAMERA / IOT_LIKELY / UNKNOWN
    val confidence: Int              // 0..100
)

enum class DeviceType(val emoji: String, val displayLabel: String) {
    CAMERA("📷", "Possible Camera"),
    IOT_LIKELY("📦", "Smart Device"),
    UNKNOWN("❔", "Unknown Device")
}

data class Detection(
    val name: String,
    val detail: String,
    val severity: DetectionSeverity = DetectionSeverity.WARNING
)

data class WifiNetworkInfo(
    val ssid: String,
    val bssid: String,
    val signalLevel: Int,
    val encryption: String,
    val riskLevel: WifiRiskLevel,
    val riskReason: String,
    /** True if this is the network the device is currently connected to. */
    val isConnected: Boolean = false
)

enum class WifiRiskLevel { SAFE, CAUTION, SUSPICIOUS }

data class ScanCapability(
    val type: ScanMethod,
    val isAvailable: Boolean,
    val requiresPermission: Boolean,
    val permissionGranted: Boolean
)

enum class ScanMethod(val displayName: String, val emoji: String, val description: String) {
    WIFI("WiFi Scan", "\uD83D\uDCF6", "Scanning for hidden cameras on your WiFi network"),
    BLUETOOTH("Bluetooth Scan", "\uD83D\uDCF1", "Checking for suspicious Bluetooth devices"),
    MAGNETIC("Magnetic Scan", "\uD83E\uDDF2", "Detecting electronic devices hidden nearby"),
    INFRARED("Infrared Scan", "\uD83D\uDD34", "Looking for invisible infrared lights from cameras"),
    MIRROR("Mirror Check", "\uD83E\uDE9E", "Guided check for one-way mirrors"),
    ULTRASONIC("Audio Sweep", "\uD83D\uDD0A", "Listening for ultrasonic signals from hidden devices")
}

enum class ScanStatus {
    NOT_STARTED,
    SCANNING,
    CLEAR,
    DETECTED,
    ERROR,
    SKIPPED
}

enum class DetectionSeverity {
    INFO,
    WARNING,
    DANGER
}

enum class MirrorCheckResult {
    NOT_STARTED,
    IN_PROGRESS,
    SUSPICIOUS,
    LOOKS_OK
}
