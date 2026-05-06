package com.safeharborsecurity.app.ui.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.local.entity.CameraAlertEntity
import com.safeharborsecurity.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Part E: Unified feed of camera/security app notifications captured by the
 * NotificationListenerService.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityHubScreen(
    onNavigateBack: () -> Unit,
    viewModel: SecurityHubViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                title = { Text("Security Hub", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.alerts.any { !it.isRead }) {
                        TextButton(onClick = { viewModel.markAllRead() }) {
                            Text("Mark all read")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CameraFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.activeFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            if (state.alerts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📷", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No camera alerts yet",
                        fontWeight = FontWeight.Bold,
                        color = NavyBlue,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "When your security cameras (Arlo, Reolink, Ring, Nest, Wyze, etc.) " +
                            "send notifications, they'll appear here in one place. Make sure " +
                            "Safe Companion has notification access enabled.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.alerts, key = { it.id }) { alert ->
                        CameraAlertCard(
                            alert = alert,
                            onTap = {
                                viewModel.markRead(alert.id)
                                openSourceApp(context, alert.sourcePackage)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraAlertCard(alert: CameraAlertEntity, onTap: () -> Unit) {
    val timeFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }
    val unreadColor = if (!alert.isRead) Color(0xFFE3F2FD) else LightSurface
    Card(
        onClick = onTap,
        colors = CardDefaults.cardColors(containerColor = unreadColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NavyBlue.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = alert.source,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyBlue
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    timeFmt.format(Date(alert.timestamp)),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                alert.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            if (alert.message.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    alert.message,
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

private fun openSourceApp(context: android.content.Context, packageName: String) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    } catch (_: Exception) {}
}
