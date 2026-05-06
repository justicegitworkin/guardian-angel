package com.safeharborsecurity.app.ui.wifi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.repository.WifiSafety
import com.safeharborsecurity.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: WifiDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wifiStatus = state.wifiStatus

    val statusColor = when (wifiStatus.safety) {
        WifiSafety.SECURE -> SafeGreen
        WifiSafety.CAUTION -> WarningAmber
        WifiSafety.UNSAFE -> ScamRed
        WifiSafety.NOT_ON_WIFI -> TextSecondary
    }

    val statusBgColor = when (wifiStatus.safety) {
        WifiSafety.SECURE -> SafeGreenLight
        WifiSafety.CAUTION -> WarningAmberLight
        WifiSafety.UNSAFE -> ScamRedLight
        WifiSafety.NOT_ON_WIFI -> LightSurface
    }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
                title = {
                    Text(
                        "WiFi Security",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = statusBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "${wifiStatus.safety.emoji} ${wifiStatus.safety.label}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                    if (wifiStatus.networkName.isNotBlank() && wifiStatus.safety != WifiSafety.NOT_ON_WIFI) {
                        Text(
                            "Network: ${wifiStatus.networkName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                        if (wifiStatus.securityType.isNotBlank()) {
                            Text(
                                "Security: ${wifiStatus.securityType}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Recommendation
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        wifiStatus.recommendation,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            }

            // Trust button (only when connected to WiFi)
            if (wifiStatus.safety != WifiSafety.NOT_ON_WIFI && wifiStatus.networkName.isNotBlank()) {
                if (state.isTrusted) {
                    OutlinedButton(
                        onClick = { viewModel.removeTrusted(wifiStatus.networkName) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ScamRed)
                    ) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Remove from Trusted Networks",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.markAsTrusted(wifiStatus.networkName) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Mark as Trusted Network",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }

            // Safety tips
            Text(
                "Public WiFi Safety Tips",
                style = MaterialTheme.typography.headlineSmall,
                color = NavyBlue,
                modifier = Modifier.padding(top = 8.dp)
            )

            SafetyTipCard(
                emoji = "\uD83D\uDEAB",
                title = "Avoid online banking",
                description = "Never log in to your bank or enter financial details on public WiFi."
            )
            SafetyTipCard(
                emoji = "\uD83D\uDD12",
                title = "Don't enter passwords",
                description = "Wait until you're on a trusted network before signing in to accounts."
            )
            SafetyTipCard(
                emoji = "\uD83D\uDED2",
                title = "Don't shop online",
                description = "Avoid entering credit card numbers while on public WiFi."
            )
            SafetyTipCard(
                emoji = "\uD83D\uDCF1",
                title = "Use mobile data instead",
                description = "Your phone's mobile data connection is usually much safer than public WiFi."
            )
            SafetyTipCard(
                emoji = "\u2753",
                title = "Ask a trusted person",
                description = "If you're not sure about a WiFi network, ask a family member or friend before connecting."
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SafetyTipCard(
    emoji: String,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(emoji, fontSize = 24.sp)
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
