package com.safeharborsecurity.app.ui.safety

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.safeharborsecurity.app.ble.DetectedTracker
import com.safeharborsecurity.app.ble.TrackerBleParser
import com.safeharborsecurity.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Item 3 — Manual "Scan for trackers nearby" screen.
 *
 * State machine:
 *   IDLE → CHECK_PERMISSIONS → CHECK_BLUETOOTH_ON → SCANNING(0..30) → DONE
 *
 * Each transition has its own visible UI so the user always knows what's
 * happening. The earlier version of this screen ran the BT-on check inside
 * the LaunchedEffect that drove the countdown, which meant a BT-off state
 * caused the scanning UI to flash for one frame and immediately disappear —
 * no visible feedback. This rewrite gates each precondition with explicit
 * UI before the LaunchedEffect ever starts.
 */
private const val TAG = "TrackerScan"
private const val SCAN_DURATION_SECONDS = 30

private enum class ScanPhase { IDLE, SCANNING, DONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScanScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    var phase by remember { mutableStateOf(ScanPhase.IDLE) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var permissionDenied by remember { mutableStateOf(false) }
    var bluetoothOff by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    // Use a state list so callback writes from the BLE scanner thread
    // propagate to compose without us doing the read-modify-write dance.
    val detected = remember { mutableStateListOf<DetectedTracker>() }

    // Stable ScanCallback — recreated only when its closure changes (it doesn't).
    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val tracker = TrackerBleParser.parse(result) ?: return
                if (detected.none { it.mac == tracker.mac }) {
                    detected.add(tracker)
                    Log.d(TAG, "Tracker detected: ${tracker.kind} mac=${tracker.mac} rssi=${tracker.rssi}")
                }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed: errorCode=$errorCode")
                scanError = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> null  // benign
                    SCAN_FAILED_FEATURE_UNSUPPORTED ->
                        "This phone's Bluetooth doesn't support tracker scanning."
                    SCAN_FAILED_INTERNAL_ERROR ->
                        "Bluetooth had a problem. Try turning Bluetooth off and on, then scan again."
                    else -> "Could not start the scan (error $errorCode)."
                }
            }
        }
    }

    // Bluetooth-on check helper (called from button click and after BT-enable
    // returns from the system).
    fun isBluetoothOn(): Boolean {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = mgr?.adapter
        return adapter != null && adapter.isEnabled
    }

    // System intent to enable Bluetooth — pre-Android-12 needed BLUETOOTH_ADMIN
    // permission, modern Android exposes ACTION_REQUEST_ENABLE which the user
    // can decline.
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isBluetoothOn()) {
            bluetoothOff = false
            // Auto-start the scan once BT comes on so the user doesn't have
            // to tap Scan twice.
            phase = ScanPhase.SCANNING
        } else {
            bluetoothOff = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        permissionDenied = granted.values.any { !it }
        if (!permissionDenied) {
            // Permissions are good — now check BT state.
            if (isBluetoothOn()) {
                phase = ScanPhase.SCANNING
            } else {
                bluetoothOff = true
            }
        }
    }

    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasAllPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // --- Drive the actual scan only while phase == SCANNING ---
    LaunchedEffect(phase) {
        if (phase != ScanPhase.SCANNING) return@LaunchedEffect
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = mgr?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            scanError = "This phone's Bluetooth isn't ready. Try toggling Bluetooth off and on."
            phase = ScanPhase.DONE
            return@LaunchedEffect
        }
        detected.clear()
        elapsedSeconds = 0
        scanError = null
        Log.d(TAG, "Starting BLE scan for $SCAN_DURATION_SECONDS seconds")
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            @Suppress("MissingPermission")
            scanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "startScan SecurityException", e)
            permissionDenied = true
            phase = ScanPhase.DONE
            return@LaunchedEffect
        } catch (e: Exception) {
            Log.w(TAG, "startScan threw", e)
            scanError = "Could not start the scan. Please try again."
            phase = ScanPhase.DONE
            return@LaunchedEffect
        }
        try {
            while (elapsedSeconds < SCAN_DURATION_SECONDS) {
                delay(1000)
                elapsedSeconds += 1
            }
        } finally {
            try {
                @Suppress("MissingPermission")
                scanner.stopScan(scanCallback)
            } catch (_: Exception) {}
            Log.d(TAG, "Scan complete: ${detected.size} trackers detected")
            phase = ScanPhase.DONE
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan for Trackers Nearby", color = NavyBlue) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NavyBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WarmWhite)
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                "📡 Looking for AirTags, Tiles, SmartTags and other tracking " +
                    "devices that might be hidden in your bag or car.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            Spacer(Modifier.height(20.dp))

            // --- Action / state region ---
            when {
                bluetoothOff -> {
                    BluetoothOffCard(
                        onEnable = {
                            enableBluetoothLauncher.launch(
                                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            )
                        }
                    )
                }
                permissionDenied -> {
                    PermissionDeniedCard()
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            permissionDenied = false
                            permissionLauncher.launch(requiredPermissions())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Try again", color = Color.White)
                    }
                }
                phase == ScanPhase.SCANNING -> {
                    ScanningCard(elapsed = elapsedSeconds, total = SCAN_DURATION_SECONDS)
                }
                else -> {
                    // IDLE or DONE — show the start/restart button.
                    Button(
                        onClick = {
                            scanError = null
                            when {
                                !hasAllPermissions() -> permissionLauncher.launch(requiredPermissions())
                                !isBluetoothOn() -> bluetoothOff = true
                                else -> phase = ScanPhase.SCANNING
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (phase == ScanPhase.DONE) "Scan Again" else "Start Scan ($SCAN_DURATION_SECONDS seconds)",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            scanError?.let { err ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        err,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NavyBlue
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // --- Results header ---
            if (phase == ScanPhase.DONE) {
                ResultsHeader(count = detected.size)
                Spacer(Modifier.height(12.dp))
            }

            // --- Empty state when scan finished and nothing found ---
            if (phase == ScanPhase.DONE && detected.isEmpty() && scanError == null) {
                EmptyStateCard()
            }

            // --- Live results during scanning + final list when done ---
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(detected, key = { it.mac }) { tracker ->
                    TrackerRow(tracker)
                }
            }

            Spacer(Modifier.height(20.dp))

            // --- Always-visible honest caveat ---
            Card(
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "What this scan can and cannot do",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = NavyBlue
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "We can see Bluetooth trackers within about 30 feet that " +
                            "are advertising right now. Some trackers go quiet when " +
                            "their owner is nearby, so a clean scan doesn't mean a " +
                            "tracker is definitely not in your space — only that we " +
                            "didn't see one in this 30-second window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothOffCard(onEnable: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Bluetooth is turned off",
                style = MaterialTheme.typography.titleMedium,
                color = NavyBlue,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Safe Companion needs Bluetooth on to scan for trackers nearby. " +
                    "Tap the button below and Android will ask if you want to turn " +
                    "it on. The scan will start automatically afterwards.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onEnable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Turn on Bluetooth and scan", color = Color.White)
            }
        }
    }
}

@Composable
private fun PermissionDeniedCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            "To scan for trackers, Safe Companion needs Bluetooth permission. " +
                "Tap Try again below and choose Allow.",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = NavyBlue
        )
    }
}

