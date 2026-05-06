@file:Suppress("DEPRECATION", "MissingPermission")

package com.safeharborsecurity.app.data.repository

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.safeharborsecurity.app.data.model.*
import com.safeharborsecurity.app.util.NetworkDeviceScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sqrt

@Singleton
class HiddenDeviceScanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkDeviceScanner: NetworkDeviceScanner
) {
    companion object {
        private const val TAG = "HiddenDeviceScan"

        private val CAMERA_SSID_PATTERNS = listOf(
            "ipcam", "ip cam", "ip_cam", "ipcamera", "hicamera",
            "vstarcam", "v380", "v380pro",
            "yi-home", "yi_home", "yihome",
            "wyze", "wyzecam",
            "reolink", "rlc-",
            "hikvision", "hik-", "hiipc",
            "dahua", "ipc-",
            "amcrest", "anke", "annke",
            "foscam", "tenvis", "sricam",
            "gocamera", "icamera",
            "cam_", "camera_", "camera", "hidden", "spy",
            "nanny", "babycam", "minicam",
            "dvr_", "nvr_", "cctv"
        )

        private val CAMERA_OUI_PREFIXES = listOf(
            "3c:a3:08", "bc:ad:28", "c0:56:e3", // Hikvision
            "28:57:be", "54:c4:15",               // Hikvision
            "90:02:a9", "4c:11:ae",               // Dahua
            "a4:14:37", "3c:ef:8c",               // Dahua
            "ec:71:db", "48:02:2a",               // Reolink
            "00:40:8c", "ac:cc:8e",               // Axis
            "e0:50:8b", "38:af:d7",               // Amcrest
            "c4:3c:b0",                            // Foscam
            "00:62:6e",                            // Yi Camera
            "2c:aa:8e", "d0:3f:27"                 // Wyze
        )

        private const val BASELINE_DURATION_MS = 3000L
        private const val SPIKE_THRESHOLD_UT = 25f

        private val ULTRASONIC_FREQUENCIES = doubleArrayOf(
            18000.0, 18500.0, 19000.0, 19500.0,
            20000.0, 20500.0, 21000.0, 21200.0
        )
        private const val ULTRASONIC_BASELINE_FREQ = 1000.0
        private const val ULTRASONIC_THRESHOLD_MULTIPLIER = 8.0
        private const val ULTRASONIC_RECORD_SECONDS = 5
    }

    private val _scanReport = MutableStateFlow(RoomScanReport())
    val scanReport: StateFlow<RoomScanReport> = _scanReport.asStateFlow()

    private val _currentMethod = MutableStateFlow<ScanMethod?>(null)
    val currentMethod: StateFlow<ScanMethod?> = _currentMethod.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress: StateFlow<String> = _scanProgress.asStateFlow()

    private var scanJob: Job? = null

    fun getCapabilities(): List<ScanCapability> {
        val caps = mutableListOf<ScanCapability>()

        // WiFi — always available
        caps.add(ScanCapability(ScanMethod.WIFI, true, true,
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)))

        // Bluetooth
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAvailable = btManager?.adapter != null
        val btPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        caps.add(ScanCapability(ScanMethod.BLUETOOTH, btAvailable, true, hasPermission(btPermission)))

        // Magnetic — only if hardware exists
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasMag = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
        caps.add(ScanCapability(ScanMethod.MAGNETIC, hasMag, false, true))

        // Ultrasonic — needs RECORD_AUDIO
        caps.add(ScanCapability(ScanMethod.ULTRASONIC, true, true,
            hasPermission(Manifest.permission.RECORD_AUDIO)))

        // IR — needs camera
        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        caps.add(ScanCapability(ScanMethod.INFRARED, hasCamera, true,
            hasPermission(Manifest.permission.CAMERA)))

        // Mirror — always available, no permissions
        caps.add(ScanCapability(ScanMethod.MIRROR, true, false, true))

        return caps
    }

    fun startFullScan(scope: CoroutineScope) {
        scanJob?.cancel()
        _scanReport.value = RoomScanReport()
        scanJob = scope.launch {
            val availableScans = getCapabilities().filter { it.isAvailable && it.permissionGranted }
            val totalScans = availableScans.count { it.type != ScanMethod.INFRARED && it.type != ScanMethod.MIRROR }
            var scanIndex = 0

            // WiFi (now also scans the local subnet for connected devices — Part D1)
            if (availableScans.any { it.type == ScanMethod.WIFI }) {
                scanIndex++
                _scanProgress.value = "Scanning WiFi... ($scanIndex of $totalScans)"
                _currentMethod.value = ScanMethod.WIFI
                var result = runWifiScan()
                _scanReport.value = _scanReport.value.copy(wifiResult = result)

                // Discover devices on the local network
                _scanProgress.value = "Looking for devices on your network..."
                val devices = runCatching {
                    networkDeviceScanner.discoverLocalDevices()
                }.getOrElse { emptyList() }
                if (devices.isNotEmpty()) {
                    val cameraDetections = devices
                        .filter { it.deviceType == DeviceType.CAMERA }
                        .map { dev ->
                            Detection(
                                name = "${dev.deviceType.emoji} ${dev.deviceType.displayLabel} — ${dev.manufacturer}",
                                detail = "Connected at ${dev.ipAddress} (MAC ${dev.macAddress}). " +
                                    "If you don't recognise this device in your home or rental, " +
                                    "ask the property manager about any connected cameras.",
                                severity = DetectionSeverity.WARNING
                            )
                        }
                    result = result.copy(
                        networkDevices = devices,
                        detections = result.detections + cameraDetections,
                        status = if (result.status == ScanStatus.CLEAR && cameraDetections.isNotEmpty())
                            ScanStatus.DETECTED else result.status
                    )
                    _scanReport.value = _scanReport.value.copy(wifiResult = result)
                }
            }

            // Bluetooth
            if (availableScans.any { it.type == ScanMethod.BLUETOOTH }) {
                scanIndex++
                _scanProgress.value = "Scanning Bluetooth... ($scanIndex of $totalScans)"
                _currentMethod.value = ScanMethod.BLUETOOTH
                val result = runBluetoothScan()
                _scanReport.value = _scanReport.value.copy(bluetoothResult = result)
            }

            // Magnetic
            if (availableScans.any { it.type == ScanMethod.MAGNETIC }) {
                scanIndex++
                _scanProgress.value = "Scanning magnetic field... ($scanIndex of $totalScans)"
                _currentMethod.value = ScanMethod.MAGNETIC
                val result = runMagneticScan()
                _scanReport.value = _scanReport.value.copy(magneticResult = result)
            }

            // Ultrasonic
            if (availableScans.any { it.type == ScanMethod.ULTRASONIC }) {
                scanIndex++
                _scanProgress.value = "Listening for ultrasonic signals... ($scanIndex of $totalScans)"
                _currentMethod.value = ScanMethod.ULTRASONIC
                val result = runUltrasonicScan()
                _scanReport.value = _scanReport.value.copy(ultrasonicResult = result)
            }

            _scanReport.value = _scanReport.value.copy(isComplete = true)
            _currentMethod.value = null
            _scanProgress.value = ""
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _currentMethod.value = null
        _scanProgress.value = ""
    }

    fun runWifiScan(): ScanMethodResult {
        val method = ScanMethod.WIFI
        try {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                return ScanMethodResult(method, ScanStatus.ERROR,
                    errorMessage = "Location permission needed to scan WiFi networks")
            }

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return ScanMethodResult(method, ScanStatus.ERROR, errorMessage = "WiFi not available")

            // Use passive scan results (no startScan trigger)
            val results = wifiManager.scanResults ?: emptyList()

            if (results.isEmpty()) {
                return ScanMethodResult(method, ScanStatus.CLEAR,
                    errorMessage = "No WiFi networks detected. Make sure WiFi is turned on.")
            }

            val networks = mutableListOf<WifiNetworkInfo>()
            val detections = mutableListOf<Detection>()

            // Find the SSID/BSSID the device is currently connected to so we
            // can tag that row "(Connected)" in the UI. WifiManager strips the
            // surrounding quotes inconsistently across Android versions, so
            // we normalise both here.
            @Suppress("DEPRECATION")
            val connInfo = runCatching { wifiManager.connectionInfo }.getOrNull()
            val connectedSsid = connInfo?.ssid
                ?.removePrefix("\"")?.removeSuffix("\"")
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }
            val connectedBssid = connInfo?.bssid?.lowercase()
                ?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }

            for (result in results) {
                val ssid = result.SSID ?: ""
                val ssidLower = ssid.lowercase()
                val mac = result.BSSID?.lowercase() ?: ""
                val macPrefix = mac.take(8)
                val capabilities = result.capabilities ?: ""
                val signal = result.level

                // Determine encryption
                val encryption = when {
                    capabilities.contains("WPA3") -> "WPA3"
                    capabilities.contains("WPA2") -> "WPA2"
                    capabilities.contains("WPA") -> "WPA"
                    capabilities.contains("WEP") -> "WEP"
                    else -> "Open"
                }

                // Determine risk level
                val matchedSsid = CAMERA_SSID_PATTERNS.find { ssidLower.contains(it) }
                val matchedOui = CAMERA_OUI_PREFIXES.find { macPrefix == it }
                val isHiddenSsid = ssid.isBlank()
                val isOpen = !capabilities.contains("WPA") && !capabilities.contains("WEP")
                val isVeryStrong = signal > -40

                val (riskLevel, riskReason) = when {
                    matchedOui != null -> WifiRiskLevel.SUSPICIOUS to "MAC address matches known camera manufacturer"
                    matchedSsid != null -> WifiRiskLevel.SUSPICIOUS to "Network name matches camera pattern \"$matchedSsid\""
                    isOpen -> WifiRiskLevel.SUSPICIOUS to "Open network — no encryption"
                    isHiddenSsid -> WifiRiskLevel.CAUTION to "Hidden network name"
                    encryption == "WEP" -> WifiRiskLevel.CAUTION to "Weak WEP encryption"
                    encryption == "WPA" && !capabilities.contains("WPA2") -> WifiRiskLevel.CAUTION to "Older WPA encryption"
                    isVeryStrong && ssidLower.isNotBlank() -> WifiRiskLevel.SAFE to "Strong signal, encrypted"
                    else -> WifiRiskLevel.SAFE to "Encrypted network"
                }

                val displaySsid = if (ssid.isBlank()) "Hidden Network" else ssid

                // BSSID match is the reliable signal — multiple APs can share
                // an SSID. Fall back to SSID match for older devices that
                // don't expose BSSID via connectionInfo without permission.
                val isConnected = (connectedBssid != null && connectedBssid == mac) ||
                    (connectedBssid == null && connectedSsid != null && connectedSsid == ssid)

                networks.add(
                    WifiNetworkInfo(
                        ssid = displaySsid,
                        bssid = mac,
                        signalLevel = signal,
                        encryption = encryption,
                        riskLevel = riskLevel,
                        riskReason = riskReason,
                        isConnected = isConnected
                    )
                )

                if (riskLevel == WifiRiskLevel.SUSPICIOUS) {
                    detections.add(Detection(
                        name = displaySsid,
                        detail = riskReason + " (Signal: ${signal}dBm)",
                        severity = if (matchedOui != null) DetectionSeverity.DANGER else DetectionSeverity.WARNING
                    ))
                }
            }

            val suspiciousCount = networks.count { it.riskLevel == WifiRiskLevel.SUSPICIOUS }
            Log.d(TAG, "WiFi scan: ${results.size} networks, $suspiciousCount suspicious")

            return ScanMethodResult(
                method,
                if (detections.isEmpty()) ScanStatus.CLEAR else ScanStatus.DETECTED,
                detections,
                errorMessage = "${results.size} networks found.${if (suspiciousCount > 0) " $suspiciousCount look suspicious." else ""}",
                wifiNetworks = networks.sortedWith(compareBy<WifiNetworkInfo> {
                    when (it.riskLevel) { WifiRiskLevel.SUSPICIOUS -> 0; WifiRiskLevel.CAUTION -> 1; WifiRiskLevel.SAFE -> 2 }
                }.thenByDescending { it.signalLevel })
            )
        } catch (e: Exception) {
            Log.e(TAG, "WiFi scan failed: ${e.message}")
            return ScanMethodResult(method, ScanStatus.ERROR, errorMessage = "WiFi scan failed: ${e.message}")
        }
    }

    fun runBluetoothScan(): ScanMethodResult {
        val method = ScanMethod.BLUETOOTH
        try {
            val btPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_CONNECT
            } else {
                Manifest.permission.BLUETOOTH
            }
            if (!hasPermission(btPermission)) {
                return ScanMethodResult(method, ScanStatus.ERROR,
                    errorMessage = "PERMISSION_NEEDED")
            }

            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter
                ?: return ScanMethodResult(method, ScanStatus.ERROR, errorMessage = "Bluetooth not available")

            if (!adapter.isEnabled) {
                return ScanMethodResult(method, ScanStatus.CLEAR,
                    errorMessage = "Bluetooth is turned off")
            }

            val bondedDevices = adapter.bondedDevices ?: emptySet()
            val detections = mutableListOf<Detection>()

            val suspiciousNames = listOf(
                "camera", "cam", "ipcam", "spy", "hidden",
                "nanny", "dvr", "nvr", "cctv", "recorder",
                "tracker", "gps", "bug", "monitor"
            )

            val genericNames = listOf(
                "unknown", "device", "bluetooth", "ble",
                "free", "gift", "prize"
            )

            for (device in bondedDevices) {
                val name = try { device.name?.lowercase() ?: "" } catch (_: SecurityException) { "" }
                val address = try { device.address ?: "" } catch (_: SecurityException) { "" }

                val cameraMatch = suspiciousNames.find { name.contains(it) }
                if (cameraMatch != null) {
                    detections.add(Detection(
                        name = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" },
                        detail = "Device name contains suspicious keyword \"$cameraMatch\"",
                        severity = DetectionSeverity.WARNING
                    ))
                    continue
                }

                val genericMatch = genericNames.find { name.contains(it) }
                if (genericMatch != null || name.matches(Regex("^[a-f0-9]{4,}$"))) {
                    detections.add(Detection(
                        name = try { device.name ?: address } catch (_: SecurityException) { address },
                        detail = "Generic or unnamed device — could be anything",
                        severity = DetectionSeverity.INFO
                    ))
                }
            }

            Log.d(TAG, "Bluetooth scan: ${bondedDevices.size} paired devices, ${detections.size} flagged")
            return ScanMethodResult(
                method,
                if (detections.isEmpty()) ScanStatus.CLEAR else ScanStatus.DETECTED,
                detections
            )
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth scan failed: ${e.message}")
            return ScanMethodResult(method, ScanStatus.ERROR, errorMessage = "Bluetooth scan failed: ${e.message}")
        }
    }

    suspend fun runMagneticScan(): ScanMethodResult = withContext(Dispatchers.Main) {
        val method = ScanMethod.MAGNETIC
        try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: return@withContext ScanMethodResult(method, ScanStatus.ERROR, errorMessage = "Sensors not available")

            val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
                ?: return@withContext ScanMethodResult(method, ScanStatus.SKIPPED)

            val readings = mutableListOf<Float>()
            var baselineComplete = false
            var baseline = 0f
            val spikes = mutableListOf<Float>()

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val magnitude = sqrt(
                        event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                    )
                    if (!baselineComplete) {
                        readings.add(magnitude)
                    } else {
                        val deviation = magnitude - baseline
                        if (deviation > SPIKE_THRESHOLD_UT) {
                            spikes.add(magnitude)
                        }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)

            delay(BASELINE_DURATION_MS)
            baselineComplete = true
            baseline = if (readings.isNotEmpty()) readings.average().toFloat() else 0f
            Log.d(TAG, "Magnetic baseline: ${baseline}\u00B5T from ${readings.size} readings")

            delay(5000)

            sensorManager.unregisterListener(listener)

            val detections = if (spikes.isNotEmpty()) {
                val maxSpike = spikes.max()
                listOf(Detection(
                    name = "Magnetic anomaly detected",
                    detail = "Found ${spikes.size} spike(s) above baseline. Peak: ${String.format("%.1f", maxSpike)}\u00B5T (baseline: ${String.format("%.1f", baseline)}\u00B5T). This could indicate hidden electronics.",
                    severity = if (maxSpike - baseline > 50f) DetectionSeverity.DANGER else DetectionSeverity.WARNING
                ))
            } else emptyList()

            ScanMethodResult(
                method,
                if (detections.isEmpty()) ScanStatus.CLEAR else ScanStatus.DETECTED,
                detections
            )
        } catch (e: Exception) {
            Log.e(TAG, "Magnetic scan failed: ${e.message}")
            ScanMethodResult(method, ScanStatus.ERROR, errorMessage = "Magnetic scan failed: ${e.message}")
        }
    }

    suspend fun runUltrasonicScan(): ScanMethodResult = withContext(Dispatchers.IO) {
        val method = ScanMethod.ULTRASONIC
        try {
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                return@withContext ScanMethodResult(method, ScanStatus.ERROR,
                    errorMessage = "PERMISSION_NEEDED")
            }

            val sampleRate = 44100
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                return@withContext ScanMethodResult(method, ScanStatus.ERROR,
                    errorMessage = "Audio recording not available on this device")
            }

            val totalSamples = sampleRate * ULTRASONIC_RECORD_SECONDS
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(bufferSize, totalSamples * 2)
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@withContext ScanMethodResult(method, ScanStatus.ERROR,
                    errorMessage = "Could not initialize audio recording")
            }

            val audioData = ShortArray(totalSamples)
            recorder.startRecording()
            var offset = 0
            while (offset < totalSamples) {
                val read = recorder.read(audioData, offset, minOf(bufferSize / 2, totalSamples - offset))
                if (read <= 0) break
                offset += read
            }
            recorder.stop()
            recorder.release()

            // Convert to doubles
            val samples = DoubleArray(offset) { audioData[it].toDouble() / Short.MAX_VALUE }

            // Get baseline energy at 1000Hz
            val baselineEnergy = goertzel(samples, ULTRASONIC_BASELINE_FREQ, sampleRate)
            val threshold = baselineEnergy * ULTRASONIC_THRESHOLD_MULTIPLIER

            // Check each ultrasonic frequency
            val detectedFreqs = mutableListOf<Pair<Double, Double>>()
            for (freq in ULTRASONIC_FREQUENCIES) {
                val energy = goertzel(samples, freq, sampleRate)
                if (energy > threshold && baselineEnergy > 0) {
                    detectedFreqs.add(freq to energy)
                }
            }

            val detections = if (detectedFreqs.isNotEmpty()) {
                val strongest = detectedFreqs.maxByOrNull { it.second }!!
                listOf(Detection(
                    name = "Unusual high-frequency signal detected",
                    detail = "Detected signal around ${strongest.first.toInt()} Hz. " +
                        "${detectedFreqs.size} ultrasonic frequency band(s) active. " +
                        "Some surveillance devices emit signals in this range. " +
                        "Air conditioning and electronics can also cause this.",
                    severity = if (detectedFreqs.size >= 3) DetectionSeverity.DANGER else DetectionSeverity.WARNING
                ))
            } else emptyList()

            Log.d(TAG, "Ultrasonic scan: ${detectedFreqs.size} frequencies detected, baseline=$baselineEnergy")

            ScanMethodResult(
                method,
                if (detections.isEmpty()) ScanStatus.CLEAR else ScanStatus.DETECTED,
                detections
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ultrasonic scan failed: ${e.message}")
            ScanMethodResult(method, ScanStatus.ERROR, errorMessage = "Audio sweep failed: ${e.message}")
        }
    }

    private fun goertzel(samples: DoubleArray, targetFreq: Double, sampleRate: Int): Double {
        val n = samples.size
        if (n == 0) return 0.0
        val k = (0.5 + n * targetFreq / sampleRate).toInt()
        val omega = 2.0 * Math.PI * k / n
        val coeff = 2.0 * cos(omega)
        var q1 = 0.0
        var q2 = 0.0
        for (sample in samples) {
            val q0 = coeff * q1 - q2 + sample
            q2 = q1
            q1 = q0
        }
        return q1 * q1 + q2 * q2 - q1 * q2 * coeff
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
