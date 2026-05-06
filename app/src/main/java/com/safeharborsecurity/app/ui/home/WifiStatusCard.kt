package com.safeharborsecurity.app.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeharborsecurity.app.data.repository.WifiSafety
import com.safeharborsecurity.app.data.repository.WifiStatus
import com.safeharborsecurity.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun WifiStatusCard(
    wifiStatus: WifiStatus,
    onClick: () -> Unit
) {
    // Part F4: VPN status indicator. Polls every 10s while the card is composed.
    val context = LocalContext.current
    var vpnActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            vpnActive = isVpnActive(context)
            delay(10_000)
        }
    }
    val statusColor = when (wifiStatus.safety) {
        WifiSafety.SECURE -> SafeGreen
        WifiSafety.CAUTION -> WarningAmber
        WifiSafety.UNSAFE -> ScamRed
        WifiSafety.NOT_ON_WIFI -> TextSecondary
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = statusColor, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${wifiStatus.safety.emoji} ${wifiStatus.safety.label}",
                    style = MaterialTheme.typography.titleSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                if (wifiStatus.networkName.isNotBlank() && wifiStatus.safety != WifiSafety.NOT_ON_WIFI) {
                    Text(
                        wifiStatus.networkName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // F4: VPN status pill — only shown when on WiFi
            if (wifiStatus.safety != WifiSafety.NOT_ON_WIFI) {
                VpnStatusPill(
                    vpnActive = vpnActive,
                    isUnsafeWifi = wifiStatus.safety == WifiSafety.UNSAFE ||
                        wifiStatus.safety == WifiSafety.CAUTION
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun VpnStatusPill(vpnActive: Boolean, isUnsafeWifi: Boolean) {
    val (label, fg, bg) = when {
        vpnActive -> Triple("🛡️ VPN", SafeGreen, SafeGreenLight)
        isUnsafeWifi -> Triple("⚠️ No VPN", WarningAmber, WarningAmberLight)
        else -> return
    }
    Surface(shape = RoundedCornerShape(10.dp), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg)
        }
    }
}

private fun isVpnActive(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val active = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(active) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
}
