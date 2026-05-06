@file:Suppress("DEPRECATION")

package com.safeharborsecurity.app.ui.scanner

import android.Manifest
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.model.*
import com.safeharborsecurity.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenDeviceScannerScreen(
    onNavigateBack: () -> Unit,
    onWifiNetworkTapped: (String, String) -> Unit = { _, _ -> },
    viewModel: HiddenDeviceViewModel = hiltViewModel()
) {
    val report by viewModel.scanReport.collectAsStateWithLifecycle()
    val currentMethod by viewModel.currentMethod.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val mirrorResult by viewModel.mirrorResult.collectAsStateWithLifecycle()
    val isScanning = currentMethod != null
    val context = LocalContext.current

    // Permission launchers
    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshCapabilities() }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshCapabilities() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshCapabilities() }

    // Refresh capabilities on resume (after returning from system Settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCapabilities()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Flashlight state for mirror check
    var flashlightOn by remember { mutableStateOf(false) }
    val cameraManager = remember { context.getSystemService(CameraManager::class.java) }

    DisposableEffect(Unit) {
        onDispose {
            // Always turn off flashlight on exit
            if (flashlightOn) {
                try {
                    val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                    if (cameraId != null) cameraManager.setTorchMode(cameraId, false)
                } catch (_: Exception) {}
            }
        }
    }

    // Refresh capabilities on resume
    LaunchedEffect(Unit) { viewModel.refreshCapabilities() }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                title = { Text("Room Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        // Turn off flashlight before navigating back
                        if (flashlightOn) {
                            try {
                                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                                if (cameraId != null) cameraManager?.setTorchMode(cameraId, false)
                                flashlightOn = false
                            } catch (_: Exception) {}
                        }
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Header
            Text("\uD83D\uDD0D", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Hidden Device Scanner",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = NavyBlue,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Scan this room for hidden cameras, microphones, and other surveillance devices.",
                fontSize = 16.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(24.dp))

            // Summary verdict at top when complete
            if (report.isComplete) {
                ScanSummaryCard(report)
                Spacer(Modifier.height(16.dp))
            }

            // Start/Stop scan button
            if (!report.isComplete) {
                Button(
                    onClick = { if (isScanning) viewModel.cancelScan() else viewModel.startScan() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) ScamRed else NavyBlue
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Stop Scan", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Start Room Scan", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (isScanning && scanProgress.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(scanProgress, fontSize = 14.sp, color = NavyBlue, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(20.dp))

            // === AUTOMATED SCAN CARDS ===
            // WiFi — show permission card if location not granted
            val wifiCap = capabilities.find { it.type == ScanMethod.WIFI }
            if (wifiCap != null && wifiCap.isAvailable) {
                if (!wifiCap.permissionGranted && report.wifiResult.status == ScanStatus.NOT_STARTED) {
                    WifiPermissionCard { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                } else if (report.wifiResult.errorMessage == "PERMISSION_NEEDED") {
                    WifiPermissionCard { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                } else {
                    WifiScanCard(report.wifiResult, currentMethod == ScanMethod.WIFI, onWifiNetworkTapped)
                }
                Spacer(Modifier.height(12.dp))
            }

            // Bluetooth — show permission request if needed
            val btCap = capabilities.find { it.type == ScanMethod.BLUETOOTH }
            if (btCap != null && btCap.isAvailable) {
                if (!btCap.permissionGranted && report.bluetoothResult.errorMessage == "PERMISSION_NEEDED") {
                    BluetoothPermissionCard(btPermissionLauncher, context)
                } else if (!btCap.permissionGranted && report.bluetoothResult.status == ScanStatus.NOT_STARTED) {
                    BluetoothPermissionCard(btPermissionLauncher, context)
                } else {
                    ScanMethodCard(report.bluetoothResult, currentMethod == ScanMethod.BLUETOOTH)
                }
                Spacer(Modifier.height(12.dp))
            }

            // Magnetic — only if sensor exists
            if (capabilities.any { it.type == ScanMethod.MAGNETIC && it.isAvailable }) {
                ScanMethodCard(report.magneticResult, currentMethod == ScanMethod.MAGNETIC)
                Spacer(Modifier.height(12.dp))
            }

            // Ultrasonic — show permission request if needed
            val audioCap = capabilities.find { it.type == ScanMethod.ULTRASONIC }
            if (audioCap != null && audioCap.isAvailable) {
                if (!audioCap.permissionGranted && report.ultrasonicResult.status == ScanStatus.NOT_STARTED) {
                    AudioPermissionCard(audioPermissionLauncher)
                } else if (report.ultrasonicResult.errorMessage == "PERMISSION_NEEDED") {
                    AudioPermissionCard(audioPermissionLauncher)
                } else {
                    ScanMethodCard(report.ultrasonicResult, currentMethod == ScanMethod.ULTRASONIC)
                }
                Spacer(Modifier.height(12.dp))
            }

            // === INTERACTIVE SCAN CARDS ===

            // IR Camera — tappable card that opens camera preview
            if (capabilities.any { it.type == ScanMethod.INFRARED && it.isAvailable }) {
                IrScanCard()
                Spacer(Modifier.height(12.dp))
            }

            // Mirror Check — guided walkthrough
            MirrorCheckCard(
                mirrorResult = mirrorResult,
                flashlightOn = flashlightOn,
                onToggleFlashlight = {
                    try {
                        val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                        if (cameraId != null) {
                            flashlightOn = !flashlightOn
                            cameraManager?.setTorchMode(cameraId, flashlightOn)
                        }
                    } catch (_: Exception) {}
                },
                onResult = { result ->
                    viewModel.setMirrorResult(result)
                    // Turn off flashlight when done
                    if (flashlightOn) {
                        try {
                            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                            if (cameraId != null) cameraManager?.setTorchMode(cameraId, false)
                            flashlightOn = false
                        } catch (_: Exception) {}
                    }
                },
                onReset = { viewModel.resetMirrorCheck() }
            )
            Spacer(Modifier.height(12.dp))

            // Scan Again button when complete
            if (report.isComplete) {
                Button(
                    onClick = { viewModel.startScan() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan Again", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
            LimitationsCard()
            Spacer(Modifier.height(60.dp))
        }
    }
}

// === WiFi card with network list ===
@Composable
private fun WifiScanCard(
    result: ScanMethodResult,
    isScanning: Boolean,
    onWifiNetworkTapped: (String, String) -> Unit = { _, _ -> }
) {
    val bgColor = when (result.status) {
        ScanStatus.CLEAR -> SafeGreenLight
        ScanStatus.DETECTED -> WarningAmberLight
        ScanStatus.ERROR -> ScamRedLight
        ScanStatus.SCANNING -> Color(0xFFE3F2FD)
        else -> LightSurface
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isScanning) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(ScanMethod.WIFI.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(ScanMethod.WIFI.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
                    if (isScanning) Text(ScanMethod.WIFI.description, fontSize = 13.sp, color = TextSecondary)
                }
                StatusBadge(result.status, isScanning)
            }

            if (isScanning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NavyBlue, trackColor = Color(0xFFE0E0E0))
            }

            // Summary line
            result.errorMessage?.let { msg ->
                if (msg.isNotBlank() && result.status != ScanStatus.NOT_STARTED) {
                    Spacer(Modifier.height(8.dp))
                    Text(msg, fontSize = 14.sp, color = if (result.status == ScanStatus.ERROR) ScamRed else TextSecondary)
                }
            }

            // Network list
            if (result.wifiNetworks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                for (network in result.wifiNetworks) {
                    WifiNetworkRow(network) {
                        onWifiNetworkTapped(network.ssid, network.bssid)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            // Devices on this network (Part D1)
            if (result.networkDevices.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Devices on This Network",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyBlue
                )
                Spacer(Modifier.height(6.dp))
                result.networkDevices.forEach { dev ->
                    NetworkDeviceRow(dev)
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "If you're in a hotel or rental, devices you don't recognise could be hidden cameras. " +
                        "Ask the property manager about any connected devices.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            // Detection details
            if (result.detections.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                result.detections.forEach { detection ->
                    DetectionRow(detection)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun NetworkDeviceRow(dev: com.safeharborsecurity.app.data.model.NetworkDeviceInfo) {
    val color = when (dev.deviceType) {
        com.safeharborsecurity.app.data.model.DeviceType.CAMERA -> WarningAmber
        com.safeharborsecurity.app.data.model.DeviceType.IOT_LIKELY -> NavyBlue
        com.safeharborsecurity.app.data.model.DeviceType.UNKNOWN -> TextSecondary
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(dev.deviceType.emoji, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${dev.manufacturer} • ${dev.deviceType.displayLabel}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                "${dev.ipAddress}  ·  ${dev.macAddress}",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun WifiNetworkRow(network: WifiNetworkInfo, onTap: () -> Unit = {}) {
    val riskColor = when (network.riskLevel) {
        WifiRiskLevel.SAFE -> SafeGreen
        WifiRiskLevel.CAUTION -> WarningAmber
        WifiRiskLevel.SUSPICIOUS -> ScamRed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lock icon
        Icon(
            if (network.encryption == "Open") Icons.Default.LockOpen else Icons.Default.Lock,
            contentDescription = null,
            tint = riskColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))

        // SSID + optional "(Connected)" chip for the network the device is on
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                network.ssid,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = NavyBlue,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (network.isConnected) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SafeGreen.copy(alpha = 0.18f)
                ) {
                    Text(
                        "(Connected)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SafeGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
        }

        // Signal bars
        SignalBars(network.signalLevel, riskColor)
        Spacer(Modifier.width(6.dp))

        // Encryption pill
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = riskColor.copy(alpha = 0.15f)
        ) {
            Text(
                network.encryption,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = riskColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SignalBars(signalLevel: Int, color: Color) {
    val bars = when {
        signalLevel > -50 -> 4
        signalLevel > -60 -> 3
        signalLevel > -70 -> 2
        else -> 1
    }
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(16.dp)) {
        for (i in 1..4) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4 * i).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i <= bars) color else color.copy(alpha = 0.2f))
            )
            if (i < 4) Spacer(Modifier.width(1.dp))
        }
    }
}

// === Bluetooth permission card ===
@Composable
private fun BluetoothPermissionCard(
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    context: android.content.Context
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ScanMethod.BLUETOOTH.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Text(ScanMethod.BLUETOOTH.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "To scan for suspicious Bluetooth devices, Safe Companion needs permission to see nearby devices.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        launcher.launch(arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ))
                    } else {
                        launcher.launch(arrayOf(Manifest.permission.BLUETOOTH))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) {
                Text("Grant Bluetooth Permission", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }) {
                Text("Open Settings", fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}

// === Audio permission card ===
@Composable
private fun AudioPermissionCard(
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ScanMethod.ULTRASONIC.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Text(ScanMethod.ULTRASONIC.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "To listen for hidden ultrasonic signals, Safe Companion needs access to your microphone.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) {
                Text("Grant Microphone Permission", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// === IR Scan Card (opens camera) ===
@Composable
private fun IrScanCard() {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ScanMethod.INFRARED.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(ScanMethod.INFRARED.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
                    Text("Use your camera to look for infrared lights", fontSize = 13.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Hidden cameras often use infrared LEDs for night vision. Your phone camera may be able to see these invisible lights as purple or white dots.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "How to check: in a darkened room, slowly scan the room with your front camera. Look for any bright purple or white dots — those could be IR LEDs from a hidden camera.",
                fontSize = 14.sp, color = NavyBlue, lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            // Launches the system camera app. Some manufacturers honour
            // EXTRA_USE_FRONT_CAMERA to default to front-facing on open;
            // others ignore it but the user can still switch cameras inside
            // the camera app. If no camera app is present (rare), we Toast.
            Button(
                onClick = {
                    val intent = android.content.Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        putExtra("android.intent.extras.CAMERA_FACING", 1) // 1 = front camera (HUAWEI / older API)
                        putExtra("android.intent.extras.USE_FRONT_CAMERA", true)
                        putExtra("com.google.assistant.extra.USE_FRONT_CAMERA", true)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: android.content.ActivityNotFoundException) {
                        // Fall back to the generic still-image action
                        try {
                            context.startActivity(
                                android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: android.content.ActivityNotFoundException) {
                            android.widget.Toast.makeText(
                                context,
                                "No camera app found on this phone.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Open Camera to Scan",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Once the camera opens, switch to the front-facing camera if it isn't already, then walk slowly through the room with the lights off.",
                fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Works best in a darkened room. Results vary by phone model — about 70% of phones can see IR light.",
                fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp
            )
        }
    }
}

// === Mirror Check Card ===
@Composable
private fun MirrorCheckCard(
    mirrorResult: MirrorCheckResult,
    flashlightOn: Boolean,
    onToggleFlashlight: () -> Unit,
    onResult: (MirrorCheckResult) -> Unit,
    onReset: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(if (mirrorResult == MirrorCheckResult.NOT_STARTED) 0 else 4) }

    // Reset step when mirrorResult resets
    LaunchedEffect(mirrorResult) {
        if (mirrorResult == MirrorCheckResult.NOT_STARTED) currentStep = 0
    }

    val bgColor = when (mirrorResult) {
        MirrorCheckResult.SUSPICIOUS -> WarningAmberLight
        MirrorCheckResult.LOOKS_OK -> SafeGreenLight
        else -> LightSurface
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ScanMethod.MIRROR.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(ScanMethod.MIRROR.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
                    Text("Guided check — works on all phones", fontSize = 13.sp, color = TextSecondary)
                }
                if (mirrorResult != MirrorCheckResult.NOT_STARTED) {
                    StatusBadge(
                        if (mirrorResult == MirrorCheckResult.SUSPICIOUS) ScanStatus.DETECTED else ScanStatus.CLEAR,
                        false
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                // Show result
                mirrorResult == MirrorCheckResult.SUSPICIOUS -> {
                    Text(
                        "\u26A0\uFE0F This mirror may be one-way glass. One-way mirrors are sometimes used to hide cameras. Consider requesting a different room or contacting the property manager.",
                        fontSize = 14.sp, color = NavyBlue, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onReset) {
                        Text("Check Another Mirror", color = NavyBlue)
                    }
                }
                mirrorResult == MirrorCheckResult.LOOKS_OK -> {
                    Text(
                        "\u2705 This mirror appears to be a standard mirror.",
                        fontSize = 14.sp, color = NavyBlue, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onReset) {
                        Text("Check Another Mirror", color = NavyBlue)
                    }
                }
                // Step-through guide
                currentStep == 0 -> {
                    Text(
                        "Check any mirror in the room for one-way glass.",
                        fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { currentStep = 1 },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                    ) {
                        Text("Start Mirror Check", fontWeight = FontWeight.Bold)
                    }
                }
                currentStep == 1 -> {
                    Text(
                        "Step 1 of 3",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Turn off the lights or find the darkest corner in the room.",
                        fontSize = 15.sp, color = NavyBlue, lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { currentStep = 2 },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                    ) { Text("Next", fontWeight = FontWeight.Bold) }
                }
                currentStep == 2 -> {
                    Text(
                        "Step 2 of 3",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Hold your phone flashlight directly against the mirror surface.",
                        fontSize = 15.sp, color = NavyBlue, lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onToggleFlashlight,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (flashlightOn) WarningAmber else Color(0xFF00897B)
                        )
                    ) {
                        Icon(
                            if (flashlightOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (flashlightOn) "Turn Off Flashlight" else "Turn On Flashlight",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { currentStep = 3 },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                    ) { Text("Next", fontWeight = FontWeight.Bold) }
                }
                currentStep == 3 -> {
                    Text(
                        "Step 3 of 3",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Look through the mirror toward the light. A real mirror appears dark behind the glass. A one-way mirror lets you see through to the other side.",
                        fontSize = 15.sp, color = NavyBlue, lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("What do you see?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onResult(MirrorCheckResult.SUSPICIOUS) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                    ) {
                        Text("I can see through it", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onResult(MirrorCheckResult.LOOKS_OK) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                    ) {
                        Text("It looks dark/solid", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (mirrorResult == MirrorCheckResult.NOT_STARTED || mirrorResult == MirrorCheckResult.SUSPICIOUS || mirrorResult == MirrorCheckResult.LOOKS_OK) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "This is a visual guide only, not a definitive test.",
                    fontSize = 12.sp, color = TextSecondary
                )
            }
        }
    }
}

// === Generic scan method card ===
@Composable
private fun ScanMethodCard(result: ScanMethodResult, isCurrentlyScanning: Boolean) {
    val bgColor = when (result.status) {
        ScanStatus.CLEAR -> SafeGreenLight
        ScanStatus.DETECTED -> WarningAmberLight
        ScanStatus.ERROR -> ScamRedLight
        ScanStatus.SCANNING -> Color(0xFFE3F2FD)
        else -> LightSurface
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentlyScanning) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(result.method.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.method.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
                    if (isCurrentlyScanning) Text(result.method.description, fontSize = 13.sp, color = TextSecondary)
                }
                StatusBadge(result.status, isCurrentlyScanning)
            }

            if (isCurrentlyScanning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NavyBlue, trackColor = Color(0xFFE0E0E0))
                if (result.method == ScanMethod.MAGNETIC) {
                    Spacer(Modifier.height(4.dp))
                    Text("Slowly move your phone around the room...", fontSize = 13.sp, color = NavyBlue, fontWeight = FontWeight.SemiBold)
                }
                if (result.method == ScanMethod.ULTRASONIC) {
                    Spacer(Modifier.height(4.dp))
                    Text("Keep the room quiet for best results...", fontSize = 13.sp, color = NavyBlue, fontWeight = FontWeight.SemiBold)
                }
            }

            if ((result.status == ScanStatus.ERROR || result.status == ScanStatus.SKIPPED) &&
                result.errorMessage != null && result.errorMessage != "PERMISSION_NEEDED") {
                Spacer(Modifier.height(8.dp))
                Text(result.errorMessage, fontSize = 14.sp, color = TextSecondary)
            }

            if (result.detections.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                result.detections.forEach { detection ->
                    DetectionRow(detection)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ScanStatus, isScanning: Boolean) {
    val (text, color) = when {
        isScanning -> "Scanning" to NavyBlue
        status == ScanStatus.CLEAR -> "Clear" to SafeGreen
        status == ScanStatus.DETECTED -> "Detected" to WarningAmber
        status == ScanStatus.ERROR -> "Error" to ScamRed
        status == ScanStatus.SKIPPED -> "Skipped" to TextSecondary
        else -> "Ready" to TextSecondary
    }

    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = color, strokeWidth = 1.5.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun DetectionRow(detection: Detection) {
    val iconColor = when (detection.severity) {
        DetectionSeverity.DANGER -> ScamRed
        DetectionSeverity.WARNING -> WarningAmber
        DetectionSeverity.INFO -> TextSecondary
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            when (detection.severity) {
                DetectionSeverity.DANGER -> Icons.Default.Warning
                else -> Icons.Default.Info
            },
            contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(detection.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NavyBlue)
            Text(detection.detail, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ScanSummaryCard(report: RoomScanReport) {
    val clearCount = report.allMethods.count { it.status == ScanStatus.CLEAR }
    val detectedCount = report.allMethods.count { it.status == ScanStatus.DETECTED }
    val totalDetections = report.allMethods.sumOf { it.detections.size }
    val ranCount = report.allMethods.count { it.status != ScanStatus.NOT_STARTED && it.status != ScanStatus.SKIPPED }

    val (emoji, title, subtitle, bgColor) = if (detectedCount == 0) {
        listOf("\uD83D\uDEE1\uFE0F", "Room Appears Safe", "No threats detected in this scan.", SafeGreenLight)
    } else {
        listOf("\u26A0\uFE0F", "$totalDetections Potential Issue(s) Found",
            "Review the details below. This does not mean there is definitely a hidden device — but it is worth investigating.",
            WarningAmberLight)
    }

    Card(colors = CardDefaults.cardColors(containerColor = bgColor as Color), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji as String, fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text(title as String, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NavyBlue, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(subtitle as String, fontSize = 15.sp, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 22.sp)
            Spacer(Modifier.height(12.dp))
            Text("$clearCount of $ranCount automated scans clear", fontSize = 14.sp, color = TextSecondary)
        }
    }
}

// === WiFi permission card ===
@Composable
private fun WifiPermissionCard(onRequestPermission: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ScanMethod.WIFI.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Text(ScanMethod.WIFI.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "To scan for suspicious WiFi networks, Safe Companion needs permission to see nearby networks.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Grant Location Permission", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "WiFi scanning requires location permission to detect nearby networks.",
                fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun LimitationsCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECEFF1)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("About This Scan", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            val limitations = listOf(
                "Safe Companion uses your phone's built-in sensors to look for signs of hidden devices. These scans can raise awareness but are not guaranteed to find every hidden camera or microphone.",
                "Infrared detection works on about 70% of phone cameras — some phones filter out IR light.",
                "Magnetic detection can be triggered by large metal objects, appliances, or building wiring.",
                "Network device scanning can find cameras connected to the same WiFi network. It cannot detect cameras on separate networks, cellular cameras, or cameras that store footage locally without WiFi.",
                "Ultrasonic detection can pick up signals from air conditioning, electronics, or hearing aids.",
                "Professional security sweeps use specialized equipment. If you have serious concerns, contact local law enforcement."
            )
            limitations.forEach { text ->
                Row(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text("\u2022", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(text, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                }
            }
        }
    }
}