@Composable
private fun ScanningCard(elapsed: Int, total: Int) {
    val progress = (elapsed.toFloat() / total).coerceIn(0f, 1f)
    val remaining = (total - elapsed).coerceAtLeast(0)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavyBlue.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$remaining seconds remaining",
                style = MaterialTheme.typography.headlineMedium,
                color = NavyBlue,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = NavyBlue
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Scanning Bluetooth nearby. Walk slowly around the room while we scan, " +
                    "in case a tracker is hidden somewhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ResultsHeader(count: Int) {
    val (color, message) = when (count) {
        0 -> SafeGreen to "✓ No tracking devices detected nearby"
        1 -> WarningAmber to "1 tracker found nearby"
        else -> WarningAmber to "$count trackers found nearby"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SafeGreen.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            "We didn't see any tracking devices in this scan. If you're worried " +
                "about a tracker following you, try scanning again from a different " +
                "spot, or after walking 100 feet from where you started.",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = NavyBlue
        )
    }
}

@Composable
private fun TrackerRow(tracker: DetectedTracker) {
    val proximityLabel = TrackerBleParser.proximityLabel(tracker.rssi)
    val warningLevel = TrackerBleParser.proximityWarningLevel(tracker.rssi)
    val accent = when (warningLevel) {
        2 -> ScamRed
        1 -> WarningAmber
        else -> SafeGreen
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(tracker.kind.emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tracker.kind.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = NavyBlue
                )
                Text(
                    if (tracker.name.isNullOrBlank()) tracker.mac else "${tracker.name} • ${tracker.mac}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.18f)
                ) {
                    Text(
                        proximityLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
