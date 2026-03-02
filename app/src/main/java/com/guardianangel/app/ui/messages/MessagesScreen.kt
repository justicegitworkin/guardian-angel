package com.guardianangel.app.ui.messages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardianangel.app.ui.home.AlertCard
import com.guardianangel.app.util.formatTime
import com.guardianangel.app.ui.theme.*

@Composable
fun MessagesScreen(
    onNavigateBack: () -> Unit,
    onOpenGuardian: (String) -> Unit,
    viewModel: MessagesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            MessagesTopBar(onBack = onNavigateBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter tabs
            FilterTabs(
                selected = state.filter,
                onSelect = viewModel::setFilter
            )

            if (state.filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📬", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No messages here",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
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
                                val context = "I received an SMS from ${alert.sender}. " +
                                    "Guardian flagged it as ${alert.riskLevel}: ${alert.reason}. " +
                                    "Recommended action: ${alert.action}. What should I do?"
                                onOpenGuardian(context)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesTopBar(onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        },
        title = {
            Text("💬 Text Messages", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
    )
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
