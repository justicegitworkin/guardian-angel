package com.safeharborsecurity.app.ui.messages

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import com.safeharborsecurity.app.ui.home.AlertCard
import com.safeharborsecurity.app.ui.theme.*

@Composable
fun MessagesScreen(
    onNavigateBack: () -> Unit,
    onOpenSafeHarbor: (String) -> Unit,
    onNavigateToEmailSetup: () -> Unit = {},
    viewModel: MessagesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            MessagesTopBar(
                tab = state.tab,
                onBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Text Messages / Emails tab bar (Social tab removed — no detector
            // path produced SOCIAL alerts, so it was perpetually empty.)
            MessageTypeTabRow(
                selected = state.tab,
                onSelect = viewModel::setTab,
                smsCount = state.alerts.size,
                emailCount = state.emailAlerts.size
            )

            // Filter tabs
            FilterTabs(
                selected = state.filter,
                onSelect = viewModel::setFilter
            )

            // Notification listener warning
            if (!state.isNotificationListenerEnabled) {
                val context = LocalContext.current
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningAmberLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                        Text(
                            "Safe Companion cannot scan your messages yet. Tap here to enable scanning.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WarningAmber)
                    }
                }
            }

            if (state.filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when (state.tab) {
                                MessageTab.EMAILS -> "📧"
                                MessageTab.TEXTS -> "📬"
                            },
                            fontSize = 48.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            when (state.tab) {
                                MessageTab.EMAILS -> "No emails scanned yet"
                                MessageTab.TEXTS -> "No messages here"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (state.tab == MessageTab.EMAILS && state.emailAccountCount == 0) {
                            Spacer(Modifier.height(24.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                                    .clickable { onNavigateToEmailSetup() },
                                colors = CardDefaults.cardColors(containerColor = WarmGoldLight.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.AddCircleOutline,
                                        contentDescription = null,
                                        tint = NavyBlue,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "Connect your email account so Safe Companion can scan for scam emails.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = onNavigateToEmailSetup,
                                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            "Add Email Account",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.filtered, key = { it.id }) { alert ->
                        AlertCard(
                            alert = alert,
                            onClick = {
                                viewModel.markAsRead(alert.id)
                                val typeLabel = when (alert.type) {
                                    "EMAIL" -> "email"
                                    else -> "text"
                                }
                                val context = "I received a $typeLabel from ${alert.sender}. " +
                                    "The message said: \"${alert.content}\". " +
                                    "Safe Companion said: ${alert.reason}. What should I do?"
                                onOpenSafeHarbor(context)
                            }
                        )
                    }

                    // Recent Tips section (only on texts tab)
                    if (state.tab == MessageTab.TEXTS && state.recentTips.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Recent Scam Tips",
                                style = MaterialTheme.typography.headlineSmall,
                                color = NavyBlue
                            )
                        }

                        items(state.recentTips, key = { "tip_${it.id}" }) { tip ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (tip.isRead) Color.White else WarmGoldLight.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                onClick = { viewModel.markTipAsRead(tip.id) }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        tip.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = NavyBlue
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        tip.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesTopBar(tab: MessageTab, onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        },
        title = {
            Text(
                when (tab) {
                    MessageTab.TEXTS -> "💬 Text Messages"
                    MessageTab.EMAILS -> "📧 Emails"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    )
}

@Composable
private fun MessageTypeTabRow(
    selected: MessageTab,
    onSelect: (MessageTab) -> Unit,
    smsCount: Int,
    emailCount: Int
) {
    val selectedIndex = when (selected) {
        MessageTab.TEXTS -> 0
        MessageTab.EMAILS -> 1
    }
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = NavyBlue.copy(alpha = 0.9f),
        contentColor = Color.White
    ) {
        Tab(
            selected = selected == MessageTab.TEXTS,
            onClick = { onSelect(MessageTab.TEXTS) },
            modifier = Modifier.height(52.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sms, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Texts" + if (smsCount > 0) " ($smsCount)" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected == MessageTab.TEXTS) Color.White else Color.White.copy(alpha = 0.6f)
                )
            }
        }
        Tab(
            selected = selected == MessageTab.EMAILS,
            onClick = { onSelect(MessageTab.EMAILS) },
            modifier = Modifier.height(52.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Emails" + if (emailCount > 0) " ($emailCount)" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected == MessageTab.EMAILS) Color.White else Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun FilterTabs(selected: MessageFilter, onSelect: (MessageFilter) -> Unit) {
    val tabs = listOf(
        MessageFilter.ALL to "All",
        MessageFilter.SCAMS to "Scams",
        MessageFilter.WARNINGS to "Warnings",
        MessageFilter.SAFE to "Safe"
    )

    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selected },
        containerColor = Color.White,
        contentColor = NavyBlue
    ) {
        tabs.forEach { (filter, label) ->
            Tab(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                modifier = Modifier.height(52.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected == filter) NavyBlue else TextSecondary
                )
            }
        }
    }
}
