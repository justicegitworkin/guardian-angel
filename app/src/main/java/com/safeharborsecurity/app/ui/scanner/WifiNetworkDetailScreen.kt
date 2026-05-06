package com.safeharborsecurity.app.ui.scanner

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.model.WifiNetworkInfo
import com.safeharborsecurity.app.data.model.WifiRiskLevel
import com.safeharborsecurity.app.ui.theme.*

/**
 * Part C: Detail screen for a specific WiFi network from the Room Scanner.
 * Shows encryption status, risk assessment, plain-English advice, and a VPN
 * launch button (or Play Store link if no VPN app is installed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiNetworkDetailScreen(
    ssid: String,
    bssid: String,
    onNavigateBack: () -> Unit,
    viewModel: WifiNetworkDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showVpnDialog by remember { mutableStateOf<List<InstalledVpn>?>(null) }
    var showNoVpnDialog by remember { mutableStateOf(false) }

    LaunchedEffect(ssid, bssid) { viewModel.load(ssid, bssid) }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                title = { Text("Network Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)
            )
        }
    ) { padding ->
        val network = state.network
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (network == null) {
                Text("Loading network details...", color = TextSecondary)
                return@Column
            }

            NetworkIdentityCard(network)
            EncryptionStatusCard(network)
            RiskAssessmentCard(network, state.legitimacySignals)
            AdviceCard(network)

            // VPN button
            Button(
                onClick = {
                    val installed = findInstalledVpns(context)
                    when {
                        installed.size == 1 -> launchVpn(context, installed[0].packageName)
                        installed.size > 1 -> showVpnDialog = installed
                        else -> showNoVpnDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) {
                Icon(Icons.Default.Shield, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enable VPN Protection", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            // WiFi settings
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = NavyBlue)
                Spacer(Modifier.width(8.dp))
                Text("Open WiFi Settings", color = NavyBlue, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    showVpnDialog?.let { apps ->
        AlertDialog(
            onDismissRequest = { showVpnDialog = null },
            title = { Text("Choose VPN App") },
            text = { Text("You have several VPN apps installed. Which would you like to use?") },
            confirmButton = {
                Column {
                    apps.forEach { vpn ->
                        TextButton(onClick = {
                            launchVpn(context, vpn.packageName)
                            showVpnDialog = null
                        }) { Text(vpn.label) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showVpnDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (showNoVpnDialog) {
        AlertDialog(
            onDismissRequest = { showNoVpnDialog = false },
            title = { Text("No VPN App Found") },
            text = {
                Text("A VPN encrypts your internet connection so others on the same WiFi cannot " +
                    "see what you do. We recommend installing one from the Google Play Store.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showNoVpnDialog = false
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/search?q=VPN&c=apps")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    } catch (_: Exception) {}
                }) { Text("Open Play Store") }
            },
            dismissButton = {
                TextButton(onClick = { showNoVpnDialog = false }) { Text("Not now") }
            }
        )
    }
}

@Composable
private fun NetworkIdentityCard(network: WifiNetworkInfo) {
    val signalLabel = when {
        network.signalLevel > -50 -> "Strong"
        network.signalLevel > -70 -> "Moderate"
        else -> "Weak"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📶", fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "“${network.ssid}”",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyBlue
                    )
                    Text(
                        text = "Signal: $signalLabel (${network.signalLevel} dBm)",
                        fontSize = 14.sp, color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun EncryptionStatusCard(network: WifiNetworkInfo) {
    val (label, description, bg, fg) = when (network.encryption) {
        "WPA3" -> Quad("WPA3 Encrypted — Very Secure",
            "This network uses the latest WiFi security. Strong protection.",
            SafeGreenLight, SafeGreen)
        "WPA2" -> Quad("WPA2 Encrypted — Secure",
            "This network is properly secured. Modern protection.",
            SafeGreenLight, SafeGreen)
        "WPA" -> Quad("WPA Encrypted — Older Security",
            "This is an older form of WiFi security. Avoid sensitive activities.",
            WarningAmberLight, WarningAmber)
        "WEP" -> Quad("WEP Encrypted — Weak Security",
            "WEP is broken and can be cracked easily. Treat this like an open network.",
            ScamRedLight, ScamRed)
        else -> Quad("OPEN NETWORK — No Encryption",
            "Anyone nearby can see what you do on this network.",
            ScamRedLight, ScamRed)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Encryption Status", fontWeight = FontWeight.Bold, color = NavyBlue)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold, color = fg, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(description, fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun RiskAssessmentCard(network: WifiNetworkInfo, legitimacySignals: List<String>) {
    val (riskLabel, fg) = when (network.riskLevel) {
        WifiRiskLevel.SUSPICIOUS -> "HIGH" to ScamRed
        WifiRiskLevel.CAUTION -> "MEDIUM" to WarningAmber
        WifiRiskLevel.SAFE -> "LOW" to SafeGreen
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Risk Assessment", fontWeight = FontWeight.Bold, color = NavyBlue)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(12.dp).clip(CircleShape).background(fg)
                )
                Spacer(Modifier.width(8.dp))
                Text("Risk Level: $riskLabel", fontWeight = FontWeight.SemiBold, color = fg)
            }
            Spacer(Modifier.height(8.dp))
            val reasons = buildList {
                if (network.riskReason.isNotBlank()) add(network.riskReason)
                addAll(legitimacySignals)
            }
            if (reasons.isEmpty()) {
                Text("No specific concerns. This network appears properly secured.",
                    fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp)
            } else {
                reasons.forEach { reason ->
                    Row(modifier = Modifier.padding(bottom = 4.dp)) {
                        Text("• ", fontSize = 14.sp, color = TextSecondary)
                        Text(reason, fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdviceCard(network: WifiNetworkInfo) {
    val tips = when (network.riskLevel) {
        WifiRiskLevel.SUSPICIOUS -> listOf(
            "Use a VPN if you need to use this network",
            "Don't log into bank accounts or enter passwords",
            "Don't enter credit card numbers",
            "Ask staff for the official WiFi name to confirm this is real"
        )
        WifiRiskLevel.CAUTION -> listOf(
            "Use a VPN for any sensitive browsing",
            "Avoid online banking on this network",
            "If you don't recognise this network, don't connect"
        )
        WifiRiskLevel.SAFE -> listOf(
            "This network looks properly secured",
            "Still avoid joining if you don't recognise the name",
            "When in doubt, mobile data is safer than unfamiliar WiFi"
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💡", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text("What You Should Do", fontWeight = FontWeight.Bold, color = NavyBlue)
            }
            Spacer(Modifier.height(8.dp))
            tips.forEachIndexed { i, tip ->
                Row(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text("${i + 1}. ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NavyBlue)
                    Text(tip, fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
                }
            }
        }
    }
}

private data class Quad(val l: String, val d: String, val bg: Color, val fg: Color)

data class InstalledVpn(val packageName: String, val label: String)

private val KNOWN_VPN_PACKAGES = listOf(
    "com.expressvpn.vpn" to "ExpressVPN",
    "com.nordvpn.android" to "NordVPN",
    "com.protonvpn.android" to "ProtonVPN",
    "org.strongswan.android" to "strongSwan",
    "com.surfshark.vpnclient.android" to "Surfshark",
    "com.wireguard.android" to "WireGuard",
    "com.privateinternetaccess.android" to "Private Internet Access",
    "com.cyberghost.vpn" to "CyberGhost",
    "com.tunnelbear.android" to "TunnelBear",
    "com.mullvad.mullvadvpn" to "Mullvad"
)

private fun findInstalledVpns(context: android.content.Context): List<InstalledVpn> {
    val pm = context.packageManager
    return KNOWN_VPN_PACKAGES.mapNotNull { (pkg, label) ->
        runCatching { pm.getPackageInfo(pkg, 0); InstalledVpn(pkg, label) }.getOrNull()
    }
}

private fun launchVpn(context: android.content.Context, packageName: String) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    } catch (_: PackageManager.NameNotFoundException) {}
}
